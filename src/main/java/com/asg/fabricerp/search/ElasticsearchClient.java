package com.asg.fabricerp.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Elasticsearch over its REST API: create the index, bulk-load records, search. Plain JSON through
 * Spring's {@link RestClient} rather than the Java client library, so no extra dependency, and the
 * handful of endpoints used (index create/delete, {@code _bulk}, {@code _search}, {@code _count})
 * have been stable since 7.x - this works against 7.10+ and 8.x alike.
 *
 * <p>Every record of every organization lives in one index; each search is filtered to the caller's
 * organization, unit, screens and row scope ({@link #query}), so the index is never asked an
 * unfiltered question.
 */
@Component
public class ElasticsearchClient {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    /**
     * Codes are keywords, lower-cased on the way in and in every term, prefix and wildcard query, so
     * "bpo-2026-12" finds BPO-2026-000012's neighbours whatever the case typed. Names and narrations
     * are analyzed text, searched as-you-type.
     */
    static final String INDEX_DEFINITION = """
        {
          "settings": {
            "number_of_shards": 1,
            "auto_expand_replicas": "0-1",
            "analysis": { "normalizer": { "code": { "type": "custom", "filter": ["lowercase", "asciifolding"] } } }
          },
          "mappings": {
            "dynamic": "strict",
            "properties": {
              "kind":            { "type": "keyword" },
              "sourceId":        { "type": "long" },
              "organizationId":  { "type": "long" },
              "businessUnitId":  { "type": "long" },
              "docType":         { "type": "keyword" },
              "code":            { "type": "keyword", "normalizer": "code", "fields": { "text": { "type": "text" } } },
              "title":           { "type": "text" },
              "subtitle":        { "type": "text" },
              "body":            { "type": "text" },
              "status":          { "type": "keyword" },
              "date":            { "type": "date", "format": "yyyy-MM-dd" },
              "amount":          { "type": "scaled_float", "scaling_factor": 100 },
              "currency":        { "type": "keyword" },
              "marketingTeamId": { "type": "long" },
              "warehouseId":     { "type": "long" },
              "toWarehouseId":   { "type": "long" },
              "createdBy":       { "type": "keyword" },
              "updatedAt":       { "type": "date", "format": "yyyy-MM-dd'T'HH:mm:ss.SSS" }
            }
          }
        }
        """;

    private final SearchProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    public ElasticsearchClient(SearchProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        var es = props.elasticsearch();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(es.timeout()).build());
        factory.setReadTimeout(es.timeout());
        RestClient.Builder builder = RestClient.builder()
            .requestFactory(factory)
            .baseUrl(props.elasticsearchEnabled() ? es.url().replaceAll("/+$", "") : "http://localhost:9200")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (es.username() != null && !es.username().isBlank()) {
            String basic = Base64.getEncoder().encodeToString(
                (es.username() + ":" + (es.password() == null ? "" : es.password())).getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        }
        this.http = builder.build();
    }

    private String index() {
        return props.elasticsearch().index();
    }

    // ------------------------------------------------------------------------------------ index

    /** @return true if the index had to be created - the caller then loads every record */
    public boolean ensureIndex() {
        HttpStatusCode head = http.head().uri("/{index}", index()).exchange((req, res) -> res.getStatusCode());
        if (head.is2xxSuccessful()) return false;
        if (head.value() != 404) throw new IllegalStateException("Elasticsearch answered " + head.value() + " for index " + index());
        try {
            http.put().uri("/{index}", index()).contentType(MediaType.APPLICATION_JSON).body(INDEX_DEFINITION)
                .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest raced) {
            // resource_already_exists_exception: another node created it between the two calls.
            if (!raced.getResponseBodyAsString().contains("resource_already_exists")) throw raced;
            return false;
        }
        return true;
    }

    public void deleteIndex() {
        try {
            http.delete().uri("/{index}", index()).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound gone) {
            // Nothing to delete.
        }
    }

    /** How many records the index holds, or -1 if it cannot say. */
    public long count() {
        JsonNode body = tree(http.get().uri("/{index}/_count", index()).retrieve().body(String.class));
        return body == null ? -1 : body.path("count").asLong(-1);
    }

    /** The newest record change the index holds; null when it is empty. Where an incremental sync resumes. */
    public LocalDateTime newestChange() {
        JsonNode body = tree(http.post().uri("/{index}/_search", index()).contentType(MediaType.APPLICATION_JSON)
            .body("{\"size\":0,\"aggs\":{\"newest\":{\"max\":{\"field\":\"updatedAt\"}}}}")
            .retrieve().body(String.class));
        JsonNode value = body == null ? null : body.path("aggregations").path("newest").path("value");
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.asLong()), ZoneOffset.UTC);
    }

    /**
     * Indexes the live records and removes the deleted ones, in one {@code _bulk} call.
     *
     * @throws IllegalStateException if any item failed - the sync then retries the batch
     */
    public void bulk(List<SearchRecord> records) {
        if (records.isEmpty()) return;
        StringBuilder ndjson = new StringBuilder(records.size() * 400);
        for (SearchRecord r : records) {
            String id = json(Map.of("_index", index(), "_id", r.key()));
            if (r.deleted()) {
                ndjson.append("{\"delete\":").append(id).append("}\n");
            } else {
                ndjson.append("{\"index\":").append(id).append("}\n").append(json(source(r))).append('\n');
            }
        }
        JsonNode body = tree(http.post().uri("/_bulk").contentType(MediaType.valueOf("application/x-ndjson"))
            .body(ndjson.toString()).retrieve().body(String.class));
        if (body != null && body.path("errors").asBoolean(false)) {
            for (JsonNode item : body.path("items")) {
                JsonNode result = item.has("index") ? item.get("index") : item.get("delete");
                // Deleting a record that was never indexed is not a failure.
                if (result != null && result.has("error") && result.path("status").asInt() != 404) {
                    throw new IllegalStateException("Elasticsearch refused " + result.path("_id").asText() + ": "
                        + result.path("error").path("reason").asText());
                }
            }
        }
    }

    static Map<String, Object> source(SearchRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", r.kind().name());
        m.put("sourceId", r.sourceId());
        m.put("organizationId", r.organizationId());
        m.put("businessUnitId", r.businessUnitId());
        m.put("docType", r.docType());
        m.put("code", r.code());
        m.put("title", r.title());
        m.put("subtitle", r.subtitle());
        m.put("body", r.body());
        m.put("status", r.status());
        m.put("date", r.date() == null ? null : r.date().toString());
        m.put("amount", r.amount());
        m.put("currency", r.currency());
        m.put("marketingTeamId", r.marketingTeamId());
        m.put("warehouseId", r.warehouseId());
        m.put("toWarehouseId", r.toWarehouseId());
        m.put("createdBy", r.createdBy());
        m.put("updatedAt", r.updatedAt() == null ? null : TS.format(r.updatedAt()));
        return m;
    }

    // ----------------------------------------------------------------------------------- search

    public SearchResult search(String q, SearchKind only, SearchScope scope, int offset, int limit) {
        List<String> terms = SearchSql.terms(q);
        if (terms.isEmpty() || scope.seesNothing()) return SearchResult.empty("elasticsearch");
        Map<String, Object> query = query(String.join(" ", terms), only, scope);
        if (query == null) return SearchResult.empty("elasticsearch");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("from", offset);
        request.put("size", limit);
        request.put("track_total_hits", true);
        request.put("query", query);
        request.put("sort", List.of(Map.of("_score", "desc"), Map.of("code", "desc"), Map.of("sourceId", "desc")));
        JsonNode body = tree(http.post().uri("/{index}/_search", index()).contentType(MediaType.APPLICATION_JSON)
            .body(json(request)).retrieve().body(String.class));
        if (body == null) return SearchResult.empty("elasticsearch");
        List<SearchResult.Hit> hits = new ArrayList<>();
        for (JsonNode h : body.path("hits").path("hits")) {
            JsonNode s = h.path("_source");
            hits.add(SearchResult.Hit.of(SearchKind.valueOf(s.path("kind").asText()), s.path("sourceId").asLong(),
                text(s, "docType"), text(s, "code"), text(s, "title"), text(s, "subtitle"), text(s, "status"),
                s.hasNonNull("date") ? LocalDate.parse(s.get("date").asText()) : null,
                s.hasNonNull("amount") ? new BigDecimal(s.get("amount").asText()) : null, text(s, "currency")));
        }
        return new SearchResult("elasticsearch", body.path("hits").path("total").path("value").asLong(hits.size()), hits);
    }

    /**
     * Relevance: an exact code first, then a code starting with what was typed, then a code containing
     * it, then words in the buyer, item or party name, references and narration. The filter is the
     * {@link SearchScope}: organization always, and one clause per kind the user may see.
     *
     * @return null when the user may see none of the kinds asked for
     */
    static Map<String, Object> query(String q, SearchKind only, SearchScope scope) {
        List<Object> kinds = new ArrayList<>();
        if (scope.sees(SearchKind.DOCUMENT) && (only == null || only == SearchKind.DOCUMENT)) {
            List<Object> must = new ArrayList<>(List.of(
                term("kind", "DOCUMENT"),
                Map.of("terms", Map.of("docType", scope.documentTypes())),
                term("businessUnitId", scope.businessUnitId())));
            if (scope.teamIds() != null) must.add(Map.of("terms", Map.of("marketingTeamId", scope.teamIds())));
            if (scope.warehouseIds() != null) {
                must.add(Map.of("bool", Map.of("minimum_should_match", 1, "should", List.of(
                    Map.of("bool", Map.of("must_not", List.of(Map.of("exists", Map.of("field", "warehouseId"))))),
                    Map.of("terms", Map.of("warehouseId", scope.warehouseIds())),
                    Map.of("terms", Map.of("toWarehouseId", scope.warehouseIds()))))));
            }
            must.add(Map.of("bool", Map.of("minimum_should_match", 1, "should", List.of(
                Map.of("bool", Map.of("must_not", List.of(term("docType", SearchScope.OWNER_ONLY_TYPE)))),
                term("createdBy", scope.username() == null ? "" : scope.username())))));
            kinds.add(Map.of("bool", Map.of("filter", must)));
        }
        for (SearchKind k : List.of(SearchKind.VOUCHER, SearchKind.PARTY, SearchKind.ITEM)) {
            if (scope.sees(k) && (only == null || only == k)) kinds.add(term("kind", k.name()));
        }
        if (kinds.isEmpty()) return null;

        String lower = q.toLowerCase(Locale.ROOT);
        String wildcard = "*" + lower.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?") + "*";
        List<Object> should = List.of(
            Map.of("term", Map.of("code", Map.of("value", lower, "boost", 20))),
            Map.of("prefix", Map.of("code", Map.of("value", lower, "boost", 8))),
            Map.of("wildcard", Map.of("code", Map.of("value", wildcard, "boost", 3))),
            Map.of("multi_match", Map.of("query", q, "type", "bool_prefix", "operator", "and",
                "fields", List.of("code.text^4", "title^3", "subtitle^2", "body"))));
        return Map.of("bool", Map.of(
            "filter", List.of(
                term("organizationId", scope.organizationId()),
                Map.of("bool", Map.of("should", kinds, "minimum_should_match", 1))),
            "should", should,
            "minimum_should_match", 1));
    }

    private static Map<String, Object> term(String field, Object value) {
        return Map.of("term", Map.of(field, value));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /** Parsed here rather than by a message converter: a proxy in front of the cluster may not label it JSON. */
    private JsonNode tree(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Elasticsearch answered with something other than JSON", e);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write a search request", e);
        }
    }
}

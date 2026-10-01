package com.asg.fabricerp.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The REST conversation with Elasticsearch, against a stand-in server. */
class ElasticsearchClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MockWebServer server;
    private ElasticsearchClient client;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        SearchProperties props = new SearchProperties(new SearchProperties.Elasticsearch(
            server.url("/").toString(), "test-index", "elastic", "secret", Duration.ofSeconds(2)), Duration.ofSeconds(15));
        client = new ElasticsearchClient(props, mapper);
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private static SearchScope scope(List<Long> teams, List<Long> stores) {
        return new SearchScope(1L, 10L, "rahim", Set.of(SearchKind.DOCUMENT, SearchKind.PARTY),
            List.of("BOOKING", "BULK_PRODUCTION_ORDER"), teams, stores);
    }

    @Test
    void aMissingIndex_isCreatedWithItsMapping() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"acknowledged\":true}"));

        assertThat(client.ensureIndex()).isTrue();

        assertThat(server.takeRequest().getMethod()).isEqualTo("HEAD");
        RecordedRequest put = server.takeRequest();
        assertThat(put.getMethod()).isEqualTo("PUT");
        assertThat(put.getPath()).isEqualTo("/test-index");
        assertThat(put.getHeader("Authorization")).startsWith("Basic ");
        assertThat(mapper.readTree(put.getBody().readUtf8()).at("/mappings/properties/code/normalizer").asText()).isEqualTo("code");
    }

    @Test
    void anExistingIndex_isLeftAlone() {
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThat(client.ensureIndex()).isFalse();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void bulk_indexesLiveRecordsAndDeletesDeletedOnes() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"errors\":false,\"items\":[]}"));
        LocalDateTime at = LocalDateTime.of(2026, 10, 1, 9, 30);
        client.bulk(List.of(
            new SearchRecord(SearchKind.DOCUMENT, 12L, 1L, 10L, "BULK_PRODUCTION_ORDER", "BPO-2026-000012", "Acme Ltd",
                "PO-77", "APPROVED", LocalDate.of(2026, 9, 30), new BigDecimal("1500.50"), "USD", 7L, null, null,
                "rahim", "PO-77", false, at),
            new SearchRecord(SearchKind.PARTY, 4L, 1L, null, "ORGANISATION", "PT-000004", "Gone Ltd", null, "INACTIVE",
                null, null, null, null, null, null, "rahim", "", true, at)));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/_bulk");
        String[] lines = req.getBody().readUtf8().split("\n");
        assertThat(lines).hasSize(3);
        assertThat(mapper.readTree(lines[0]).at("/index/_id").asText()).isEqualTo("DOCUMENT:12");
        JsonNode source = mapper.readTree(lines[1]);
        assertThat(source.get("code").asText()).isEqualTo("BPO-2026-000012");
        assertThat(source.get("date").asText()).isEqualTo("2026-09-30");
        assertThat(source.get("updatedAt").asText()).isEqualTo("2026-10-01T09:30:00.000");
        assertThat(mapper.readTree(lines[2]).at("/delete/_id").asText()).isEqualTo("PARTY:4");
    }

    @Test
    void aRefusedRecord_failsTheBatch_butAMissingDeleteDoesNot() {
        server.enqueue(new MockResponse().setBody("""
            {"errors":true,"items":[{"delete":{"_id":"PARTY:4","status":404,"error":{"reason":"not found"}}}]}"""));
        client.bulk(List.of(deleted()));

        server.enqueue(new MockResponse().setBody("""
            {"errors":true,"items":[{"index":{"_id":"PARTY:4","status":400,"error":{"reason":"mapper_parsing_exception"}}}]}"""));
        assertThatThrownBy(() -> client.bulk(List.of(deleted())))
            .hasMessageContaining("PARTY:4").hasMessageContaining("mapper_parsing_exception");
    }

    private static SearchRecord deleted() {
        return new SearchRecord(SearchKind.PARTY, 4L, 1L, null, "ORGANISATION", "PT-000004", "Gone", null, null,
            null, null, null, null, null, null, null, null, true, LocalDateTime.now());
    }

    @Test
    void search_isFilteredToTheUsersScope_andReadsTheHits() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"hits":{"total":{"value":1},"hits":[{"_source":{
              "kind":"DOCUMENT","sourceId":12,"docType":"BULK_PRODUCTION_ORDER","code":"BPO-2026-000012",
              "title":"Acme Ltd","subtitle":"PO-77","status":"APPROVED","date":"2026-09-30","amount":1500.5,"currency":"USD"}}]}}"""));

        SearchResult result = client.search("BPO-2026", null, scope(List.of(7L), List.of(3L)), 0, 20);

        assertThat(result.engine()).isEqualTo("elasticsearch");
        assertThat(result.total()).isEqualTo(1);
        SearchResult.Hit hit = result.hits().get(0);
        assertThat(hit.code()).isEqualTo("BPO-2026-000012");
        assertThat(hit.url()).isEqualTo("/bpo?open=12");
        assertThat(hit.typeLabel()).isEqualTo("Production Order");

        JsonNode sent = mapper.readTree(server.takeRequest().getBody().readUtf8());
        String filter = sent.at("/query/bool/filter").toString();
        assertThat(filter).contains("{\"term\":{\"organizationId\":1}}")
            .contains("{\"term\":{\"businessUnitId\":10}}")
            .contains("{\"terms\":{\"marketingTeamId\":[7]}}")
            .contains("{\"terms\":{\"warehouseId\":[3]}}")
            .contains("{\"term\":{\"createdBy\":\"rahim\"}}")
            .contains("{\"term\":{\"kind\":\"PARTY\"}}")
            .doesNotContain("VOUCHER").doesNotContain("ITEM");
        assertThat(sent.at("/query/bool/should/0/term/code/value").asText()).isEqualTo("bpo-2026");
        assertThat(sent.at("/sort/1/code").asText()).isEqualTo("desc");
    }

    @Test
    void askingForAKindTheUserCannotSee_asksNothing() {
        SearchResult result = client.search("x", SearchKind.VOUCHER, scope(null, null), 0, 20);
        assertThat(result.total()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void newestChange_readsTheMaxAggregation() {
        server.enqueue(new MockResponse().setBody("{\"aggregations\":{\"newest\":{\"value\":1790847000000}}}"));
        assertThat(client.newestChange()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 30));

        server.enqueue(new MockResponse().setBody("{\"aggregations\":{\"newest\":{\"value\":null}}}"));
        assertThat(client.newestChange()).isNull();
    }
}

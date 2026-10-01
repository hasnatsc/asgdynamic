package com.asg.fabricerp.search;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * The one definition of what is searchable: {@link #PROJECTION}, a {@code UNION ALL} of documents,
 * vouchers, parties and items in the {@link SearchRecord} shape. The indexer reads it to feed
 * Elasticsearch ({@link #changedSince}); the PostgreSQL search reads it directly ({@link #search}).
 * One query for both, so the index cannot come to hold something the fallback would not find.
 */
@Component
public class SearchSql {

    /** Records past this many words of a query are ignored: a search is a lookup, not a paragraph. */
    static final int MAX_TERMS = 5;

    static final String PROJECTION = """
        SELECT 'DOCUMENT' AS kind, d.id AS source_id, d.organization_id, d.business_unit_id,
               d.document_type AS doc_type, d.document_no AS code, COALESCE(p.name, '') AS title,
               NULLIF(concat_ws(' · ', NULLIF(d.reference_no, ''), NULLIF(c.lc_no, '')), '') AS subtitle,
               d.status, d.document_date AS doc_date, d.subtotal_amount AS amount, d.currency_code AS currency,
               d.marketing_team_id, d.warehouse_id, d.to_warehouse_id, d.created_by,
               concat_ws(' ', d.reference_no, d.invoice_no, c.lc_no, c.master_lc_no, p.code, d.remarks) AS body,
               d.deleted,
               COALESCE(GREATEST(d.updated_at, d.created_at, p.updated_at, p.created_at), TIMESTAMP '1970-01-01') AS ts
        FROM gbl_business_documents d
        LEFT JOIN pty_parties p ON p.id = d.party_id
        LEFT JOIN com_document_details c ON c.document_id = d.id
        UNION ALL
        SELECT 'VOUCHER', e.id, e.organization_id, e.business_unit_id,
               e.voucher_type, e.entry_no, COALESCE(e.narration, ''), e.event_type,
               CASE WHEN e.reversed_by_id IS NULL THEN 'POSTED' ELSE 'REVERSED' END, e.posting_date,
               (SELECT sum(l.functional_amount) FROM acc_gl_entry_lines l WHERE l.entry_id = e.id AND l.side = 'DEBIT'),
               'BDT', NULL, NULL, NULL, e.created_by,
               e.narration, FALSE, COALESCE(e.updated_at, e.created_at, TIMESTAMP '1970-01-01')
        FROM acc_gl_entries e
        UNION ALL
        SELECT 'PARTY', p.id, p.organization_id, NULL,
               p.party_type, p.code, p.name, p.legal_name,
               CASE WHEN p.active THEN 'ACTIVE' ELSE 'INACTIVE' END, NULL, NULL, NULL, NULL, NULL, NULL, p.created_by,
               concat_ws(' ', p.legal_name, p.tin, p.bin, p.vat_reg_no,
                         (SELECT string_agg(r.role_code, ' ') FROM pty_party_roles r WHERE r.party_id = p.id AND r.is_current)),
               p.deleted, COALESCE(p.updated_at, p.created_at, TIMESTAMP '1970-01-01')
        FROM pty_parties p
        UNION ALL
        SELECT 'ITEM', i.id, i.organization_id, NULL,
               i.item_type, i.item_code, i.item_name, i.item_name_bn,
               CASE WHEN NOT i.active THEN 'INACTIVE' WHEN i.approved THEN 'APPROVED' ELSE 'DRAFT' END,
               NULL, NULL, NULL, NULL, NULL, NULL, i.created_by,
               concat_ws(' ', i.item_name_bn, i.barcode, i.sku, i.description),
               i.deleted, COALESCE(i.updated_at, i.created_at, TIMESTAMP '1970-01-01')
        FROM inv_items i
        """;

    /**
     * Who may see what, as {@link SearchScope} describes it - {@code BusinessDocumentRepository.LIST_FILTER}
     * and the booking list's creator rule, restated over the projection.
     */
    private static final String VISIBLE = """
            WHERE s.organization_id = :org AND NOT s.deleted
              AND (   (:docs AND s.kind = 'DOCUMENT' AND s.business_unit_id = :unit AND s.doc_type IN (:types)
                       AND (:allTeams OR s.marketing_team_id IN (:teams))
                       AND (:allStores OR s.warehouse_id IS NULL OR s.warehouse_id IN (:stores) OR s.to_warehouse_id IN (:stores))
                       AND (s.doc_type <> :ownerOnly OR s.created_by = :user))
                   OR (:vouchers AND s.kind = 'VOUCHER')
                   OR (:parties AND s.kind = 'PARTY')
                   OR (:items AND s.kind = 'ITEM'))
            """;

    private static final RowMapper<SearchRecord> RECORD = (rs, n) -> new SearchRecord(
        SearchKind.valueOf(rs.getString("kind")), rs.getLong("source_id"), rs.getLong("organization_id"),
        rs.getObject("business_unit_id", Long.class), rs.getString("doc_type"), rs.getString("code"),
        rs.getString("title"), rs.getString("subtitle"), rs.getString("status"),
        rs.getObject("doc_date", java.time.LocalDate.class), rs.getBigDecimal("amount"), rs.getString("currency"),
        rs.getObject("marketing_team_id", Long.class), rs.getObject("warehouse_id", Long.class),
        rs.getObject("to_warehouse_id", Long.class), rs.getString("created_by"), rs.getString("body"),
        rs.getBoolean("deleted"), toLocal(rs.getTimestamp("ts")));

    private final NamedParameterJdbcTemplate jdbc;

    public SearchSql(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records changed at or after {@code since}, oldest first, resuming after {@code afterKey} among
     * records stamped exactly {@code since} - a keyset, so a batch boundary inside one timestamp
     * neither repeats nor skips. Deleted records are included: the indexer removes them.
     */
    public List<SearchRecord> changedSince(LocalDateTime since, String afterKey, int limit) {
        return jdbc.query("""
            SELECT * FROM (SELECT s.*, s.kind || ':' || s.source_id AS k FROM (""" + PROJECTION + """
            ) s) s
            WHERE s.ts > :since OR (s.ts = :since AND s.k > :afterKey)
            ORDER BY s.ts, s.k
            LIMIT :limit
            """, new MapSqlParameterSource("since", Timestamp.valueOf(since))
                .addValue("afterKey", afterKey == null ? "" : afterKey)
                .addValue("limit", limit), RECORD);
    }

    /**
     * The PostgreSQL search: every word of {@code q} must appear in the code, title, subtitle or
     * body. Ranked exact code, then code prefix, then code containing it, then anything else -
     * newest code first within each.
     */
    public SearchResult search(String q, SearchKind only, SearchScope scope, int offset, int limit) {
        List<String> terms = terms(q);
        if (terms.isEmpty() || scope.seesNothing()) return SearchResult.empty("database");
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("org", scope.organizationId())
            .addValue("unit", scope.businessUnitId())
            .addValue("user", scope.username())
            .addValue("ownerOnly", SearchScope.OWNER_ONLY_TYPE)
            .addValue("types", scope.documentTypes().isEmpty() ? List.of("-") : scope.documentTypes())
            .addValue("docs", scope.sees(SearchKind.DOCUMENT) && (only == null || only == SearchKind.DOCUMENT))
            .addValue("vouchers", scope.sees(SearchKind.VOUCHER) && (only == null || only == SearchKind.VOUCHER))
            .addValue("parties", scope.sees(SearchKind.PARTY) && (only == null || only == SearchKind.PARTY))
            .addValue("items", scope.sees(SearchKind.ITEM) && (only == null || only == SearchKind.ITEM))
            .addValue("allTeams", scope.teamIds() == null)
            .addValue("teams", scope.teamIds() == null || scope.teamIds().isEmpty() ? List.of(-1L) : scope.teamIds())
            .addValue("allStores", scope.warehouseIds() == null)
            .addValue("stores", scope.warehouseIds() == null || scope.warehouseIds().isEmpty() ? List.of(-1L) : scope.warehouseIds())
            .addValue("exact", terms.size() == 1 ? terms.get(0) : String.join(" ", terms))
            .addValue("prefix", like(terms.get(0), false))
            .addValue("offset", offset)
            .addValue("limit", limit);
        StringBuilder match = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            p.addValue("t" + i, like(terms.get(i), true));
            match.append("""
                  AND (lower(s.code) LIKE :t%1$d ESCAPE '\\' OR lower(s.title) LIKE :t%1$d ESCAPE '\\'
                       OR lower(COALESCE(s.subtitle, '')) LIKE :t%1$d ESCAPE '\\' OR lower(COALESCE(s.body, '')) LIKE :t%1$d ESCAPE '\\')
                """.formatted(i));
        }
        String filtered = "FROM (" + PROJECTION + ") s\n" + VISIBLE + match;
        long[] total = {0};
        List<SearchResult.Hit> hits = jdbc.query("SELECT s.*, count(*) OVER () AS total " + filtered + """
            ORDER BY CASE WHEN lower(s.code) = :exact THEN 0
                          WHEN lower(s.code) LIKE :prefix ESCAPE '\\' THEN 1
                          WHEN lower(s.code) LIKE :t0 ESCAPE '\\' THEN 2 ELSE 3 END,
                     s.code DESC, s.source_id DESC
            OFFSET :offset LIMIT :limit
            """, p, (rs, n) -> {
                total[0] = rs.getLong("total");
                return SearchResult.Hit.of(SearchKind.valueOf(rs.getString("kind")), rs.getLong("source_id"),
                    rs.getString("doc_type"), rs.getString("code"), rs.getString("title"), rs.getString("subtitle"),
                    rs.getString("status"), rs.getObject("doc_date", java.time.LocalDate.class),
                    rs.getBigDecimal("amount"), rs.getString("currency"));
            });
        if (hits.isEmpty() && offset > 0) {
            // A page past the last result still reports how many there are.
            Long n = jdbc.queryForObject("SELECT count(*) " + filtered, p, Long.class);
            total[0] = n == null ? 0 : n;
        }
        return new SearchResult("database", total[0], hits);
    }

    /** The words of a query, lower-cased; at most {@link #MAX_TERMS}. */
    static List<String> terms(String q) {
        if (q == null) return List.of();
        return java.util.Arrays.stream(q.strip().toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(t -> !t.isEmpty())
            .limit(MAX_TERMS)
            .toList();
    }

    /** {@code %term%} (or {@code term%}), with LIKE's own wildcards in the term taken literally. */
    static String like(String term, boolean contains) {
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return (contains ? "%" : "") + escaped + "%";
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : ts.toLocalDateTime();
    }
}

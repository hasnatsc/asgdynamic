package com.asg.fabricerp.commercial;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commercial setup: the document names an LC may require and a CI is presented with, and the heads
 * PI, LC and CI costs are recorded under. A name or head that a document has used is retired
 * (inactive), not deleted, so the document still reads.
 */
@Service
public class CommercialSetupService {

    private static final Set<String> KINDS = Set.of("PI", "LC", "CI");

    public record NameRequest(String name, String docKind, Integer sortOrder, Boolean active) { }

    public record CostHeadRequest(String name, String docKind, String accountCode, Boolean active) { }

    private final DocumentNameRepository names;
    private final CostHeadRepository heads;
    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public CommercialSetupService(DocumentNameRepository names, CostHeadRepository heads, NamedParameterJdbcTemplate jdbc,
                                  OrgContext context) {
        this.names = names;
        this.heads = heads;
        this.jdbc = jdbc;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public List<DocumentName> documentNames() {
        return names.list(context.requireOrganizationId());
    }

    @Transactional(readOnly = true)
    public List<CostHead> costHeads() {
        return heads.list(context.requireOrganizationId());
    }

    @Transactional
    public DocumentName saveName(Long id, NameRequest r) {
        Long org = context.requireOrganizationId();
        DocumentName n = id == null ? new DocumentName() : names.findScoped(id, org)
            .orElseThrow(() -> new IllegalArgumentException("Document name not found: " + id));
        if (id == null) {
            n.setOrganizationId(org);
            n.setCode(nextCode("com_document_names", "DN"));
        }
        n.setName(required(r.name(), "Give the document's name"));
        n.setDocKind(kind(r.docKind()));
        n.setSortOrder(r.sortOrder());
        if (r.active() != null) n.setActive(r.active());
        return names.save(n);
    }

    @Transactional
    public CostHead saveCostHead(Long id, CostHeadRequest r) {
        Long org = context.requireOrganizationId();
        CostHead h = id == null ? new CostHead() : heads.findScoped(id, org)
            .orElseThrow(() -> new IllegalArgumentException("Cost head not found: " + id));
        if (id == null) {
            h.setOrganizationId(org);
            h.setCode(nextCode("com_cost_heads", "CH"));
        }
        h.setName(required(r.name(), "Give the cost head's name"));
        h.setDocKind(kind(r.docKind()));
        String account = r.accountCode() == null || r.accountCode().isBlank() ? null : r.accountCode().strip();
        if (account != null && !exists("SELECT count(*) FROM acc_accounts WHERE organization_id = :org AND code = :code AND deleted = FALSE", account)) {
            throw new IllegalArgumentException("Account " + account + " is not in the chart of accounts");
        }
        h.setAccountCode(account);
        if (r.active() != null) h.setActive(r.active());
        return heads.save(h);
    }

    /** Deleted when nothing has used it; otherwise retired, so the documents that did still read. */
    @Transactional
    public String deleteName(Long id) {
        DocumentName n = names.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Document name not found: " + id));
        if (exists("SELECT count(*) FROM com_document_events WHERE document_name_id = :id", id)) {
            n.setActive(false);
            names.save(n);
            return "retired";
        }
        n.markDeleted();
        names.save(n);
        return "deleted";
    }

    @Transactional
    public String deleteCostHead(Long id) {
        CostHead h = heads.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Cost head not found: " + id));
        if (exists("SELECT count(*) FROM com_document_events WHERE cost_head_id = :id", id)) {
            h.setActive(false);
            heads.save(h);
            return "retired";
        }
        h.markDeleted();
        heads.save(h);
        return "deleted";
    }

    private String nextCode(String table, String prefix) {
        Integer max = jdbc.queryForObject("""
            SELECT COALESCE(MAX(CAST(substring(code FROM '[0-9]+$') AS INTEGER)), 0) FROM %s
            WHERE organization_id = :org AND code ~ '^%s[0-9]+$'
            """.formatted(table, prefix), new MapSqlParameterSource("org", context.requireOrganizationId()), Integer.class);
        return prefix + String.format("%03d", (max == null ? 0 : max) + 1);
    }

    private boolean exists(String sql, Object key) {
        MapSqlParameterSource p = new MapSqlParameterSource("org", context.requireOrganizationId()).addValue("code", key).addValue("id", key);
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n != null && n > 0;
    }

    private static String kind(String k) {
        String v = k == null ? "" : k.strip().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(v)) throw new IllegalArgumentException("Type is PI, LC or CI");
        return v;
    }

    private static String required(String v, String message) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(message);
        return v.strip();
    }
}

package com.asg.fabricerp.commercial;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.production.ProductionBoardService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * The commercial register - the legacy PI, LC and CI reports and the commercial dashboard in one
 * place: every export PI with the LCs that cover it, every LC with what is invoiced and realized
 * against it and how long it has left, every CI with where its realization stands and when it
 * matures; and the import PIs and LCs with their checkpoints, bills of entry and costs.
 *
 * <p>Team-scoped like every other screen: a marketing user sees their team's export paper.
 */
@Service
@Transactional(readOnly = true)
public class CommercialRegisterQueries {

    private static final String LIVE = "d.deleted = FALSE AND d.status NOT IN ('CANCELLED')";
    private static final String COMMITTED = "('APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public CommercialRegisterQueries(NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    /** The tiles over the register. */
    public Map<String, Object> summary() {
        MapSqlParameterSource p = scope().addValue("today", LocalDate.now()).addValue("soon", LocalDate.now().plusDays(15));
        return ProductionBoardService.camel(jdbc.queryForMap("""
            SELECT
              (SELECT COALESCE(SUM(d.subtotal_amount), 0) FROM gbl_business_documents d
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'EXPORT_PROFORMA_INVOICE'
                   AND d.deleted = FALSE AND d.status IN ('APPROVED', 'PARTIAL') AND (:allTeams OR d.marketing_team_id IN (:teams))) AS pi_open_value,
              (SELECT COALESCE(SUM(d.subtotal_amount), 0) FROM gbl_business_documents d
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'EXPORT_LETTER_OF_CREDIT'
                   AND d.deleted = FALSE AND d.status IN ('APPROVED', 'PARTIAL') AND (:allTeams OR d.marketing_team_id IN (:teams))) AS lc_open_value,
              (SELECT count(*) FROM gbl_business_documents d JOIN com_document_details c ON c.document_id = d.id
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'EXPORT_LETTER_OF_CREDIT'
                   AND d.deleted = FALSE AND d.status IN ('APPROVED', 'PARTIAL') AND c.validity_date BETWEEN :today AND :soon
                   AND (:allTeams OR d.marketing_team_id IN (:teams))) AS lc_expiring,
              (SELECT COALESCE(SUM(d.subtotal_amount), 0) FROM gbl_business_documents d
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'EXPORT_COMMERCIAL_INVOICE'
                   AND d.deleted = FALSE AND d.status IN ('APPROVED', 'PARTIAL') AND (:allTeams OR d.marketing_team_id IN (:teams))) AS ci_unrealized_value,
              (SELECT count(*) FROM gbl_business_documents d
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'EXPORT_COMMERCIAL_INVOICE'
                   AND d.deleted = FALSE AND d.status IN ('APPROVED', 'PARTIAL') AND (:allTeams OR d.marketing_team_id IN (:teams))) AS ci_unrealized,
              (SELECT COALESCE(SUM(d.subtotal_amount * d.exchange_rate), 0) FROM gbl_business_documents d
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'IMPORT_LETTER_OF_CREDIT'
                   AND d.deleted = FALSE AND d.status IN ('APPROVED', 'PARTIAL', 'PROCESSING')) AS import_lc_value_bdt,
              (SELECT count(*) FROM gbl_business_documents d
                 WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'IMPORT_PROFORMA_INVOICE'
                   AND d.deleted = FALSE AND d.status IN ('DRAFT', 'SUBMITTED', 'APPROVED')) AS import_pi_open
            """, p));
    }

    /** Export PIs, with the schedules they offer and the LCs that cover them. */
    public List<Map<String, Object>> exportPis(String q, LocalDate from, LocalDate to) {
        return list("""
            SELECT d.id, d.document_no, d.document_date, d.status, d.revision_no, p.name AS applicant, b.name AS brand,
                   d.currency_code AS currency, d.total_quantity, d.subtotal_amount, c.validity_date,
                   (SELECT string_agg(DISTINCT s.document_no, ', ') FROM gbl_business_document_line_groups g
                      JOIN gbl_business_document_color_lines l ON l.line_group_id = g.id
                      JOIN gbl_business_document_color_lines sl ON sl.id = l.source_color_line_id
                      JOIN gbl_business_document_line_groups sg ON sg.id = sl.line_group_id
                      JOIN gbl_business_documents s ON s.id = sg.document_id
                     WHERE g.document_id = d.id) AS schedules,
                   (SELECT string_agg(DISTINCT COALESCE(lc_c.lc_no, lc.document_no), ', ') FROM gbl_business_documents lc
                      JOIN com_document_details lc_c ON lc_c.document_id = lc.id
                      JOIN gbl_business_document_line_groups g ON g.document_id = lc.id
                      JOIN gbl_business_document_color_lines l ON l.line_group_id = g.id
                      JOIN gbl_business_document_color_lines pl ON pl.id = l.source_color_line_id
                      JOIN gbl_business_document_line_groups pg ON pg.id = pl.line_group_id
                     WHERE pg.document_id = d.id AND lc.deleted = FALSE AND lc.status IN %s) AS lcs
            FROM gbl_business_documents d
            LEFT JOIN com_document_details c ON c.document_id = d.id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            LEFT JOIN pty_parties b ON b.id = d.brand_id
            WHERE d.document_type = 'EXPORT_PROFORMA_INVOICE' AND %s
            """.formatted(COMMITTED, filters()), q, from, to);
    }

    /** Export LCs: value, invoiced, realized, UD/UP, back-to-back LCs opened on them, days to expiry. */
    public List<Map<String, Object>> exportLcs(String q, LocalDate from, LocalDate to) {
        return list("""
            SELECT d.id, d.document_no, d.document_date, d.status, d.revision_no, p.name AS buyer, c.lc_no, c.master_lc_no,
                   c.issue_date, c.shipment_date, c.validity_date, c.acknowledged_on, d.currency_code AS currency,
                   d.subtotal_amount AS lc_value,
                   (SELECT COALESCE(SUM(ci.subtotal_amount), 0) FROM gbl_business_documents ci
                     WHERE ci.parent_document_id = d.id AND ci.document_type = 'EXPORT_COMMERCIAL_INVOICE'
                       AND ci.deleted = FALSE AND ci.status IN %s) AS invoiced,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e JOIN gbl_business_documents ci ON ci.id = e.document_id
                     WHERE ci.parent_document_id = d.id AND ci.document_type = 'EXPORT_COMMERCIAL_INVOICE' AND ci.deleted = FALSE
                       AND ci.status <> 'CANCELLED' AND e.kind = 'REALIZATION' AND e.code = 'FINAL_PAYMENT') AS realized,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'UD') AS ud_value,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'UP') AS up_value,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'BTB_LC')
                   + (SELECT COALESCE(SUM(i.subtotal_amount), 0) FROM com_document_details ic JOIN gbl_business_documents i ON i.id = ic.document_id
                       WHERE ic.backed_by_document_id = d.id AND i.deleted = FALSE AND i.status <> 'CANCELLED') AS btb_value,
                   c.incentive_amount,
                   CASE WHEN c.validity_date IS NULL THEN NULL ELSE c.validity_date - CURRENT_DATE END AS days_to_expiry
            FROM gbl_business_documents d
            LEFT JOIN com_document_details c ON c.document_id = d.id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            WHERE d.document_type = 'EXPORT_LETTER_OF_CREDIT' AND %s
            """.formatted(COMMITTED, filters()), q, from, to);
    }

    /** Export CIs and their realization. */
    public List<Map<String, Object>> exportCis(String q, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = list("""
            SELECT d.id, d.document_no, d.document_date, d.status, p.name AS buyer, lc.document_no AS lc_document_no,
                   lcc.lc_no, c.ci_kind, c.ibc_no, c.realization_step, d.currency_code AS currency, d.total_quantity,
                   d.subtotal_amount, lcc.tenure, lcc.payment_terms,
                   (SELECT e.event_date FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'REALIZATION'
                      AND e.code = 'PARTY_ACCEPTANCE') AS accepted_on,
                   (SELECT e.event_date FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'REALIZATION'
                      AND e.code = 'BANK_SUBMISSION') AS submitted_on,
                   (SELECT e.amount FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'REALIZATION'
                      AND e.code = 'FINAL_PAYMENT') AS realized,
                   (SELECT e.event_date FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'REALIZATION'
                      AND e.code = 'FINAL_PAYMENT') AS realized_on
            FROM gbl_business_documents d
            LEFT JOIN com_document_details c ON c.document_id = d.id
            LEFT JOIN gbl_business_documents lc ON lc.id = d.parent_document_id
            LEFT JOIN com_document_details lcc ON lcc.document_id = lc.id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            WHERE d.document_type = 'EXPORT_COMMERCIAL_INVOICE' AND %s
            """.formatted(filters()), q, from, to);
        // Maturity: the payment's start plus the tenure, as the viewer works it out.
        for (Map<String, Object> r : rows) {
            Object tenure = r.get("tenure");
            LocalDate start = "DATE_OF_DELIVERY".equals(r.get("paymentTerms")) || "DATE_OF_SHIPMENT".equals(r.get("paymentTerms"))
                ? (LocalDate) r.get("documentDate")
                : "DATE_OF_ACCEPTANCE".equals(r.get("paymentTerms")) || r.get("paymentTerms") == null ? (LocalDate) r.get("acceptedOn")
                : (LocalDate) r.get("submittedOn");
            LocalDate due = tenure == null || start == null ? null : start.plusDays(CommercialTerms.Tenure.valueOf((String) tenure).days());
            r.put("maturityDue", due);
            r.put("overdue", due != null && r.get("realizedOn") == null && due.isBefore(LocalDate.now()));
        }
        return rows;
    }

    /** Import PIs with their checkpoints and the LC and orders raised on them. */
    public List<Map<String, Object>> importPis(String q, LocalDate from, LocalDate to) {
        return list("""
            SELECT d.id, d.document_no, d.document_date, d.status, d.reference_no AS supplier_pi_no, p.name AS supplier,
                   a.name AS local_agent, d.currency_code AS currency, d.subtotal_amount,
                   (SELECT string_agg(e.code, ',') FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'MILESTONE') AS milestones,
                   (SELECT string_agg(DISTINCT x.document_no, ', ') FROM gbl_business_documents x
                      JOIN gbl_business_document_line_groups g ON g.document_id = x.id
                      JOIN gbl_business_document_color_lines l ON l.line_group_id = g.id
                      JOIN gbl_business_document_color_lines pl ON pl.id = l.source_color_line_id
                      JOIN gbl_business_document_line_groups pg ON pg.id = pl.line_group_id
                     WHERE pg.document_id = d.id AND x.deleted = FALSE AND x.status <> 'CANCELLED'
                       AND x.document_type IN ('IMPORT_LETTER_OF_CREDIT', 'PURCHASE_ORDER')) AS raised
            FROM gbl_business_documents d
            LEFT JOIN com_document_details c ON c.document_id = d.id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            LEFT JOIN pty_parties a ON a.id = c.local_agent_party_id
            WHERE d.document_type = 'IMPORT_PROFORMA_INVOICE' AND %s
            """.formatted(filters()), q, from, to);
    }

    /** Import LCs with their bill of entry, costs, and the export LC they are backed by. */
    public List<Map<String, Object>> importLcs(String q, LocalDate from, LocalDate to) {
        return list("""
            SELECT d.id, d.document_no, d.document_date, d.status, d.revision_no, p.name AS supplier, c.lc_no, c.import_doc_type,
                   c.lc_type, c.tenure, c.issue_date, c.shipment_date, c.validity_date, c.port, c.bill_of_entry_no,
                   c.bill_of_entry_date, d.currency_code AS currency, d.subtotal_amount, back.document_no AS backed_by,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e WHERE e.document_id = d.id AND e.kind = 'COST') AS costs
            FROM gbl_business_documents d
            LEFT JOIN com_document_details c ON c.document_id = d.id
            LEFT JOIN gbl_business_documents back ON back.id = c.backed_by_document_id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            WHERE d.document_type = 'IMPORT_LETTER_OF_CREDIT' AND %s
            """.formatted(filters()), q, from, to);
    }

    private String filters() {
        return """
            d.organization_id = :org AND d.business_unit_id = :unit AND %s
              AND (:allTeams OR d.marketing_team_id IN (:teams) OR d.document_type IN ('IMPORT_PROFORMA_INVOICE', 'IMPORT_LETTER_OF_CREDIT'))
              AND (CAST(:from AS DATE) IS NULL OR d.document_date >= :from)
              AND (CAST(:to AS DATE) IS NULL OR d.document_date <= :to)
              AND (:q = '%%' OR lower(d.document_no) LIKE :q OR lower(COALESCE(d.reference_no, '')) LIKE :q
                   OR d.party_id IN (SELECT id FROM pty_parties WHERE lower(name) LIKE :q)
                   OR d.id IN (SELECT document_id FROM com_document_details WHERE lower(COALESCE(lc_no, '')) LIKE :q))
            ORDER BY d.document_date DESC, d.id DESC
            LIMIT 500
            """.formatted(LIVE.replace("NOT IN ('CANCELLED')", "<> 'CANCELLED'"));
    }

    private List<Map<String, Object>> list(String sql, String q, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = scope()
            .addValue("q", q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%")
            .addValue("from", from).addValue("to", to);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, p)) {
            Map<String, Object> row = ProductionBoardService.camel(r);
            row.replaceAll((k, v) -> v instanceof java.sql.Date date ? date.toLocalDate() : v);
            rows.add(row);
        }
        return rows;
    }

    private MapSqlParameterSource scope() {
        RowScope scope = context.requireRowScope();
        List<Long> teams = scope.idsForQuery(ScopeDimension.MARKETING_TEAM);
        return new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("unit", context.requireBusinessUnitId())
            .addValue("allTeams", !scope.restricts(ScopeDimension.MARKETING_TEAM))
            .addValue("teams", teams.isEmpty() ? List.of(-1L) : teams);
    }
}

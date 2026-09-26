package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * The two boards the chain is run from, read from the draw streams and the stock ledger rather
 * than typed in - so a figure on a board can never drift from the documents behind it.
 *
 * <p><b>Production board</b>: every open production order line, where its fabric is at each stage,
 * a late flag, and the next thing it needs (weave, dye, schedule).
 * <p><b>Ready to deliver</b>: approved delivery schedule lines by due date, with what the store can
 * send now - the smaller of what is still to go on delivery orders and what is free in stock.
 */
@Service
@Transactional(readOnly = true)
public class ProductionBoardService {

    /** A line is late when it is due within this many days and not ready. */
    public static final int LATE_WITHIN_DAYS = 7;

    public record BoardFilter(Long teamId, Long buyerId, String fabricType, LocalDate dueBy, String q, boolean includeCompleted) { }

    public record Page(List<Map<String, Object>> rows, long total, Map<String, Object> totals) { }

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public ProductionBoardService(NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    // ------------------------------------------------------------------------- production board

    private static final String BOARD = """
        WITH l AS (
            SELECT d.id AS bpo_id, d.document_no AS bpo_no, d.status, d.required_date, d.document_date,
                   p.name AS buyer, t.name AS team, g.id AS group_id, g.group_no, g.construction, g.fabric_type,
                   g.route_code, g.greige_key, g.deliver_stage, COALESCE(g.needs_processing, false) AS needs_processing,
                   COALESCE(g.greige_allowance_pct, 0) AS allowance, COALESCE(g.delivery_tolerance_pct, 0) AS tolerance,
                   COALESCE(NULLIF(u.symbol, ''), u.name) AS uom, cl.id AS line_id, cl.color_name, cl.color_code, cl.quantity,
                   cl.short_close_reason IS NOT NULL AS short_closed, cl.short_closed_quantity,
                   cl.quantity / NULLIF((SELECT SUM(x.quantity) FROM gbl_business_document_color_lines x
                                         WHERE x.line_group_id = g.id), 0) AS share
            FROM gbl_business_documents d
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            LEFT JOIN org_marketing_teams t ON t.id = d.marketing_team_id
            LEFT JOIN inv_uoms u ON u.id = g.uom_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit
              AND d.document_type = 'BULK_PRODUCTION_ORDER' AND d.deleted = false
              AND d.status IN (:statuses)
              AND (:allTeams OR d.marketing_team_id IN (:teams))
              AND (CAST(:teamId AS BIGINT) IS NULL OR d.marketing_team_id = :teamId)
              AND (CAST(:buyerId AS BIGINT) IS NULL OR d.party_id = :buyerId)
              AND (CAST(:fabricType AS VARCHAR) IS NULL OR lower(g.fabric_type) = lower(CAST(:fabricType AS VARCHAR)))
              AND (CAST(:dueBy AS DATE) IS NULL OR d.required_date <= :dueBy)
              AND (:q = '%' OR lower(d.document_no) LIKE :q OR lower(COALESCE(p.name, '')) LIKE :q
                   OR lower(COALESCE(cl.color_name, '')) LIKE :q OR lower(COALESCE(g.construction, '')) LIKE :q)
        ), f AS (
            SELECT l.*,
                   l.quantity * (1 + l.allowance / 100) AS greige_required,
                   CASE WHEN l.greige_key = 'CONSTRUCTION'
                        THEN COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'GROUP'
                                       AND dr.source_id = l.group_id AND dr.stream = 'WEAVING_WORK_ORDER'), 0) * COALESCE(l.share, 0)
                        ELSE COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                                       AND dr.source_id = l.line_id AND dr.stream = 'WEAVING_WORK_ORDER'), 0) END AS weaving,
                   COALESCE((SELECT SUM(dr.drawn_quantity) FROM gbl_line_draws dr
                             JOIN gbl_business_document_color_lines w ON w.id = dr.source_id
                             WHERE dr.source_kind = 'COLOUR' AND dr.stream = 'GREIGE_RECEIVE'
                               AND (w.source_color_line_id = l.line_id OR w.source_line_group_id = l.group_id)), 0)
                     * CASE WHEN l.greige_key = 'CONSTRUCTION' THEN COALESCE(l.share, 0) ELSE 1 END AS greige_received,
                   COALESCE((SELECT SUM(b.quantity - b.reserved_quantity) FROM inv_fabric_balances b
                             JOIN inv_fabric_lots lot ON lot.id = b.lot_id
                             WHERE lot.stage = 'GREIGE' AND lot.line_group_id = l.group_id
                               AND (lot.color_line_id = l.line_id OR lot.color_line_id IS NULL)), 0)
                     * CASE WHEN l.greige_key = 'CONSTRUCTION' THEN COALESCE(l.share, 0) ELSE 1 END AS greige_on_hand,
                   COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                             AND dr.source_id = l.line_id AND dr.stream = 'PROCESSING_WORK_ORDER'), 0) AS dyeing,
                   COALESCE((SELECT SUM(dr.drawn_quantity) FROM gbl_line_draws dr
                             JOIN gbl_business_document_color_lines w ON w.id = dr.source_id
                             WHERE dr.source_kind = 'COLOUR' AND dr.stream = 'GREIGE_ISSUE' AND w.source_color_line_id = l.line_id), 0) AS greige_issued,
                   COALESCE((SELECT SUM(b.quantity - b.reserved_quantity) FROM inv_fabric_balances b
                             JOIN inv_fabric_lots lot ON lot.id = b.lot_id
                             WHERE lot.stage = 'FINISHED' AND lot.color_line_id = l.line_id AND lot.grade = 'A'), 0) AS finished_ready,
                   COALESCE((SELECT SUM(fl.quantity) FROM gbl_business_document_color_lines fl
                             JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
                             JOIN gbl_business_documents fd ON fd.id = fg.document_id
                             JOIN gbl_business_document_color_lines w ON w.id = fl.source_color_line_id
                             WHERE fd.document_type = 'FINISHED_FABRICS_RECEIVE' AND fd.status = 'APPROVED' AND fd.deleted = false
                               AND w.source_color_line_id = l.line_id AND COALESCE(fl.grade, 'A') = 'A'), 0) AS finished_a,
                   COALESCE((SELECT SUM(fl.quantity) FROM gbl_business_document_color_lines fl
                             JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
                             JOIN gbl_business_documents fd ON fd.id = fg.document_id
                             JOIN gbl_business_document_color_lines w ON w.id = fl.source_color_line_id
                             WHERE fd.document_type = 'FINISHED_FABRICS_RECEIVE' AND fd.status = 'APPROVED' AND fd.deleted = false
                               AND w.source_color_line_id = l.line_id AND fl.grade = 'B'), 0) AS finished_b,
                   COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                             AND dr.source_id = l.line_id AND dr.stream = 'REQUEST_FOR_PI'), 0) AS scheduled,
                   COALESCE((SELECT SUM(dr.drawn_quantity) FROM gbl_line_draws dr
                             JOIN gbl_business_document_color_lines r ON r.id = dr.source_id
                             WHERE dr.source_kind = 'COLOUR' AND dr.stream = 'DELIVERY_ORDER' AND r.source_color_line_id = l.line_id), 0) AS on_delivery_orders,
                   COALESCE((SELECT SUM(fl.quantity) FROM gbl_business_document_color_lines fl
                             JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
                             JOIN gbl_business_documents fd ON fd.id = fg.document_id
                             JOIN gbl_business_document_color_lines dl ON dl.id = fl.source_color_line_id
                             JOIN gbl_business_document_color_lines rl ON rl.id = dl.source_color_line_id
                             WHERE fd.document_type = 'FABRICS_DELIVERY' AND fd.status = 'APPROVED' AND fd.deleted = false
                               AND rl.source_color_line_id = l.line_id), 0) AS delivered
            FROM l
        ), r AS (
            SELECT f.*,
                   CASE WHEN f.deliver_stage = 'FINISHED' THEN f.finished_ready ELSE f.greige_on_hand END AS ready,
                   GREATEST(f.quantity - f.delivered - CASE WHEN f.short_closed THEN f.short_closed_quantity ELSE 0 END, 0) AS balance
            FROM f
        )
        SELECT r.*,
               (NOT r.short_closed AND r.required_date IS NOT NULL AND r.required_date <= CAST(:lateBy AS DATE)
                AND r.ready < GREATEST(r.scheduled - r.delivered, r.balance)) AS late,
               (NOT r.short_closed AND r.route_code IS NOT NULL AND r.weaving < r.greige_required) AS needs_weaving,
               (NOT r.short_closed AND r.needs_processing AND r.dyeing < r.quantity) AS needs_dyeing,
               (NOT r.short_closed AND r.scheduled < r.quantity) AS needs_schedule
        FROM r
        """;

    public Page productionBoard(BoardFilter filter, int page, int size) {
        MapSqlParameterSource p = scopeParams()
            .addValue("statuses", filter.includeCompleted()
                ? List.of("APPROVED", "PROCESSING", "PARTIAL", "COMPLETED") : List.of("APPROVED", "PROCESSING", "PARTIAL"))
            .addValue("teamId", filter.teamId()).addValue("buyerId", filter.buyerId())
            .addValue("fabricType", blank(filter.fabricType())).addValue("dueBy", filter.dueBy())
            .addValue("q", like(filter.q())).addValue("lateBy", LocalDate.now().plusDays(LATE_WITHIN_DAYS))
            .addValue("limit", size).addValue("offset", Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM (" + BOARD + ") b "
            + "ORDER BY b.late DESC, b.required_date NULLS LAST, b.bpo_no, b.group_no, b.line_id LIMIT :limit OFFSET :offset", p);
        Map<String, Object> totals = jdbc.queryForMap("""
            SELECT count(*) AS lines, COUNT(*) FILTER (WHERE late) AS late,
                   COUNT(*) FILTER (WHERE needs_weaving) AS to_weave, COUNT(*) FILTER (WHERE needs_dyeing) AS to_dye,
                   COUNT(*) FILTER (WHERE needs_schedule) AS to_schedule,
                   COUNT(DISTINCT bpo_id) AS orders, COALESCE(SUM(quantity), 0) AS quantity,
                   COALESCE(SUM(delivered), 0) AS delivered, COALESCE(SUM(ready), 0) AS ready
            FROM (""" + BOARD + ") b", p);
        return new Page(rows.stream().map(ProductionBoardService::camel).toList(), ((Number) totals.get("lines")).longValue(),
            camel(totals));
    }

    // ------------------------------------------------------------------------ ready to deliver

    private static final String READY = """
        WITH s AS (
            SELECT d.id AS schedule_id, d.document_no AS schedule_no, d.status, p.name AS buyer, t.name AS team,
                   rl.id AS line_id, rl.color_name, rl.quantity AS scheduled,
                   COALESCE(rl.delivery_date, d.required_date) AS due_date,
                   bl.id AS bpo_line_id, bg.id AS group_id, bd.id AS bpo_id, bd.document_no AS bpo_no,
                   bg.fabric_type, bg.construction, bg.deliver_stage, bg.greige_key,
                   COALESCE(bg.delivery_tolerance_pct, 0) AS tolerance, COALESCE(NULLIF(u.symbol, ''), u.name) AS uom, w.name AS garments,
                   COALESCE(d.garments_address, '') AS address
            FROM gbl_business_documents d
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines rl ON rl.line_group_id = g.id
            JOIN gbl_business_document_color_lines bl ON bl.id = rl.source_color_line_id
            JOIN gbl_business_document_line_groups bg ON bg.id = bl.line_group_id
            JOIN gbl_business_documents bd ON bd.id = bg.document_id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            LEFT JOIN pty_parties w ON w.id = d.garments_id
            LEFT JOIN org_marketing_teams t ON t.id = d.marketing_team_id
            LEFT JOIN inv_uoms u ON u.id = g.uom_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit
              AND d.document_type = 'REQUEST_FOR_PI' AND d.deleted = false
              AND d.status IN ('APPROVED', 'PARTIAL')
              AND rl.short_close_reason IS NULL
              AND (:allTeams OR d.marketing_team_id IN (:teams))
              AND (CAST(:buyerId AS BIGINT) IS NULL OR d.party_id = :buyerId)
              AND (:q = '%' OR lower(d.document_no) LIKE :q OR lower(COALESCE(p.name, '')) LIKE :q
                   OR lower(bd.document_no) LIKE :q OR lower(COALESCE(rl.color_name, '')) LIKE :q)
        ), f AS (
            SELECT s.*,
                   COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                             AND dr.source_id = s.line_id AND dr.stream = 'DELIVERY_ORDER'), 0) AS on_delivery_orders,
                   COALESCE((SELECT SUM(fl.quantity) FROM gbl_business_document_color_lines fl
                             JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
                             JOIN gbl_business_documents fd ON fd.id = fg.document_id
                             JOIN gbl_business_document_color_lines dl ON dl.id = fl.source_color_line_id
                             WHERE fd.document_type = 'FABRICS_DELIVERY' AND fd.status = 'APPROVED' AND fd.deleted = false
                               AND dl.source_color_line_id = s.line_id), 0) AS delivered,
                   COALESCE((SELECT SUM(b.quantity - b.reserved_quantity) FROM inv_fabric_balances b
                             JOIN inv_fabric_lots lot ON lot.id = b.lot_id
                             WHERE CASE WHEN s.deliver_stage = 'FINISHED'
                                        THEN lot.stage = 'FINISHED' AND lot.color_line_id = s.bpo_line_id AND lot.grade = 'A'
                                        ELSE lot.stage = 'GREIGE' AND lot.line_group_id = s.group_id
                                             AND (lot.color_line_id = s.bpo_line_id OR (s.greige_key = 'CONSTRUCTION' AND lot.color_line_id IS NULL))
                                   END), 0) AS ready
            FROM s
        )
        SELECT f.*,
               GREATEST(f.scheduled - f.on_delivery_orders, 0) AS to_order,
               GREATEST(LEAST(f.scheduled - f.on_delivery_orders, f.ready), 0) AS deliverable,
               (f.due_date - CAST(:today AS DATE)) AS days_due
        FROM f
        """;

    public Page readyToDeliver(Long buyerId, String q, boolean deliverableOnly, int page, int size) {
        MapSqlParameterSource p = scopeParams().addValue("buyerId", buyerId).addValue("q", like(q))
            .addValue("today", LocalDate.now()).addValue("limit", size).addValue("offset", Math.max(0, page) * size);
        String where = deliverableOnly ? " WHERE b.deliverable > 0" : "";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM (" + READY + ") b" + where
            + " ORDER BY b.due_date NULLS LAST, b.schedule_no, b.line_id LIMIT :limit OFFSET :offset", p);
        Map<String, Object> totals = jdbc.queryForMap("""
            SELECT count(*) AS lines, COUNT(*) FILTER (WHERE deliverable > 0) AS deliverable_lines,
                   COUNT(*) FILTER (WHERE days_due < 0 AND to_order > 0) AS overdue,
                   COALESCE(SUM(deliverable), 0) AS deliverable, COALESCE(SUM(to_order), 0) AS to_order
            FROM (""" + READY + ") b" + where, p);
        return new Page(rows.stream().map(ProductionBoardService::camel).toList(), ((Number) totals.get("lines")).longValue(),
            camel(totals));
    }

    // --------------------------------------------------------------------------------- helpers

    private MapSqlParameterSource scopeParams() {
        RowScope scope = context.requireRowScope();
        return new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("unit", context.requireBusinessUnitId())
            .addValue("allTeams", !scope.restricts(ScopeDimension.MARKETING_TEAM))
            .addValue("teams", teamIds(scope));
    }

    static Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            StringBuilder b = new StringBuilder();
            boolean up = false;
            for (char c : k.toCharArray()) {
                if (c == '_') { up = true; continue; }
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
            out.put(b.toString(), v);
        });
        return out;
    }

    private static String like(String q) {
        return q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    /** A SQL IN list may not be empty; no team has id -1. */
    static List<Long> teamIds(RowScope scope) {
        List<Long> ids = scope.idsForQuery(ScopeDimension.MARKETING_TEAM);
        return ids.isEmpty() ? List.of(-1L) : ids;
    }
}

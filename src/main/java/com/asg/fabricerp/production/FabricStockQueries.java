package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Fabric stock screen: balances by store, order, lot, shade and grade, and each lot's ledger.
 * Team-scoped like every other screen - a marketing user sees the stock of their team's orders.
 */
@Service
@Transactional(readOnly = true)
public class FabricStockQueries {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public FabricStockQueries(NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    private static final String BALANCES = """
        SELECT b.warehouse_id, w.name AS store, l.id AS lot_id, l.stage, l.dye_lot, l.shade, l.grade,
               d.id AS bpo_id, d.document_no AS bpo_no, p.name AS buyer, t.name AS team,
               g.group_no, g.construction, g.fabric_type, cl.color_name, COALESCE(NULLIF(u.symbol, ''), u.name) AS uom,
               b.quantity, b.reserved_quantity AS reserved, b.quantity - b.reserved_quantity AS free, b.rolls,
               b.updated_at AS last_moved
        FROM inv_fabric_balances b
        JOIN inv_fabric_lots l ON l.id = b.lot_id
        JOIN org_warehouses w ON w.id = b.warehouse_id
        JOIN gbl_business_documents d ON d.id = l.bpo_document_id
        JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
        LEFT JOIN gbl_business_document_color_lines cl ON cl.id = l.color_line_id
        LEFT JOIN pty_parties p ON p.id = d.party_id
        LEFT JOIN org_marketing_teams t ON t.id = d.marketing_team_id
        LEFT JOIN inv_uoms u ON u.id = g.uom_id
        WHERE b.organization_id = :org
          AND (:allTeams OR d.marketing_team_id IN (:teams))
          AND (CAST(:wh AS BIGINT) IS NULL OR b.warehouse_id = :wh)
          AND (CAST(:stage AS VARCHAR) IS NULL OR l.stage = :stage)
          AND (:withZero OR b.quantity > 0)
          AND (:q = '%' OR lower(d.document_no) LIKE :q OR lower(COALESCE(p.name, '')) LIKE :q
               OR lower(COALESCE(cl.color_name, '')) LIKE :q OR lower(COALESCE(g.construction, '')) LIKE :q
               OR lower(COALESCE(l.dye_lot, '')) LIKE :q OR lower(COALESCE(l.shade, '')) LIKE :q)
        """;

    public Map<String, Object> balances(Long warehouseId, String stage, String q, boolean withZero, int page, int size) {
        MapSqlParameterSource p = scope().addValue("wh", warehouseId)
            .addValue("stage", stage == null || stage.isBlank() ? null : stage.toUpperCase(Locale.ROOT))
            .addValue("q", like(q)).addValue("withZero", withZero)
            .addValue("limit", size).addValue("offset", Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM (" + BALANCES + ") s "
            + "ORDER BY s.store, s.bpo_no, s.group_no, s.color_name NULLS FIRST, s.dye_lot NULLS FIRST, s.grade NULLS FIRST "
            + "LIMIT :limit OFFSET :offset", p);
        Map<String, Object> totals = jdbc.queryForMap("""
            SELECT count(*) AS lots, COALESCE(SUM(quantity), 0) AS quantity, COALESCE(SUM(reserved), 0) AS reserved,
                   COALESCE(SUM(free), 0) AS free, COALESCE(SUM(rolls), 0) AS rolls,
                   COALESCE(SUM(quantity) FILTER (WHERE stage = 'GREIGE'), 0) AS greige,
                   COALESCE(SUM(quantity) FILTER (WHERE stage = 'FINISHED'), 0) AS finished
            FROM (""" + BALANCES + ") s", p);
        return Map.of("rows", rows.stream().map(ProductionBoardService::camel).toList(),
            "total", totals.get("lots"), "totals", ProductionBoardService.camel(totals));
    }

    /** One lot's ledger, newest first. */
    public List<Map<String, Object>> moves(Long lotId, Long warehouseId) {
        return jdbc.queryForList("""
            SELECT m.id, m.posted_at, m.move_type, m.quantity, m.rolls, m.remarks, m.posted_by, w.name AS store,
                   d.id AS document_id, d.document_no, d.document_type, m.reverses_move_id
            FROM inv_fabric_moves m
            JOIN inv_fabric_lots l ON l.id = m.lot_id
            JOIN gbl_business_documents b ON b.id = l.bpo_document_id
            JOIN org_warehouses w ON w.id = m.warehouse_id
            JOIN gbl_business_documents d ON d.id = m.document_id
            WHERE m.lot_id = :lot AND m.organization_id = :org
              AND (CAST(:wh AS BIGINT) IS NULL OR m.warehouse_id = :wh)
              AND (:allTeams OR b.marketing_team_id IN (:teams))
            ORDER BY m.posted_at DESC, m.id DESC
            """, scope().addValue("lot", lotId).addValue("wh", warehouseId))
            .stream().map(ProductionBoardService::camel).toList();
    }

    private MapSqlParameterSource scope() {
        RowScope scope = context.requireRowScope();
        return new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("allTeams", !scope.restricts(ScopeDimension.MARKETING_TEAM))
            .addValue("teams", teamIds(scope));
    }

    private static String like(String q) {
        return q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
    }

    /** A SQL IN list may not be empty; no team has id -1. */
    static List<Long> teamIds(RowScope scope) {
        List<Long> ids = scope.idsForQuery(ScopeDimension.MARKETING_TEAM);
        return ids.isEmpty() ? List.of(-1L) : ids;
    }
}

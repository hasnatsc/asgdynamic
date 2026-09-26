package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * The chain's read-side SQL: which documents may be raised against, which lots a line may pick,
 * and how much has been delivered against each order line.
 */
@Component
public class ChainQueries {

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;
    private final OrgContext context;

    public ChainQueries(NamedParameterJdbcTemplate jdbc, EntityManager em, OrgContext context) {
        this.jdbc = jdbc;
        this.em = em;
        this.context = context;
    }

    /** Parent documents a step may be raised against: approved and open, in scope, newest first. */
    public List<BusinessDocument> openParents(ChainStep step, String q, int page, int size) {
        RowScope scope = context.requireRowScope();
        String like = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        var query = em.createQuery("""
                select d from BusinessDocument d left join fetch d.party p
                where d.organizationId = :org and d.businessUnit.id = :unit and d.documentType = :type
                  and d.deleted = false and d.status in :statuses
                  and (lower(d.documentNo) like :q or lower(coalesce(d.referenceNo, '')) like :q
                       or lower(coalesce(p.name, '')) like :q)
                  and (:allTeams = true or d.marketingTeam.id in :teams)
                  and (:batchOpen = false or d.batchClosed = false)
                order by d.documentDate desc, d.id desc
                """, BusinessDocument.class)
            .setParameter("org", context.requireOrganizationId())
            .setParameter("unit", context.requireBusinessUnitId())
            .setParameter("type", step.parentType())
            .setParameter("statuses", EnumSet.of(BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING,
                BusinessDocumentStatus.PARTIAL))
            .setParameter("q", like)
            .setParameter("allTeams", !scope.restricts(ScopeDimension.MARKETING_TEAM))
            .setParameter("teams", scope.idsForQuery(ScopeDimension.MARKETING_TEAM))
            .setParameter("batchOpen", step == ChainStep.GI || step == ChainStep.FFR)
            .setFirstResult(Math.max(0, page) * size)
            .setMaxResults(size + 1);
        return query.getResultList();
    }

    // ------------------------------------------------------------------------------------ lots

    /** Which lots a line may take: stage, the order's fabric line, its colour (or none), and grade. */
    public record LotRule(String stage, Long groupId, Long colourLineId, String grade) { }

    public boolean lotFits(Long lotId, LotRule rule) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM inv_fabric_lots l
            WHERE l.id = :lot AND l.stage = :stage AND l.line_group_id = :grp
              AND COALESCE(l.color_line_id, 0) = COALESCE(CAST(:line AS BIGINT), 0)
              AND (CAST(:grade AS VARCHAR) IS NULL OR l.grade = :grade)
            """, rule(rule).addValue("lot", lotId), Integer.class);
        return n != null && n > 0;
    }

    /** The lots a rule allows, with what each store holds of them. */
    public List<Map<String, Object>> lots(LotRule rule, Long warehouseId) {
        return jdbc.queryForList("""
            SELECT l.id AS "lotId", l.stage, l.dye_lot AS "dyeLot", l.shade, l.grade,
                   b.warehouse_id AS "warehouseId", w.name AS "warehouseName",
                   b.quantity AS "onHand", b.reserved_quantity AS reserved,
                   b.quantity - b.reserved_quantity AS free, b.rolls
            FROM inv_fabric_lots l
            JOIN inv_fabric_balances b ON b.lot_id = l.id
            JOIN org_warehouses w ON w.id = b.warehouse_id
            WHERE l.organization_id = :org AND l.stage = :stage AND l.line_group_id = :grp
              AND COALESCE(l.color_line_id, 0) = COALESCE(CAST(:line AS BIGINT), 0)
              AND (CAST(:grade AS VARCHAR) IS NULL OR l.grade = :grade)
              AND (CAST(:wh AS BIGINT) IS NULL OR b.warehouse_id = :wh)
              AND b.quantity > 0
            ORDER BY w.name, l.dye_lot NULLS FIRST, l.shade NULLS FIRST, l.id
            """, rule(rule).addValue("org", context.requireOrganizationId()).addValue("wh", warehouseId));
    }

    public List<Map<String, Object>> lotsById(Collection<Long> lotIds, Long warehouseId) {
        if (lotIds.isEmpty()) return List.of();
        return jdbc.queryForList("""
            SELECT l.id AS "lotId", l.stage, l.dye_lot AS "dyeLot", l.shade, l.grade,
                   b.warehouse_id AS "warehouseId", w.name AS "warehouseName",
                   b.quantity AS "onHand", b.reserved_quantity AS reserved,
                   b.quantity - b.reserved_quantity AS free, b.rolls
            FROM inv_fabric_lots l
            JOIN inv_fabric_balances b ON b.lot_id = l.id
            JOIN org_warehouses w ON w.id = b.warehouse_id
            WHERE l.id IN (:ids) AND (CAST(:wh AS BIGINT) IS NULL OR b.warehouse_id = :wh)
            """, new MapSqlParameterSource("ids", lotIds).addValue("wh", warehouseId));
    }

    /** Lot labels for display: id -> "Lot 12 · Shade B2 · Grade A" / "Greige". */
    public Map<Long, String> lotLabels(Collection<Long> lotIds) {
        Map<Long, String> out = new HashMap<>();
        if (lotIds.isEmpty()) return out;
        jdbc.query("""
            SELECT id, stage, bpo_document_id, line_group_id, color_line_id, dye_lot, shade, grade
            FROM inv_fabric_lots WHERE id IN (:ids)
            """, new MapSqlParameterSource("ids", lotIds), rs -> {
            out.put(rs.getLong(1), new FabricStockService.Lot(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                (Long) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getString(8)).label());
        });
        return out;
    }

    public BigDecimal reservedFor(Long doLineId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM inv_fabric_reservations WHERE color_line_id = :line",
            new MapSqlParameterSource("line", doLineId), BigDecimal.class);
    }

    // ------------------------------------------------------------------------------ delivered

    /** Fabric delivered (posted, not cancelled) against each production order colour line. */
    public Map<Long, BigDecimal> deliveredByBpoLine(Collection<Long> bpoLineIds) {
        return sumBy("""
            SELECT rl.source_color_line_id AS k, SUM(fl.quantity) AS v
            FROM gbl_business_document_color_lines fl
            JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
            JOIN gbl_business_documents fd ON fd.id = fg.document_id
            JOIN gbl_business_document_color_lines dl ON dl.id = fl.source_color_line_id
            JOIN gbl_business_document_color_lines rl ON rl.id = dl.source_color_line_id
            WHERE fd.document_type = 'FABRICS_DELIVERY' AND fd.deleted = false AND fd.status = 'APPROVED'
              AND rl.source_color_line_id IN (:ids)
            GROUP BY rl.source_color_line_id
            """, bpoLineIds);
    }

    /** Fabric delivered against each delivery schedule line. */
    public Map<Long, BigDecimal> deliveredByScheduleLine(Collection<Long> rpiLineIds) {
        return sumBy("""
            SELECT dl.source_color_line_id AS k, SUM(fl.quantity) AS v
            FROM gbl_business_document_color_lines fl
            JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
            JOIN gbl_business_documents fd ON fd.id = fg.document_id
            JOIN gbl_business_document_color_lines dl ON dl.id = fl.source_color_line_id
            WHERE fd.document_type = 'FABRICS_DELIVERY' AND fd.deleted = false AND fd.status = 'APPROVED'
              AND dl.source_color_line_id IN (:ids)
            GROUP BY dl.source_color_line_id
            """, rpiLineIds);
    }

    /** Finished fabric received per dyeing work order line, by grade: line -> {A: q, B: q}. */
    public Map<Long, Map<String, BigDecimal>> finishedByGrade(Collection<Long> pwoLineIds) {
        Map<Long, Map<String, BigDecimal>> out = new HashMap<>();
        if (pwoLineIds.isEmpty()) return out;
        jdbc.query("""
            SELECT l.source_color_line_id, COALESCE(l.grade, 'A'), SUM(l.quantity)
            FROM gbl_business_document_color_lines l
            JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
            JOIN gbl_business_documents d ON d.id = g.document_id
            WHERE d.document_type = 'FINISHED_FABRICS_RECEIVE' AND d.deleted = false AND d.status = 'APPROVED'
              AND l.source_color_line_id IN (:ids)
            GROUP BY 1, 2
            """, new MapSqlParameterSource("ids", pwoLineIds), rs -> {
            out.computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getString(2), rs.getBigDecimal(3));
        });
        return out;
    }

    /** How many committed documents of {@code type} were raised against {@code parentId}. */
    public int committedChildren(Long parentId, String type) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM gbl_business_documents
            WHERE parent_document_id = :p AND document_type = :t AND deleted = false
              AND status IN ('APPROVED', 'PROCESSING', 'PARTIAL', 'COMPLETED', 'CLOSED')
            """, new MapSqlParameterSource("p", parentId).addValue("t", type), Integer.class);
        return n == null ? 0 : n;
    }

    /** Live child documents of a parent, any line of which names it - for the viewer's "raised against it" list. */
    public List<Map<String, Object>> childrenOf(Long parentId) {
        return jdbc.queryForList("""
            SELECT DISTINCT d.id, d.document_no AS "documentNo", d.document_type AS "documentType", d.status,
                   d.document_date AS "documentDate", d.total_quantity AS "totalQuantity"
            FROM gbl_business_documents d
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines l ON l.line_group_id = g.id
            LEFT JOIN gbl_business_document_color_lines sl ON sl.id = l.source_color_line_id
            LEFT JOIN gbl_business_document_line_groups sg ON sg.id = COALESCE(l.source_line_group_id, sl.line_group_id)
            WHERE d.deleted = false AND sg.document_id = :p
            ORDER BY d.document_date, d.id
            """, new MapSqlParameterSource("p", parentId));
    }

    /**
     * For a page of documents: the constructions on each and the colours they carry, for the list.
     * Keyed by document id; each value has "fabric" and "colours" (text) and "colourCount".
     */
    public Map<Long, Map<String, Object>> fabricSummaries(Collection<Long> documentIds) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        if (documentIds == null || documentIds.isEmpty()) return out;
        jdbc.query("""
            SELECT g.document_id AS d,
                   string_agg(DISTINCT NULLIF(btrim(g.construction), ''), ', ') AS fabric,
                   string_agg(DISTINCT COALESCE(sc.color_name, l.color_name), ', ') AS colours,
                   count(DISTINCT COALESCE(sc.id, l.id)) AS n
            FROM gbl_business_document_line_groups g
            JOIN gbl_business_document_color_lines l ON l.line_group_id = g.id
            -- a line woven for a whole fabric line stands for that line's colours
            LEFT JOIN gbl_business_document_color_lines sc
                   ON l.source_line_group_id IS NOT NULL AND l.source_color_line_id IS NULL
                  AND sc.line_group_id = l.source_line_group_id
            WHERE g.document_id IN (:ids)
            GROUP BY g.document_id
            """, new MapSqlParameterSource("ids", documentIds), rs -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fabric", rs.getString("fabric"));
            m.put("colours", rs.getString("colours"));
            m.put("colourCount", rs.getLong("n"));
            out.put(rs.getLong("d"), m);
        });
        return out;
    }

    private Map<Long, BigDecimal> sumBy(String sql, Collection<Long> ids) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> { out.put(rs.getLong("k"), rs.getBigDecimal("v")); });
        return out;
    }

    private static MapSqlParameterSource rule(LotRule rule) {
        return new MapSqlParameterSource("stage", rule.stage()).addValue("grp", rule.groupId())
            .addValue("line", rule.colourLineId()).addValue("grade", rule.grade());
    }
}

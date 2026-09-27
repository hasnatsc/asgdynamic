package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.inventory.item.ItemType;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * The purchase and store documents' read-side SQL: what may be raised against, how much of each
 * line later documents have committed, what a store holds, and the fabric lots in a store.
 */
@Component
public class SupplyQueries {

    /** A document in one of these states has standing: its quantities count downstream. */
    static final List<String> COMMITTED = List.of("APPROVED", "PARTIAL", "PROCESSING", "COMPLETED", "CLOSED");

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;
    private final OrgContext context;

    public SupplyQueries(NamedParameterJdbcTemplate jdbc, EntityManager em, OrgContext context) {
        this.jdbc = jdbc;
        this.em = em;
        this.context = context;
    }

    /** Parent documents a step may be raised against: approved and open, in the user's scope, newest first. */
    public List<BusinessDocument> openParents(SupplyStep step, String q, int page, int size) {
        RowScope scope = context.requireRowScope();
        String like = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        boolean allStores = !scope.restricts(ScopeDimension.WAREHOUSE);
        return em.createQuery("""
                select d from BusinessDocument d left join fetch d.party p left join fetch d.warehouse w
                  left join fetch d.toWarehouse tw
                where d.organizationId = :org and d.businessUnit.id = :unit and d.documentType = :type
                  and d.deleted = false and d.status in :statuses
                  and (lower(d.documentNo) like :q or lower(coalesce(d.referenceNo, '')) like :q
                       or lower(coalesce(p.name, '')) like :q)
                  and (:allStores = true or w is null or w.id in :stores or tw.id in :stores)
                order by d.documentDate desc, d.id desc
                """, BusinessDocument.class)
            .setParameter("org", context.requireOrganizationId())
            .setParameter("unit", context.requireBusinessUnitId())
            .setParameter("type", step.parentType())
            .setParameter("statuses", EnumSet.of(BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING,
                BusinessDocumentStatus.PARTIAL))
            .setParameter("q", like)
            .setParameter("allStores", allStores)
            .setParameter("stores", nonEmpty(scope.idsForQuery(ScopeDimension.WAREHOUSE)))
            .setFirstResult(Math.max(0, page) * size)
            .setMaxResults(size + 1)
            .getResultList();
    }

    // ---------------------------------------------------------------------------- downstream

    /**
     * What documents of {@code childType} with standing (approved or posted, not cancelled) have
     * taken of each parent line: parent line id -> quantity, net of what they short-closed.
     */
    public Map<Long, BigDecimal> committed(DocumentType childType, Collection<Long> parentLineIds) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (parentLineIds.isEmpty()) return out;
        jdbc.query("""
            SELECT cl.source_color_line_id, SUM(cl.quantity - cl.short_closed_quantity)
            FROM gbl_business_document_color_lines cl
            JOIN gbl_business_document_line_groups g ON g.id = cl.line_group_id
            JOIN gbl_business_documents d ON d.id = g.document_id
            WHERE d.document_type = :type AND d.deleted = FALSE AND d.status IN (:committed)
              AND cl.source_color_line_id IN (:ids)
            GROUP BY cl.source_color_line_id
            """, new MapSqlParameterSource("type", childType.name()).addValue("committed", COMMITTED)
                .addValue("ids", parentLineIds),
            rs -> { out.put(rs.getLong(1), rs.getBigDecimal(2)); });
        return out;
    }

    /** Live documents raised against {@code parentId}, oldest first. */
    public List<Map<String, Object>> childrenOf(Long parentId) {
        return jdbc.queryForList("""
            SELECT DISTINCT d.id, d.document_no AS "documentNo", d.document_type AS "documentType", d.status,
                   to_char(d.document_date, 'YYYY-MM-DD') AS "documentDate"
            FROM gbl_business_documents d
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
            JOIN gbl_business_document_color_lines pl ON pl.id = cl.source_color_line_id
            JOIN gbl_business_document_line_groups pg ON pg.id = pl.line_group_id
            WHERE pg.document_id = :parent AND d.deleted = FALSE
            ORDER BY 5, 1
            """, new MapSqlParameterSource("parent", parentId));
    }

    // ----------------------------------------------------------------------------------- stock

    /** What a store holds of each item: item id -> [quantity, value]. */
    public Map<Long, BigDecimal[]> storeStock(Long warehouseId, Collection<Long> itemIds) {
        Map<Long, BigDecimal[]> out = new HashMap<>();
        if (warehouseId == null || itemIds.isEmpty()) return out;
        jdbc.query("""
            SELECT item_id, quantity, value FROM inv_item_balances WHERE warehouse_id = :wh AND item_id IN (:ids)
            """, new MapSqlParameterSource("wh", warehouseId).addValue("ids", itemIds),
            rs -> { out.put(rs.getLong(1), new BigDecimal[] { rs.getBigDecimal(2), rs.getBigDecimal(3) }); });
        return out;
    }

    /** Posted value per line of a document (for its viewer): line id -> [quantity, value, unit cost]. */
    public Map<Long, BigDecimal[]> postedByLine(Long documentId) {
        Map<Long, BigDecimal[]> out = new HashMap<>();
        jdbc.query("""
            SELECT m.line_id, SUM(m.quantity), SUM(m.value)
            FROM inv_item_moves m WHERE m.document_id = :doc AND m.line_id IS NOT NULL
            GROUP BY m.line_id
            """, new MapSqlParameterSource("doc", documentId), rs -> {
            BigDecimal qty = rs.getBigDecimal(2), value = rs.getBigDecimal(3);
            out.put(rs.getLong(1), new BigDecimal[] { qty, value, ItemStockService.unitCostOf(value, qty) });
        });
        return out;
    }

    /**
     * Items for a line picker, with what the store holds of each: a {@code LookupPage}-shaped map.
     * {@code stockOnly}: only items the store holds (issues, transfers, adjustments out).
     */
    public Map<String, Object> itemOptions(String q, ItemType type, Long warehouseId, boolean stockOnly, boolean stockItemsOnly,
                                           Long id, int page, int size) {
        String like = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        MapSqlParameterSource p = new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("q", like).addValue("type", type == null ? null : type.name()).addValue("wh", warehouseId)
            .addValue("stockOnly", stockOnly && warehouseId != null).addValue("stockItems", stockItemsOnly)
            .addValue("id", id).addValue("limit", size + 1).addValue("offset", Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT i.id, i.item_code AS code, i.item_name AS name, i.item_type AS "itemType",
                   u.id AS "uomId", COALESCE(NULLIF(u.symbol, ''), u.code) AS uom,
                   i.brand_id AS "brandId", i.model_id AS "modelId", i.cost_price AS "costPrice",
                   i.reorder_level AS "reorderLevel",
                   b.quantity AS stock, b.value AS "stockValue"
            FROM inv_items i
            JOIN inv_uoms u ON u.id = i.base_uom_id
            LEFT JOIN inv_item_balances b ON b.item_id = i.id AND b.warehouse_id = :wh
            WHERE i.organization_id = :org AND i.deleted = FALSE
              AND (CAST(:id AS BIGINT) IS NULL OR i.id = :id)
              AND (CAST(:id AS BIGINT) IS NOT NULL OR i.active = TRUE)
              AND (CAST(:type AS VARCHAR) IS NULL OR i.item_type = :type)
              AND (:stockItems = FALSE OR i.item_type <> 'SERVICE')
              AND (:stockOnly = FALSE OR COALESCE(b.quantity, 0) > 0)
              AND (lower(i.item_code) LIKE :q OR lower(i.item_name) LIKE :q)
            ORDER BY i.item_name, i.id
            LIMIT :limit OFFSET :offset
            """, p);
        boolean more = rows.size() > size;
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> r : rows.subList(0, Math.min(size, rows.size()))) {
            Map<String, Object> o = new LinkedHashMap<>(r);
            o.put("text", r.get("code") + " · " + r.get("name"));
            BigDecimal stock = (BigDecimal) r.get("stock");
            o.put("sub", ItemType.valueOf((String) r.get("itemType")).label() + " · " + r.get("uom")
                + (warehouseId == null ? "" : " · in store " + ItemStockService.plain(stock)));
            results.add(o);
        }
        return Map.of("results", results, "pagination", Map.of("more", more));
    }

    /**
     * Fabric lots a store holds, for a fabric transfer: order, fabric, colour and lot, with what is
     * on hand, reserved for delivery orders, and free to move.
     */
    public Map<String, Object> fabricLots(Long warehouseId, String q, Long lotId, int page, int size) {
        String like = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        MapSqlParameterSource p = new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("wh", warehouseId).addValue("q", like).addValue("lot", lotId)
            .addValue("limit", size + 1).addValue("offset", Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.id, l.stage, l.dye_lot AS "dyeLot", l.shade, l.grade, d.document_no AS "bpoNo", pt.name AS buyer,
                   g.construction, g.fabric_type AS "fabricType", cl.color_name AS "colorName",
                   COALESCE(NULLIF(u.symbol, ''), u.code) AS uom,
                   b.quantity AS "onHand", b.reserved_quantity AS reserved, b.quantity - b.reserved_quantity AS free, b.rolls
            FROM inv_fabric_balances b
            JOIN inv_fabric_lots l ON l.id = b.lot_id
            JOIN gbl_business_documents d ON d.id = l.bpo_document_id
            JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
            LEFT JOIN gbl_business_document_color_lines cl ON cl.id = l.color_line_id
            LEFT JOIN pty_parties pt ON pt.id = d.party_id
            LEFT JOIN inv_uoms u ON u.id = g.uom_id
            WHERE b.organization_id = :org AND b.warehouse_id = :wh
              AND (CAST(:lot AS BIGINT) IS NULL OR l.id = :lot)
              AND (CAST(:lot AS BIGINT) IS NOT NULL OR b.quantity - b.reserved_quantity > 0)
              AND (lower(d.document_no) LIKE :q OR lower(COALESCE(pt.name, '')) LIKE :q
                   OR lower(COALESCE(cl.color_name, '')) LIKE :q OR lower(COALESCE(g.construction, '')) LIKE :q
                   OR lower(COALESCE(l.dye_lot, '')) LIKE :q)
            ORDER BY d.document_no, g.group_no, cl.color_name NULLS FIRST, l.id
            LIMIT :limit OFFSET :offset
            """, p);
        boolean more = rows.size() > size;
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> r : rows.subList(0, Math.min(size, rows.size()))) {
            Map<String, Object> o = new LinkedHashMap<>(r);
            o.put("text", "%s · %s%s".formatted(r.get("bpoNo"), r.get("construction") == null ? "" : r.get("construction") + " ",
                r.get("colorName") == null ? "all colours" : r.get("colorName")));
            o.put("sub", "%s · %s free of %s %s".formatted(lotLabel(r), ItemStockService.plain((BigDecimal) r.get("free")),
                ItemStockService.plain((BigDecimal) r.get("onHand")), r.get("uom") == null ? "" : r.get("uom")));
            results.add(o);
        }
        return Map.of("results", results, "pagination", Map.of("more", more));
    }

    /** What a store holds of one fabric lot: [on hand, reserved]. */
    public BigDecimal[] fabricBalance(Long warehouseId, Long lotId) {
        return jdbc.query("""
            SELECT quantity, reserved_quantity FROM inv_fabric_balances WHERE warehouse_id = :wh AND lot_id = :lot
            """, new MapSqlParameterSource("wh", warehouseId).addValue("lot", lotId),
            rs -> rs.next() ? new BigDecimal[] { rs.getBigDecimal(1), rs.getBigDecimal(2) }
                            : new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
    }

    static String lotLabel(Map<String, Object> r) {
        if ("GREIGE".equals(r.get("stage"))) return "Greige";
        List<String> parts = new ArrayList<>();
        if (r.get("dyeLot") != null) parts.add("Lot " + r.get("dyeLot"));
        if (r.get("shade") != null) parts.add("Shade " + r.get("shade"));
        parts.add("Grade " + r.get("grade"));
        return String.join(" · ", parts);
    }

    /** A JPQL/SQL IN list may not be empty; no row has id -1. */
    static List<Long> nonEmpty(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }
}

package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.inventory.item.ItemType;
import com.asg.fabricerp.production.ProductionBoardService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * The item stock screens - the legacy Inventory dashboard, Stock report, Item Ledger report and
 * Monthly Stock report - read from the ledger ItemStockService keeps. Store-scoped like every
 * other screen: a store keeper sees their own stores.
 */
@Service
@Transactional(readOnly = true)
public class ItemStockQueries {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public ItemStockQueries(NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    private static final String BALANCES = """
        SELECT b.warehouse_id, w.name AS store, i.id AS item_id, i.item_code, i.item_name, i.item_type,
               c.name AS category, COALESCE(NULLIF(u.symbol, ''), u.code) AS uom,
               b.quantity, b.value, CASE WHEN b.quantity > 0 THEN round(b.value / b.quantity, 6) ELSE 0 END AS average_cost,
               i.reorder_level, i.minimum_stock,
               (i.reorder_level IS NOT NULL AND b.quantity <= i.reorder_level) AS low,
               b.updated_at AS last_moved
        FROM inv_item_balances b
        JOIN inv_items i ON i.id = b.item_id
        JOIN org_warehouses w ON w.id = b.warehouse_id
        JOIN inv_uoms u ON u.id = i.base_uom_id
        LEFT JOIN inv_item_categories c ON c.id = i.category_id
        WHERE b.organization_id = :org
          AND (:allStores OR b.warehouse_id IN (:stores))
          AND (CAST(:wh AS BIGINT) IS NULL OR b.warehouse_id = :wh)
          AND (CAST(:type AS VARCHAR) IS NULL OR i.item_type = :type)
          AND (:withZero OR b.quantity > 0)
          AND (:lowOnly = FALSE OR (i.reorder_level IS NOT NULL AND b.quantity <= i.reorder_level))
          AND (:q = '%' OR lower(i.item_code) LIKE :q OR lower(i.item_name) LIKE :q OR lower(COALESCE(c.name, '')) LIKE :q)
        """;

    /** Balances by store and item, with totals for the page's tiles. */
    public Map<String, Object> balances(Long warehouseId, ItemType type, String q, boolean withZero, boolean lowOnly,
                                        int page, int size) {
        MapSqlParameterSource p = scope().addValue("wh", warehouseId).addValue("type", type == null ? null : type.name())
            .addValue("q", like(q)).addValue("withZero", withZero).addValue("lowOnly", lowOnly)
            .addValue("limit", size).addValue("offset", Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM (" + BALANCES + ") s "
            + "ORDER BY s.store, s.item_name, s.item_id LIMIT :limit OFFSET :offset", p);
        Map<String, Object> totals = jdbc.queryForMap("""
            SELECT count(*) AS lines, count(DISTINCT item_id) AS items, COALESCE(SUM(value), 0) AS value,
                   count(*) FILTER (WHERE low) AS low
            FROM (""" + BALANCES + ") s", p);
        List<Map<String, Object>> byStore = jdbc.queryForList("""
            SELECT store, COALESCE(SUM(value), 0) AS value, count(*) AS lines FROM (""" + BALANCES + """
            ) s GROUP BY store ORDER BY 2 DESC""", p);
        return Map.of("rows", rows.stream().map(ProductionBoardService::camel).toList(),
            "total", totals.get("lines"), "totals", ProductionBoardService.camel(totals),
            "byStore", byStore.stream().map(ProductionBoardService::camel).toList());
    }

    /**
     * One item's ledger in one store (or all the user's stores): the balance brought forward on
     * {@code from}, then every move to {@code to} with the running quantity and value after it.
     */
    public Map<String, Object> ledger(Long itemId, Long warehouseId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfYear(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(start)) throw new IllegalArgumentException("The ledger's end date is before its start");
        MapSqlParameterSource p = scope().addValue("item", itemId).addValue("wh", warehouseId)
            .addValue("from", start).addValue("to", end);
        Map<String, Object> item = jdbc.queryForList("""
            SELECT i.id, i.item_code AS "itemCode", i.item_name AS "itemName", i.item_type AS "itemType",
                   COALESCE(NULLIF(u.symbol, ''), u.code) AS uom
            FROM inv_items i JOIN inv_uoms u ON u.id = i.base_uom_id WHERE i.id = :item AND i.organization_id = :org
            """, p).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        Map<String, Object> opening = jdbc.queryForMap("""
            SELECT COALESCE(SUM(quantity), 0) AS quantity, COALESCE(SUM(value), 0) AS value
            FROM inv_item_moves
            WHERE organization_id = :org AND item_id = :item AND move_date < :from
              AND (:allStores OR warehouse_id IN (:stores)) AND (CAST(:wh AS BIGINT) IS NULL OR warehouse_id = :wh)
            """, p);
        List<Map<String, Object>> moves = jdbc.queryForList("""
            SELECT m.id, m.move_date AS "moveDate", m.posted_at AS "postedAt", m.move_type AS "moveType",
                   m.quantity, m.unit_cost AS "unitCost", m.value, w.name AS store, m.remarks, m.posted_by AS "postedBy",
                   d.id AS "documentId", d.document_no AS "documentNo", d.document_type AS "documentType",
                   pt.name AS party, m.reverses_move_id AS "reversesMoveId"
            FROM inv_item_moves m
            JOIN org_warehouses w ON w.id = m.warehouse_id
            JOIN gbl_business_documents d ON d.id = m.document_id
            LEFT JOIN pty_parties pt ON pt.id = d.party_id
            WHERE m.organization_id = :org AND m.item_id = :item AND m.move_date BETWEEN :from AND :to
              AND (:allStores OR m.warehouse_id IN (:stores)) AND (CAST(:wh AS BIGINT) IS NULL OR m.warehouse_id = :wh)
            ORDER BY m.move_date, m.id
            """, p);
        BigDecimal qty = (BigDecimal) opening.get("quantity"), value = (BigDecimal) opening.get("value");
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> m : moves) {
            BigDecimal q = (BigDecimal) m.get("quantity");
            qty = qty.add(q);
            value = value.add((BigDecimal) m.get("value"));
            if (q.signum() > 0) in = in.add(q); else out = out.add(q.negate());
            Map<String, Object> row = new LinkedHashMap<>(m);
            row.put("slug", SupplyStep.of(com.asg.fabricerp.global.documents.DocumentType.valueOf((String) m.get("documentType")))
                .map(SupplyStep::slug).orElse(null));
            row.put("balance", qty);
            row.put("balanceValue", value);
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", item);
        result.put("from", start);
        result.put("to", end);
        result.put("openingQuantity", opening.get("quantity"));
        result.put("openingValue", opening.get("value"));
        result.put("in", in);
        result.put("out", out);
        result.put("closingQuantity", qty);
        result.put("closingValue", value);
        result.put("moves", rows);
        return result;
    }

    /**
     * The monthly stock report: for each store and item that held or moved stock in the month,
     * opening, received, issued and closing - quantity and value - straight from the ledger.
     */
    public Map<String, Object> monthly(YearMonth month, Long warehouseId, ItemType type) {
        MapSqlParameterSource p = scope().addValue("from", month.atDay(1)).addValue("to", month.atEndOfMonth())
            .addValue("wh", warehouseId).addValue("type", type == null ? null : type.name());
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT w.name AS store, i.id AS item_id, i.item_code, i.item_name, i.item_type,
                   COALESCE(NULLIF(u.symbol, ''), u.code) AS uom,
                   SUM(m.quantity) FILTER (WHERE m.move_date < :from) AS opening_quantity,
                   SUM(m.value) FILTER (WHERE m.move_date < :from) AS opening_value,
                   SUM(m.quantity) FILTER (WHERE m.move_date >= :from AND m.quantity > 0) AS in_quantity,
                   SUM(m.value) FILTER (WHERE m.move_date >= :from AND m.quantity > 0) AS in_value,
                   -SUM(m.quantity) FILTER (WHERE m.move_date >= :from AND m.quantity < 0) AS out_quantity,
                   -SUM(m.value) FILTER (WHERE m.move_date >= :from AND m.quantity < 0) AS out_value,
                   SUM(m.quantity) AS closing_quantity, SUM(m.value) AS closing_value
            FROM inv_item_moves m
            JOIN inv_items i ON i.id = m.item_id
            JOIN org_warehouses w ON w.id = m.warehouse_id
            JOIN inv_uoms u ON u.id = i.base_uom_id
            WHERE m.organization_id = :org AND m.move_date <= :to
              AND (:allStores OR m.warehouse_id IN (:stores))
              AND (CAST(:wh AS BIGINT) IS NULL OR m.warehouse_id = :wh)
              AND (CAST(:type AS VARCHAR) IS NULL OR i.item_type = :type)
            GROUP BY w.name, i.id, i.item_code, i.item_name, i.item_type, u.symbol, u.code
            HAVING SUM(m.quantity) <> 0 OR count(*) FILTER (WHERE m.move_date >= :from) > 0
            ORDER BY w.name, i.item_name
            """, p);
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = ProductionBoardService.camel(r);
            for (String k : List.of("openingQuantity", "openingValue", "inQuantity", "inValue", "outQuantity", "outValue",
                                    "closingQuantity", "closingValue")) {
                BigDecimal v = row.get(k) == null ? BigDecimal.ZERO : (BigDecimal) row.get(k);
                row.put(k, v);
                if (k.endsWith("Value")) totals.merge(k, v, BigDecimal::add);
            }
            out.add(row);
        }
        return Map.of("month", month.toString(), "label", InventoryPeriodService.label(month), "rows", out, "totals", totals);
    }

    private MapSqlParameterSource scope() {
        RowScope scope = context.requireRowScope();
        return new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("allStores", !scope.restricts(ScopeDimension.WAREHOUSE))
            .addValue("stores", SupplyQueries.nonEmpty(scope.idsForQuery(ScopeDimension.WAREHOUSE)));
    }

    private static String like(String q) {
        return q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
    }
}

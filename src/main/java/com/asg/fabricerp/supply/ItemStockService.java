package com.asg.fabricerp.supply;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Every change to general item stock - the only code that writes inv_item_moves and
 * inv_item_balances, so the ledger and the balances cannot disagree.
 *
 * <p><b>Valuation is moving weighted average, per store and item.</b> What comes in is valued at
 * its own cost (an MRR at the purchase order's price in taka, a transfer receive at what the
 * transfer issue took out); what goes out is valued at the store's average at that moment, and
 * the last unit out takes whatever value is left, so an empty store is worth exactly nothing.
 *
 * <p>Each move locks its balance row first ({@code FOR UPDATE}) and is refused in plain words
 * before the database's own CHECK would be: two store keepers issuing the last ten pieces at once
 * cannot both succeed. Runs inside the caller's transaction; a refusal rolls the posting back.
 */
@Service
public class ItemStockService {

    /** Unit costs and averages are carried to six places, as the ledger's columns are. */
    static final int COST_SCALE = 6;

    /** One ledger row to write. {@code what} names the line in a refusal: "MI-2026-000004, Cotton yarn 30/1". */
    public record Move(Long warehouseId, Long itemId, BigDecimal quantity, String moveType, Long documentId,
                       Long lineId, LocalDate date, String what) { }

    /** A store's holding of one item. */
    public record Balance(BigDecimal quantity, BigDecimal value) {
        public BigDecimal averageCost() {
            return quantity.signum() == 0 ? BigDecimal.ZERO : value.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
        }
    }

    /** What a move wrote: its signed quantity and value, and the unit cost it was valued at. */
    public record Posted(BigDecimal quantity, BigDecimal value, BigDecimal unitCost) { }

    private final NamedParameterJdbcTemplate jdbc;

    public ItemStockService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------ moves

    /** Stock into a store at {@code unitCost} a unit. */
    public Posted receive(Long orgId, Move m, BigDecimal unitCost, String user) {
        positive(m.quantity());
        BigDecimal cost = unitCost == null ? BigDecimal.ZERO : unitCost.max(BigDecimal.ZERO).setScale(COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal value = m.quantity().multiply(cost).setScale(COST_SCALE, RoundingMode.HALF_UP);
        jdbc.update("""
            INSERT INTO inv_item_balances (warehouse_id, item_id, organization_id, quantity, value, updated_at)
            VALUES (:wh, :item, :org, :qty, :value, now())
            ON CONFLICT (warehouse_id, item_id) DO UPDATE
               SET quantity = inv_item_balances.quantity + EXCLUDED.quantity,
                   value = inv_item_balances.value + EXCLUDED.value, updated_at = now()
            """, new MapSqlParameterSource("wh", m.warehouseId()).addValue("item", m.itemId()).addValue("org", orgId)
                .addValue("qty", m.quantity()).addValue("value", value));
        insertMove(orgId, m, m.quantity(), cost, value, null, null, user);
        return new Posted(m.quantity(), value, cost);
    }

    /** Stock out of a store at its current average cost; refused beyond what the store holds. */
    public Posted issue(Long orgId, Move m, String user) {
        positive(m.quantity());
        Balance b = lock(m.warehouseId(), m.itemId());
        if (b.quantity().compareTo(m.quantity()) < 0) {
            throw new IllegalStateException("%s: %s to take out, but the store holds only %s"
                .formatted(m.what(), plain(m.quantity()), plain(b.quantity())));
        }
        BigDecimal value = outValue(b, m.quantity());
        adjust(m.warehouseId(), m.itemId(), m.quantity().negate(), value.negate());
        BigDecimal cost = unitCostOf(value, m.quantity());
        insertMove(orgId, m, m.quantity().negate(), cost, value.negate(), null, null, user);
        return new Posted(m.quantity().negate(), value.negate(), cost);
    }

    /**
     * Writes the reversing row of every move {@code documentId} posted, dated {@code date}. What
     * went out comes back at exactly the value it took; what came in goes out at the store's
     * average - refused when the store no longer holds it (it has been issued or sent on).
     */
    public List<Posted> reverseDocument(Long orgId, Long documentId, LocalDate date, String reason, String documentNo,
                                        String user) {
        List<Object[]> moves = jdbc.query("""
            SELECT m.id, m.warehouse_id, m.item_id, m.quantity, m.value, m.line_id, i.item_name
            FROM inv_item_moves m
            JOIN inv_items i ON i.id = m.item_id
            WHERE m.document_id = :doc AND m.move_type <> 'REVERSAL'
              AND NOT EXISTS (SELECT 1 FROM inv_item_moves r WHERE r.reverses_move_id = m.id)
            ORDER BY m.id DESC
            """, new MapSqlParameterSource("doc", documentId), (rs, i) -> new Object[] {
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                rs.getObject(6) == null ? null : rs.getLong(6), rs.getString(7) });
        List<Posted> out = new ArrayList<>();
        for (Object[] row : moves) {
            Long moveId = (Long) row[0], wh = (Long) row[1], item = (Long) row[2], lineId = (Long) row[5];
            BigDecimal qty = (BigDecimal) row[3], value = (BigDecimal) row[4];
            Move reversal = new Move(wh, item, qty.abs(), "REVERSAL", documentId, lineId, date, documentNo + ", " + row[6]);
            Balance b = lock(wh, item);
            BigDecimal backQty, backValue;
            if (qty.signum() > 0) {
                if (b.quantity().compareTo(qty) < 0) {
                    throw new IllegalStateException(("%s cannot be cancelled: of the %s %s it brought in, the store now holds only %s - "
                        + "the rest has been issued or sent on. Cancel those documents first.")
                        .formatted(documentNo, plain(qty), row[6], plain(b.quantity())));
                }
                backQty = qty.negate();
                backValue = outValue(b, qty).negate();
            } else {
                backQty = qty.negate();
                backValue = value.negate();
            }
            adjust(wh, item, backQty, backValue);
            BigDecimal cost = unitCostOf(backValue.abs(), backQty.abs());
            insertMove(orgId, reversal, backQty, cost, backValue, moveId, reason, user);
            out.add(new Posted(backQty, backValue, cost));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------ reads

    public Balance balance(Long warehouseId, Long itemId) {
        return jdbc.query("""
            SELECT quantity, value FROM inv_item_balances WHERE warehouse_id = :wh AND item_id = :item
            """, new MapSqlParameterSource("wh", warehouseId).addValue("item", itemId),
            rs -> rs.next() ? new Balance(rs.getBigDecimal(1), rs.getBigDecimal(2)) : new Balance(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    /** The unit cost a document line was posted at (what a transfer issue took out, say); null if it was not posted. */
    public BigDecimal postedUnitCost(Long lineId) {
        return jdbc.query("""
            SELECT m.unit_cost FROM inv_item_moves m
            WHERE m.line_id = :line AND m.move_type <> 'REVERSAL'
              AND NOT EXISTS (SELECT 1 FROM inv_item_moves r WHERE r.reverses_move_id = m.id)
            ORDER BY m.id DESC LIMIT 1
            """, new MapSqlParameterSource("line", lineId), rs -> rs.next() ? rs.getBigDecimal(1) : null);
    }

    // ------------------------------------------------------------------------------- internals

    /**
     * The value {@code quantity} takes out of {@code b}: its share at the average cost, and all of
     * what is left when it empties the store - never more than the store is worth.
     */
    static BigDecimal outValue(Balance b, BigDecimal quantity) {
        if (quantity.compareTo(b.quantity()) >= 0) return b.value();
        return quantity.multiply(b.averageCost()).setScale(COST_SCALE, RoundingMode.HALF_UP).min(b.value());
    }

    static BigDecimal unitCostOf(BigDecimal value, BigDecimal quantity) {
        return quantity.signum() == 0 ? BigDecimal.ZERO
            : value.abs().divide(quantity.abs(), COST_SCALE, RoundingMode.HALF_UP);
    }

    private Balance lock(Long warehouseId, Long itemId) {
        Balance b = jdbc.query("""
            SELECT quantity, value FROM inv_item_balances
            WHERE warehouse_id = :wh AND item_id = :item FOR UPDATE
            """, new MapSqlParameterSource("wh", warehouseId).addValue("item", itemId),
            rs -> rs.next() ? new Balance(rs.getBigDecimal(1), rs.getBigDecimal(2)) : null);
        return b == null ? new Balance(BigDecimal.ZERO, BigDecimal.ZERO) : b;
    }

    private void adjust(Long warehouseId, Long itemId, BigDecimal quantity, BigDecimal value) {
        int rows = jdbc.update("""
            UPDATE inv_item_balances SET quantity = quantity + :qty, value = value + :value, updated_at = now()
            WHERE warehouse_id = :wh AND item_id = :item
            """, new MapSqlParameterSource("qty", quantity).addValue("value", value)
                .addValue("wh", warehouseId).addValue("item", itemId));
        if (rows == 0) {
            if (quantity.signum() < 0) throw new IllegalStateException("The store holds none of that item");
            jdbc.update("""
                INSERT INTO inv_item_balances (warehouse_id, item_id, organization_id, quantity, value, updated_at)
                SELECT :wh, :item, w.organization_id, :qty, :value, now() FROM org_warehouses w WHERE w.id = :wh
                """, new MapSqlParameterSource("qty", quantity).addValue("value", value)
                    .addValue("wh", warehouseId).addValue("item", itemId));
        }
    }

    private void insertMove(Long orgId, Move m, BigDecimal signedQty, BigDecimal unitCost, BigDecimal signedValue,
                            Long reverses, String remarks, String user) {
        jdbc.update("""
            INSERT INTO inv_item_moves (organization_id, warehouse_id, item_id, move_date, quantity, unit_cost, value,
                                        move_type, document_id, line_id, reverses_move_id, remarks, posted_by, posted_at)
            VALUES (:org, :wh, :item, :date, :qty, :cost, :value, :type, :doc, :line, :rev, :remarks, :user, now())
            """, new MapSqlParameterSource("org", orgId).addValue("wh", m.warehouseId()).addValue("item", m.itemId())
                .addValue("date", m.date() == null ? LocalDate.now() : m.date())
                .addValue("qty", signedQty).addValue("cost", unitCost).addValue("value", signedValue)
                .addValue("type", m.moveType()).addValue("doc", m.documentId()).addValue("line", m.lineId())
                .addValue("rev", reverses).addValue("remarks", remarks == null ? null : truncate(remarks, 300))
                .addValue("user", user));
    }

    private static void positive(BigDecimal q) {
        if (q == null || q.signum() <= 0) throw new IllegalArgumentException("A stock quantity must be more than zero");
    }

    static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

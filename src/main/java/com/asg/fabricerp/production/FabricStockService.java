package com.asg.fabricerp.production;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.asg.fabricerp.production.LineDrawLedger.plain;

/**
 * Every change to fabric stock - the only code that writes inv_fabric_lots, inv_fabric_moves,
 * inv_fabric_balances and inv_fabric_reservations, so the ledger and the balances cannot disagree.
 *
 * <p>Each move locks its balance row first ({@code FOR UPDATE}) and checks it in plain words
 * before the database's own CHECK would: two store keepers posting against the last 500 m at
 * once cannot both succeed - the second sees the first's balance and is refused.
 *
 * <p>Runs inside the caller's transaction; a refusal throws and rolls the whole posting back.
 */
@Service
public class FabricStockService {

    public static final String GREIGE = "GREIGE";
    public static final String FINISHED = "FINISHED";

    /** What a lot is: a production order's fabric line (and colour), and for finished cloth its dye lot, shade and grade. */
    public record LotKey(String stage, Long bpoId, Long groupId, Long colourLineId,
                         String dyeLot, String shade, String grade) { }

    /** One ledger row written by a posting. */
    public record Move(Long warehouseId, Long lotId, BigDecimal quantity, int rolls, Long uomId, String moveType,
                       Long documentId, Long lineId) { }

    private final NamedParameterJdbcTemplate jdbc;

    public FabricStockService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------ lots

    /** The lot for {@code key}, created on first use. */
    public long lotFor(Long orgId, LotKey key, String user) {
        MapSqlParameterSource p = new MapSqlParameterSource("org", orgId)
            .addValue("stage", key.stage()).addValue("bpo", key.bpoId()).addValue("grp", key.groupId())
            .addValue("line", key.colourLineId()).addValue("dyeLot", key.dyeLot()).addValue("shade", key.shade())
            .addValue("grade", key.grade()).addValue("user", user);
        jdbc.update("""
            INSERT INTO inv_fabric_lots (organization_id, stage, bpo_document_id, line_group_id, color_line_id,
                                         dye_lot, shade, grade, created_by, created_at)
            VALUES (:org, :stage, :bpo, :grp, :line, :dyeLot, :shade, :grade, :user, now())
            ON CONFLICT (organization_id, stage, line_group_id, COALESCE(color_line_id, 0),
                         COALESCE(dye_lot, ''), COALESCE(shade, ''), COALESCE(grade, '')) DO NOTHING
            """, p);
        return jdbc.queryForObject("""
            SELECT id FROM inv_fabric_lots
            WHERE organization_id = :org AND stage = :stage AND line_group_id = :grp
              AND COALESCE(color_line_id, 0) = COALESCE(CAST(:line AS BIGINT), 0)
              AND COALESCE(dye_lot, '') = COALESCE(CAST(:dyeLot AS VARCHAR), '')
              AND COALESCE(shade, '') = COALESCE(CAST(:shade AS VARCHAR), '')
              AND COALESCE(grade, '') = COALESCE(CAST(:grade AS VARCHAR), '')
            """, p, Long.class);
    }

    /** A lot as the documents need it. */
    public record Lot(Long id, String stage, Long bpoId, Long groupId, Long colourLineId,
                      String dyeLot, String shade, String grade) {
        public String label() {
            if (GREIGE.equals(stage)) return "Greige";
            List<String> parts = new ArrayList<>();
            if (dyeLot != null) parts.add("Lot " + dyeLot);
            if (shade != null) parts.add("Shade " + shade);
            parts.add("Grade " + grade);
            return String.join(" · ", parts);
        }
    }

    public Lot lot(Long orgId, Long lotId) {
        return jdbc.query("""
            SELECT id, stage, bpo_document_id, line_group_id, color_line_id, dye_lot, shade, grade
            FROM inv_fabric_lots WHERE id = :id AND organization_id = :org
            """, new MapSqlParameterSource("id", lotId).addValue("org", orgId), rs -> rs.next()
            ? new Lot(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), (Long) rs.getObject(5),
                      rs.getString(6), rs.getString(7), rs.getString(8))
            : null);
    }

    // ------------------------------------------------------------------------------------ moves

    /** Fabric into a store: greige off the loom, finished cloth from the dye house. */
    public void receive(Long orgId, Move m, String user) {
        positive(m.quantity());
        jdbc.update("""
            INSERT INTO inv_fabric_balances (warehouse_id, lot_id, organization_id, quantity, reserved_quantity, rolls, updated_at)
            VALUES (:wh, :lot, :org, :qty, 0, :rolls, now())
            ON CONFLICT (warehouse_id, lot_id) DO UPDATE
               SET quantity = inv_fabric_balances.quantity + EXCLUDED.quantity,
                   rolls = inv_fabric_balances.rolls + EXCLUDED.rolls, updated_at = now()
            """, balance(orgId, m.warehouseId(), m.lotId()).addValue("qty", m.quantity()).addValue("rolls", m.rolls()));
        insertMove(orgId, m, m.quantity(), m.rolls(), null, null, user);
    }

    /** Fabric out of a store from its unreserved balance: greige handed to a dye batch. */
    public void issue(Long orgId, Move m, String what, String user) {
        positive(m.quantity());
        Balance b = lock(m.warehouseId(), m.lotId());
        BigDecimal available = b.quantity().subtract(b.reserved());
        if (available.compareTo(m.quantity()) < 0) {
            throw new IllegalStateException("%s: %s to issue, but the store holds only %s free (%s on hand, %s reserved)"
                .formatted(what, plain(m.quantity()), plain(available.max(BigDecimal.ZERO)), plain(b.quantity()), plain(b.reserved())));
        }
        adjust(m.warehouseId(), m.lotId(), m.quantity().negate(), BigDecimal.ZERO, -m.rolls());
        insertMove(orgId, m, m.quantity().negate(), -m.rolls(), null, null, user);
    }

    /** Fabric out against a delivery order line's reservation: the reservation shrinks with the stock. */
    public void deliverReserved(Long orgId, Long doLineId, Move m, String what, String user) {
        positive(m.quantity());
        BigDecimal held = reservedOn(doLineId, m.warehouseId(), m.lotId(), true);
        if (held.compareTo(m.quantity()) < 0) {
            throw new IllegalStateException("%s: %s to deliver, but its delivery order holds only %s of this lot"
                .formatted(what, plain(m.quantity()), plain(held)));
        }
        lock(m.warehouseId(), m.lotId());
        jdbc.update("""
            UPDATE inv_fabric_reservations SET quantity = quantity - :qty, updated_at = now()
            WHERE color_line_id = :line AND warehouse_id = :wh AND lot_id = :lot
            """, new MapSqlParameterSource("qty", m.quantity()).addValue("line", doLineId)
                .addValue("wh", m.warehouseId()).addValue("lot", m.lotId()));
        adjust(m.warehouseId(), m.lotId(), m.quantity().negate(), m.quantity().negate(), -m.rolls());
        insertMove(orgId, m, m.quantity().negate(), -m.rolls(), null, null, user);
    }

    /** A reversed move, as the caller needs it to undo what the move's document did around it. */
    public record Reversed(Long warehouseId, Long lotId, BigDecimal quantity, String moveType, Long lineId) { }

    /**
     * Writes the exact reversing row of every move {@code documentId} posted. Refused when the
     * stock it brought in has already gone on (issued, reserved or delivered).
     */
    public List<Reversed> reverseDocument(Long orgId, Long documentId, String reason, String documentNo, String user) {
        List<Reversed> reversed = new ArrayList<>();
        List<Object[]> moves = jdbc.query("""
            SELECT m.id, m.warehouse_id, m.lot_id, m.quantity, m.rolls, m.uom_id, m.move_type, m.color_line_id
            FROM inv_fabric_moves m
            WHERE m.document_id = :doc AND m.move_type <> 'REVERSAL'
              AND NOT EXISTS (SELECT 1 FROM inv_fabric_moves r WHERE r.reverses_move_id = m.id)
            ORDER BY m.id
            """, new MapSqlParameterSource("doc", documentId), (rs, i) -> new Object[] {
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4), rs.getInt(5),
                rs.getObject(6), rs.getString(7), rs.getObject(8) });
        for (Object[] m : moves) {
            Long moveId = (Long) m[0], wh = (Long) m[1], lot = (Long) m[2];
            BigDecimal qty = (BigDecimal) m[3];
            int rolls = (Integer) m[4];
            Balance b = lock(wh, lot);
            if (qty.signum() > 0 && b.quantity().subtract(b.reserved()).compareTo(qty) < 0) {
                throw new IllegalStateException(("%s cannot be cancelled: of the %s it received, only %s is still free in the "
                    + "store - the rest has been issued, reserved or delivered. Cancel those documents first.")
                    .formatted(documentNo, plain(qty), plain(b.quantity().subtract(b.reserved()).max(BigDecimal.ZERO))));
            }
            adjust(wh, lot, qty.negate(), BigDecimal.ZERO, -rolls);
            Move reversal = new Move(wh, lot, qty.negate(), -rolls, m[5] == null ? null : ((Number) m[5]).longValue(),
                "REVERSAL", documentId, m[7] == null ? null : ((Number) m[7]).longValue());
            insertMove(orgId, reversal, qty.negate(), -rolls, moveId, reason, user);
            reversed.add(new Reversed(wh, lot, qty, (String) m[6], reversal.lineId()));
        }
        return reversed;
    }

    // ------------------------------------------------------------------------------ reservations

    /** Holds {@code quantity} of a lot for a delivery order line, so no other order can promise it. */
    public void reserve(Long orgId, Long doLineId, Long warehouseId, Long lotId, BigDecimal quantity, String what) {
        positive(quantity);
        Balance b = lock(warehouseId, lotId);
        BigDecimal free = b.quantity().subtract(b.reserved());
        if (free.compareTo(quantity) < 0) {
            throw new IllegalStateException("%s: %s to reserve, but only %s of that lot is free in the store (%s on hand, %s already reserved)"
                .formatted(what, plain(quantity), plain(free.max(BigDecimal.ZERO)), plain(b.quantity()), plain(b.reserved())));
        }
        adjust(warehouseId, lotId, BigDecimal.ZERO, quantity, 0);
        jdbc.update("""
            INSERT INTO inv_fabric_reservations (organization_id, color_line_id, warehouse_id, lot_id, quantity, updated_at)
            VALUES (:org, :line, :wh, :lot, :qty, now())
            ON CONFLICT (color_line_id, warehouse_id, lot_id)
            DO UPDATE SET quantity = inv_fabric_reservations.quantity + EXCLUDED.quantity, updated_at = now()
            """, new MapSqlParameterSource("org", orgId).addValue("line", doLineId).addValue("wh", warehouseId)
                .addValue("lot", lotId).addValue("qty", quantity));
    }

    /** Gives back whatever a delivery order line still holds. */
    public BigDecimal releaseReservation(Long doLineId) {
        BigDecimal total = BigDecimal.ZERO;
        List<Object[]> held = jdbc.query("""
            SELECT warehouse_id, lot_id, quantity FROM inv_fabric_reservations
            WHERE color_line_id = :line AND quantity > 0 FOR UPDATE
            """, new MapSqlParameterSource("line", doLineId),
            (rs, i) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3) });
        for (Object[] r : held) {
            lock((Long) r[0], (Long) r[1]);
            adjust((Long) r[0], (Long) r[1], BigDecimal.ZERO, ((BigDecimal) r[2]).negate(), 0);
            total = total.add((BigDecimal) r[2]);
        }
        jdbc.update("UPDATE inv_fabric_reservations SET quantity = 0, updated_at = now() WHERE color_line_id = :line",
            new MapSqlParameterSource("line", doLineId));
        return total;
    }

    /** What a delivery order line still holds, in every store and lot. */
    public BigDecimal reservedFor(Long doLineId) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(quantity), 0) FROM inv_fabric_reservations WHERE color_line_id = :line
            """, new MapSqlParameterSource("line", doLineId), BigDecimal.class);
    }

    // ------------------------------------------------------------------------------- internals

    private record Balance(BigDecimal quantity, BigDecimal reserved) { }

    private Balance lock(Long warehouseId, Long lotId) {
        Balance b = jdbc.query("""
            SELECT quantity, reserved_quantity FROM inv_fabric_balances
            WHERE warehouse_id = :wh AND lot_id = :lot FOR UPDATE
            """, new MapSqlParameterSource("wh", warehouseId).addValue("lot", lotId),
            rs -> rs.next() ? new Balance(rs.getBigDecimal(1), rs.getBigDecimal(2)) : null);
        return b == null ? new Balance(BigDecimal.ZERO, BigDecimal.ZERO) : b;
    }

    private BigDecimal reservedOn(Long doLineId, Long warehouseId, Long lotId, boolean forUpdate) {
        return jdbc.query("""
            SELECT quantity FROM inv_fabric_reservations
            WHERE color_line_id = :line AND warehouse_id = :wh AND lot_id = :lot
            """ + (forUpdate ? " FOR UPDATE" : ""),
            new MapSqlParameterSource("line", doLineId).addValue("wh", warehouseId).addValue("lot", lotId),
            rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);
    }

    private void adjust(Long warehouseId, Long lotId, BigDecimal quantity, BigDecimal reserved, int rolls) {
        int rows = jdbc.update("""
            UPDATE inv_fabric_balances
            SET quantity = quantity + :qty, reserved_quantity = reserved_quantity + :res,
                rolls = GREATEST(rolls + :rolls, 0), updated_at = now()
            WHERE warehouse_id = :wh AND lot_id = :lot
            """, new MapSqlParameterSource("qty", quantity).addValue("res", reserved).addValue("rolls", rolls)
                .addValue("wh", warehouseId).addValue("lot", lotId));
        if (rows == 0) throw new IllegalStateException("The store holds none of that lot");
    }

    private void insertMove(Long orgId, Move m, BigDecimal signedQty, int signedRolls, Long reverses, String remarks, String user) {
        jdbc.update("""
            INSERT INTO inv_fabric_moves (organization_id, warehouse_id, lot_id, quantity, rolls, uom_id, move_type,
                                          document_id, color_line_id, reverses_move_id, remarks, posted_by, posted_at)
            VALUES (:org, :wh, :lot, :qty, :rolls, :uom, :type, :doc, :line, :rev, :remarks, :user, now())
            """, new MapSqlParameterSource("org", orgId).addValue("wh", m.warehouseId()).addValue("lot", m.lotId())
                .addValue("qty", signedQty).addValue("rolls", signedRolls).addValue("uom", m.uomId())
                .addValue("type", m.moveType()).addValue("doc", m.documentId()).addValue("line", m.lineId())
                .addValue("rev", reverses).addValue("remarks", remarks == null ? null : truncate(remarks, 300))
                .addValue("user", user));
    }

    private static MapSqlParameterSource balance(Long orgId, Long warehouseId, Long lotId) {
        return new MapSqlParameterSource("org", orgId).addValue("wh", warehouseId).addValue("lot", lotId);
    }

    private static void positive(BigDecimal q) {
        if (q == null || q.signum() <= 0) throw new IllegalArgumentException("A stock quantity must be more than zero");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

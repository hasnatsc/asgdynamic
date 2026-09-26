package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * gbl_line_draws: one counter per parent line and stream. The only code that writes it.
 *
 * <p>A draw is one atomic upsert that returns the new total, checked against the cap in the same
 * statement's transaction: two users drawing the last metres of one line at once cannot both
 * succeed, because the second waits on the first's row lock and then sees its total. A refused
 * draw throws, and the caller's transaction rolls the counter back with everything else.
 *
 * <p>Each parent line's {@code fulfilled_quantity} mirrors its principal stream
 * ({@link ChainStep#principalChildOf}), capped at the line's quantity.
 */
@Component
public class LineDrawLedger {

    public enum SourceKind { GROUP, COLOUR }

    private final NamedParameterJdbcTemplate jdbc;

    public LineDrawLedger(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Adds {@code quantity} to a stream and refuses it when the total would pass {@code cap}.
     *
     * @param parentType the parent line's document type, for the fulfilled-quantity mirror
     * @param what       names the line in the refusal: "Colour Navy on BPO-2026-000014"
     * @return the stream's new total
     */
    public BigDecimal draw(Long orgId, SourceKind kind, Long sourceId, String stream, BigDecimal quantity,
                           BigDecimal cap, DocumentType parentType, Supplier<String> what) {
        if (quantity == null || quantity.signum() == 0) return drawn(kind, sourceId, stream);
        if (quantity.signum() < 0) throw new IllegalArgumentException("A quantity cannot be negative");
        BigDecimal total = jdbc.queryForObject("""
            INSERT INTO gbl_line_draws (organization_id, source_kind, source_id, stream, drawn_quantity, updated_at)
            VALUES (:org, :kind, :id, :stream, :qty, now())
            ON CONFLICT (source_kind, source_id, stream)
            DO UPDATE SET drawn_quantity = gbl_line_draws.drawn_quantity + EXCLUDED.drawn_quantity, updated_at = now()
            RETURNING drawn_quantity
            """, params(kind, sourceId, stream).addValue("org", orgId).addValue("qty", quantity), BigDecimal.class);
        if (cap != null && total.compareTo(cap) > 0) {
            BigDecimal before = total.subtract(quantity);
            BigDecimal left = cap.subtract(before).max(BigDecimal.ZERO);
            throw new IllegalStateException("%s: %s asked for, but only %s is left (%s allowed, %s already taken) - %s over"
                .formatted(what.get(), plain(quantity), plain(left), plain(cap), plain(before), plain(total.subtract(cap))));
        }
        mirror(kind, sourceId, stream, parentType);
        return total;
    }

    /** Gives back what a draw took. Never takes a stream below zero. */
    public void release(SourceKind kind, Long sourceId, String stream, BigDecimal quantity, DocumentType parentType) {
        if (quantity == null || quantity.signum() <= 0) return;
        jdbc.update("""
            UPDATE gbl_line_draws SET drawn_quantity = GREATEST(drawn_quantity - :qty, 0), updated_at = now()
            WHERE source_kind = :kind AND source_id = :id AND stream = :stream
            """, params(kind, sourceId, stream).addValue("qty", quantity));
        mirror(kind, sourceId, stream, parentType);
    }

    public BigDecimal drawn(SourceKind kind, Long sourceId, String stream) {
        return jdbc.query("""
            SELECT drawn_quantity FROM gbl_line_draws
            WHERE source_kind = :kind AND source_id = :id AND stream = :stream
            """, params(kind, sourceId, stream), rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);
    }

    /** Every stream drawn on each of {@code ids}: id -> stream -> total. */
    public Map<Long, Map<String, BigDecimal>> streams(SourceKind kind, Collection<Long> ids) {
        Map<Long, Map<String, BigDecimal>> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        jdbc.query("""
            SELECT source_id, stream, drawn_quantity FROM gbl_line_draws
            WHERE source_kind = :kind AND source_id IN (:ids) AND drawn_quantity > 0
            """, new MapSqlParameterSource("kind", kind.name()).addValue("ids", ids), rs -> {
            out.computeIfAbsent(rs.getLong(1), k -> new LinkedHashMap<>()).put(rs.getString(2), rs.getBigDecimal(3));
        });
        return out;
    }

    /** An approved revision takes over its predecessor's lines: every stream on {@code from} moves to {@code to}. */
    public void repoint(SourceKind kind, Long from, Long to) {
        jdbc.update("""
            UPDATE gbl_line_draws SET source_id = :to, updated_at = now()
            WHERE source_kind = :kind AND source_id = :from
            """, new MapSqlParameterSource("kind", kind.name()).addValue("from", from).addValue("to", to));
    }

    private void mirror(SourceKind kind, Long sourceId, String stream, DocumentType parentType) {
        if (kind != SourceKind.COLOUR || parentType == null) return;
        ChainStep.principalChildOf(parentType).filter(c -> c.stream().equals(stream)).ifPresent(c ->
            jdbc.update("""
                UPDATE gbl_business_document_color_lines cl
                SET fulfilled_quantity = LEAST(COALESCE((SELECT d.drawn_quantity FROM gbl_line_draws d
                                                         WHERE d.source_kind = 'COLOUR' AND d.source_id = cl.id
                                                           AND d.stream = :stream), 0), cl.quantity)
                WHERE cl.id = :id
                """, new MapSqlParameterSource("stream", stream).addValue("id", sourceId)));
    }

    private static MapSqlParameterSource params(SourceKind kind, Long id, String stream) {
        return new MapSqlParameterSource("kind", kind.name()).addValue("id", id).addValue("stream", stream);
    }

    static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}

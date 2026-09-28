package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * A production order's pre-delivery schedule: the deliveries planned on it - a PP submission of
 * 20 on the 25th, the full delivery on the 30th - by colour, each with its delivery type and serial
 * number. Planning only: it draws nothing and moves no stock; delivery schedules still do that.
 *
 * <p>A row names the <em>booking</em> colour line the order's line was drawn from, which stays the
 * same when the order is edited (its own lines are rebuilt) or revised.
 */
@Component
public class PreDeliverySchedule {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public PreDeliverySchedule(NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    /** Replaces the order's schedule with {@code rows}, in their order. Runs in the caller's transaction. */
    public void replace(BusinessDocument order, List<ChainDocumentRequest.PreDelivery> rows) {
        Map<Long, BigDecimal> colours = new LinkedHashMap<>();
        for (var g : order.getLineGroups()) {
            for (BusinessDocumentColorLine l : g.getColorLines()) {
                if (l.getSourceColorLine() != null) colours.merge(l.getSourceColorLine().getId(), l.getQuantity(), BigDecimal::add);
            }
        }
        MapSqlParameterSource doc = new MapSqlParameterSource("doc", order.getId()).addValue("org", order.getOrganizationId());
        Set<Long> alreadyUsed = new HashSet<>(jdbc.queryForList(
            "SELECT delivery_type_id FROM fab_bpo_pre_deliveries WHERE document_id = :doc", doc, Long.class));
        Map<Long, Boolean> types = new HashMap<>();
        jdbc.query("SELECT id, active FROM fab_delivery_types WHERE organization_id = :org AND deleted = FALSE", doc,
            rs -> { types.put(rs.getLong(1), rs.getBoolean(2)); });

        int n = 0;
        List<MapSqlParameterSource> inserts = new ArrayList<>();
        for (ChainDocumentRequest.PreDelivery r : rows) {
            n++;
            String at = "Pre-delivery row " + n + ": ";
            if (r.deliveryTypeId() == null || !types.containsKey(r.deliveryTypeId())) {
                throw new IllegalArgumentException(at + "choose the delivery type");
            }
            if (!types.get(r.deliveryTypeId()) && !alreadyUsed.contains(r.deliveryTypeId())) {
                throw new IllegalArgumentException(at + "that delivery type is retired");
            }
            if (r.deliveryDate() == null) throw new IllegalArgumentException(at + "give the delivery date");
            if (r.sourceId() == null || !colours.containsKey(r.sourceId())) {
                throw new IllegalArgumentException(at + "choose one of the order's colours");
            }
            if (r.quantity() == null || r.quantity().signum() <= 0) throw new IllegalArgumentException(at + "the quantity must be more than 0");
            if (r.serialNo() == null || r.serialNo() <= 0) throw new IllegalArgumentException(at + "the serial number must be 1 or more");
            inserts.add(new MapSqlParameterSource("org", order.getOrganizationId()).addValue("doc", order.getId())
                .addValue("no", n).addValue("type", r.deliveryTypeId()).addValue("date", r.deliveryDate())
                .addValue("colour", r.sourceId()).addValue("qty", r.quantity()).addValue("serial", r.serialNo())
                .addValue("by", context.username()));
        }
        jdbc.update("DELETE FROM fab_bpo_pre_deliveries WHERE document_id = :doc", doc);
        if (!inserts.isEmpty()) {
            jdbc.batchUpdate("""
                INSERT INTO fab_bpo_pre_deliveries (organization_id, document_id, line_no, delivery_type_id, delivery_date,
                    source_color_line_id, quantity, serial_no, created_by, created_at)
                VALUES (:org, :doc, :no, :type, :date, :colour, :qty, :serial, :by, now())
                """, inserts.toArray(MapSqlParameterSource[]::new));
        }
    }

    /** The schedule, in its order, with each row's type and colour named. */
    public List<Map<String, Object>> rows(Long documentId) {
        return jdbc.query("""
            SELECT p.line_no, p.delivery_type_id, t.code, t.name, p.delivery_date, p.source_color_line_id,
                   c.color_name, c.color_code, g.group_no, p.quantity, p.serial_no
            FROM fab_bpo_pre_deliveries p
            JOIN fab_delivery_types t ON t.id = p.delivery_type_id
            JOIN gbl_business_document_color_lines c ON c.id = p.source_color_line_id
            JOIN gbl_business_document_line_groups g ON g.id = c.line_group_id
            WHERE p.document_id = :doc ORDER BY p.line_no
            """, new MapSqlParameterSource("doc", documentId), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", rs.getInt("line_no"));
            m.put("deliveryTypeId", rs.getLong("delivery_type_id"));
            m.put("deliveryTypeCode", rs.getString("code"));
            m.put("deliveryTypeName", rs.getString("name"));
            m.put("deliveryDate", rs.getObject("delivery_date", LocalDate.class));
            m.put("sourceId", rs.getLong("source_color_line_id"));
            m.put("colorName", rs.getString("color_name"));
            m.put("colorCode", rs.getString("color_code"));
            m.put("groupNo", rs.getInt("group_no"));
            m.put("quantity", rs.getBigDecimal("quantity"));
            m.put("serialNo", rs.getInt("serial_no"));
            return m;
        });
    }

    /** A revision starts with its predecessor's schedule. */
    public void copy(Long fromId, Long toId) {
        jdbc.update("""
            INSERT INTO fab_bpo_pre_deliveries (organization_id, document_id, line_no, delivery_type_id, delivery_date,
                source_color_line_id, quantity, serial_no, created_by, created_at)
            SELECT organization_id, :to, line_no, delivery_type_id, delivery_date, source_color_line_id, quantity, serial_no, :by, now()
            FROM fab_bpo_pre_deliveries WHERE document_id = :from
            """, new MapSqlParameterSource("from", fromId).addValue("to", toId).addValue("by", context.username()));
    }
}

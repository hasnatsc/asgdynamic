package com.asg.fabricerp.inventory.item;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small input-cleaning and row-building helpers shared by the item master services. */
public final class Masters {

    private Masters() { }

    /** Trimmed text, or null when blank - so an empty form field never becomes a stored "". */
    static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String required(String value, String field) {
        String cleaned = clean(value);
        if (cleaned == null) throw new IllegalArgumentException(field + " is required.");
        return cleaned;
    }

    static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required.");
        return value;
    }

    static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative.");
        }
        return value;
    }

    static BigDecimal percent(BigDecimal value, String field) {
        nonNegative(value, field);
        if (value != null && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(field + " cannot exceed 100%.");
        }
        return value;
    }

    /** The columns every approvable master's grid row carries. */
    static Map<String, Object> approvableRow(ApprovableMaster m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("version", m.getVersion());
        row.put("active", m.getActive());
        row.put("approved", m.getApproved());
        row.put("approvedBy", m.getApprovedBy());
        row.put("approvedAt", m.getApprovedAt());
        row.put("createdBy", m.getCreatedBy());
        row.put("updatedAt", m.getUpdatedAt());
        return row;
    }

    /** The columns every non-approvable master's grid row carries. */
    static Map<String, Object> baseRow(com.asg.fabricerp.common.BaseOrgEntity m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("version", m.getVersion());
        row.put("active", m.getActive());
        row.put("createdBy", m.getCreatedBy());
        row.put("updatedAt", m.getUpdatedAt());
        return row;
    }

    /** Lookup option: what a picker needs and nothing more. */
    public record Option(Long id, String code, String text) { }
}

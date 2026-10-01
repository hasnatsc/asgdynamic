package com.asg.fabricerp.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One searchable record as {@link SearchSql} projects it: what is matched (code, title, subtitle,
 * body), what is shown, and what decides who may see it (organization, unit, type, team, stores,
 * creator). The Elasticsearch document is this record, field for field.
 *
 * @param docType   the document type, voucher type, party type or item type, by enum name
 * @param updatedAt the later of the record's own change and its party's, so a renamed buyer is re-indexed
 */
public record SearchRecord(SearchKind kind, Long sourceId, Long organizationId, Long businessUnitId,
                           String docType, String code, String title, String subtitle, String status,
                           LocalDate date, BigDecimal amount, String currency,
                           Long marketingTeamId, Long warehouseId, Long toWarehouseId, String createdBy,
                           String body, boolean deleted, LocalDateTime updatedAt) {

    /** The Elasticsearch id: unique across kinds, stable across re-indexing. */
    public String key() {
        return kind.name() + ":" + sourceId;
    }
}

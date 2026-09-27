package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The legacy Period screen: a month's stock is closed once it has been counted and reported, and
 * nothing may then be posted into it - not a receipt, an issue, a transfer, an adjustment, nor
 * the reversal a cancellation writes - until it is reopened, with a reason.
 *
 * <p>A month with no row is open, so a new organization posts from its first day without
 * setting anything up. Closing is refused while store documents dated in the month are still
 * unposted drafts: a month closed around them could never take them.
 */
@Service
public class InventoryPeriodService {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public InventoryPeriodService(NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    /** Refuses a posting dated in a closed month. */
    public void requireOpen(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        if (isClosed(context.requireOrganizationId(), YearMonth.from(day))) {
            throw new IllegalStateException(("Stock for %s is closed; nothing can be posted into it. Date the document in an "
                + "open month, or have the month reopened under Inventory → Inventory periods.").formatted(label(YearMonth.from(day))));
        }
    }

    public boolean isClosed(Long orgId, YearMonth month) {
        return Boolean.TRUE.equals(jdbc.query("""
            SELECT status = 'CLOSED' FROM inv_periods WHERE organization_id = :org AND period_month = :month
            """, new MapSqlParameterSource("org", orgId).addValue("month", month.atDay(1)),
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE));
    }

    /** The twelve months of {@code year}, each with its state and how much moved in it. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> year(int year) {
        Long org = context.requireOrganizationId();
        Map<LocalDate, Map<String, Object>> rows = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth month = YearMonth.of(year, m);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", month.toString());
            row.put("label", label(month));
            row.put("status", "OPEN");
            row.put("moves", 0);
            row.put("drafts", 0);
            rows.put(month.atDay(1), row);
        }
        MapSqlParameterSource p = new MapSqlParameterSource("org", org)
            .addValue("from", LocalDate.of(year, 1, 1)).addValue("to", LocalDate.of(year, 12, 31));
        jdbc.query("""
            SELECT period_month, status, closed_by, closed_at, reopened_by, reopened_at, remarks
            FROM inv_periods WHERE organization_id = :org AND period_month BETWEEN :from AND :to
            """, p, rs -> {
            Map<String, Object> row = rows.get(rs.getDate(1).toLocalDate());
            if (row == null) return;
            row.put("status", rs.getString(2));
            row.put("closedBy", rs.getString(3));
            row.put("closedAt", rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime());
            row.put("reopenedBy", rs.getString(5));
            row.put("reopenedAt", rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toLocalDateTime());
            row.put("remarks", rs.getString(7));
        });
        jdbc.query("""
            SELECT date_trunc('month', move_date)::date, count(*) FROM inv_item_moves
            WHERE organization_id = :org AND move_date BETWEEN :from AND :to GROUP BY 1
            """, p, rs -> {
            Map<String, Object> row = rows.get(rs.getDate(1).toLocalDate());
            if (row != null) row.put("moves", rs.getLong(2));
        });
        jdbc.query("""
            SELECT date_trunc('month', document_date)::date, count(*) FROM gbl_business_documents
            WHERE organization_id = :org AND deleted = FALSE AND status = 'DRAFT'
              AND document_type IN (:types) AND document_date BETWEEN :from AND :to GROUP BY 1
            """, p.addValue("types", postingTypes()), rs -> {
            Map<String, Object> row = rows.get(rs.getDate(1).toLocalDate());
            if (row != null) row.put("drafts", rs.getLong(2));
        });
        return new ArrayList<>(rows.values());
    }

    @Transactional
    public void close(YearMonth month, String remarks) {
        Long org = context.requireOrganizationId();
        List<String> drafts = jdbc.queryForList("""
            SELECT document_no FROM gbl_business_documents
            WHERE organization_id = :org AND deleted = FALSE AND status = 'DRAFT' AND document_type IN (:types)
              AND document_date BETWEEN :from AND :to
            ORDER BY document_date, document_no LIMIT 6
            """, new MapSqlParameterSource("org", org).addValue("types", postingTypes())
                .addValue("from", month.atDay(1)).addValue("to", month.atEndOfMonth()), String.class);
        if (!drafts.isEmpty()) {
            throw new IllegalStateException("%s still has unposted store documents dated in it (%s%s). Post or delete them first."
                .formatted(label(month), String.join(", ", drafts.subList(0, Math.min(5, drafts.size()))), drafts.size() > 5 ? ", …" : ""));
        }
        int updated = jdbc.update("""
            UPDATE inv_periods SET status = 'CLOSED', closed_by = :user, closed_at = now(), remarks = :remarks,
                   version = COALESCE(version, 0) + 1
            WHERE organization_id = :org AND period_month = :month AND status = 'OPEN'
            """, params(org, month, remarks));
        if (updated == 0) {
            if (isClosed(org, month)) throw new IllegalStateException(label(month) + " is already closed");
            jdbc.update("""
                INSERT INTO inv_periods (organization_id, period_month, status, closed_by, closed_at, remarks, version)
                VALUES (:org, :month, 'CLOSED', :user, now(), :remarks, 0)
                """, params(org, month, remarks));
        }
    }

    @Transactional
    public void reopen(YearMonth month, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the month is reopened");
        int updated = jdbc.update("""
            UPDATE inv_periods SET status = 'OPEN', reopened_by = :user, reopened_at = now(), remarks = :remarks,
                   version = COALESCE(version, 0) + 1
            WHERE organization_id = :org AND period_month = :month AND status = 'CLOSED'
            """, params(context.requireOrganizationId(), month, "Reopened: " + reason.strip()));
        if (updated == 0) throw new IllegalStateException(label(month) + " is not closed");
    }

    private MapSqlParameterSource params(Long org, YearMonth month, String remarks) {
        return new MapSqlParameterSource("org", org).addValue("month", month.atDay(1)).addValue("user", context.username())
            .addValue("remarks", remarks == null || remarks.isBlank() ? null : remarks.strip());
    }

    /** The document types the store posts - the ones a closed month would strand as drafts. */
    static List<String> postingTypes() {
        return Arrays.stream(SupplyStep.values()).filter(SupplyStep::isPosting).map(s -> s.type().name()).toList();
    }

    static String label(YearMonth month) {
        return month.format(LABEL);
    }
}

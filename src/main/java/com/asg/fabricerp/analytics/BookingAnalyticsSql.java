package com.asg.fabricerp.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Booking analytics query, as SQL text and named parameters - nothing else. Kept free of
 * Spring and JPA so the exact statements the screen runs can be compiled and executed against a
 * database on their own, and so what each figure means is written down in one place.
 *
 * <h2>Which bookings count</h2>
 * <ul>
 *   <li>Live bookings of the operating business unit ({@code deleted = false}).</li>
 *   <li>The current version only: a booking that has been revised is represented by its latest
 *       revision, never by the original and the revision together - otherwise a revised order would
 *       be counted twice.</li>
 *   <li>Booked in the chosen period ({@code document_date}), except for the forward-looking views
 *       - the delivery schedule and the approval queue - which take every open booking in scope,
 *       whenever it was booked.</li>
 *   <li>Narrowed to the viewer's scope ({@link AnalyticsView}) and the filters.</li>
 * </ul>
 *
 * <h2>Status groups</h2>
 * Draft; Pending (submitted, awaiting approval); Confirmed (approved and everything after it:
 * partial, processing, completed, closed); Rejected; Cancelled. "Pipeline" is Draft + Pending.
 * Cancelled bookings are shown in the status mix but left out of every total.
 *
 * <h2>Money</h2>
 * Bookings carry their own currency and no rate to a common one, so values are never added across
 * currencies: with a currency chosen every figure is in that currency; with none, only counts and
 * quantities are summed and the value figures are left empty (the service does that).
 *
 * <p>Filter values are always bound parameters. The only text spliced into a statement is chosen
 * from fixed fragments here (a date grain, a whitelisted sort column), never from the request.
 */
public final class BookingAnalyticsSql {

    private BookingAnalyticsSql() { }

    public record Query(String sql, Map<String, Object> params) { }

    /** Trend bucket size; picked by the service from the length of the period. */
    public enum Grain {
        DAY("day", "1 day"), WEEK("week", "1 week"), MONTH("month", "1 month");

        final String field;
        final String step;

        Grain(String field, String step) { this.field = field; this.step = step; }

        public static Grain forPeriod(LocalDate from, LocalDate to) {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            return days <= 45 ? DAY : days <= 190 ? WEEK : MONTH;
        }
    }

    /** What a breakdown is grouped by. */
    public enum Dimension { TEAM, PERSON, BUYER, MONTH, FABRIC_TYPE, CONSTRUCTION }

    /** Status groups, in the order the status mix shows them. */
    public static final List<String> STATUS_GROUPS = List.of("CONFIRMED", "PENDING", "DRAFT", "REJECTED", "CANCELLED");

    /**
     * Everything that decides which bookings a query covers. Built by the service from the viewer's
     * resolved scope and the request's filters - by the time it gets here the filters are already
     * known to be within the scope.
     */
    public record Criteria(long organizationId, long businessUnitId,
                           LocalDate from, LocalDate to,
                           AnalyticsView view, String username, Long userId, List<Long> teamIds,
                           Long teamId, Long personId, Long buyerId,
                           String currency, String statusGroup, String bookingType, String search) {

        /** The same criteria for the period of equal length just before this one - for the deltas. */
        public Criteria previousPeriod() {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            return new Criteria(organizationId, businessUnitId, from.minusDays(days), from.minusDays(1),
                view, username, userId, teamIds, teamId, personId, buyerId, currency, statusGroup, bookingType, search);
        }

        public Criteria withoutCurrency() {
            return new Criteria(organizationId, businessUnitId, from, to, view, username, userId, teamIds,
                teamId, personId, buyerId, null, statusGroup, bookingType, search);
        }
    }

    // ------------------------------------------------------------------------------ the base set

    static final String STATUS_GROUP = """
        CASE d.status
                   WHEN 'DRAFT' THEN 'DRAFT'
                   WHEN 'SUBMITTED' THEN 'PENDING'
                   WHEN 'REJECTED' THEN 'REJECTED'
                   WHEN 'CANCELLED' THEN 'CANCELLED'
                   ELSE 'CONFIRMED' END""";

    /**
     * {@code WITH b AS (...)}: the bookings in scope, one row each, with their status group.
     *
     * @param dated whether the period applies (false for the forward-looking views)
     */
    static String base(Criteria c, boolean dated, Map<String, Object> p) {
        StringBuilder sql = new StringBuilder("""
            WITH b AS (
              SELECT d.id, d.document_no, d.reference_no, d.document_date, d.required_date, d.status,
                     %s AS grp,
                     d.currency_code, d.subtotal_amount AS amount, d.total_quantity AS qty,
                     d.marketing_team_id, d.marketing_person_id, d.party_id, d.created_by, d.created_at,
                     d.revision_no, d.booking_type
              FROM gbl_business_documents d
              WHERE d.organization_id = :org AND d.business_unit_id = :unit
                AND d.document_type = 'BOOKING' AND d.deleted = false
                AND NOT EXISTS (SELECT 1 FROM gbl_business_documents n
                                WHERE n.revision_of_id = COALESCE(d.revision_of_id, d.id)
                                  AND n.revision_no > d.revision_no AND n.deleted = false)
            """.formatted(STATUS_GROUP));
        p.put("org", c.organizationId());
        p.put("unit", c.businessUnitId());
        if (dated) {
            sql.append("    AND d.document_date BETWEEN :from AND :to\n");
            p.put("from", c.from());
            p.put("to", c.to());
        }
        switch (c.view()) {
            case MINE -> {
                sql.append("    AND (d.created_by = :username OR d.marketing_person_id = :userId)\n");
                p.put("username", c.username());
                p.put("userId", c.userId() == null ? -1L : c.userId());
            }
            case TEAM -> {
                sql.append("    AND d.marketing_team_id IN (:teamIds)\n");
                p.put("teamIds", c.teamIds() == null || c.teamIds().isEmpty() ? List.of(-1L) : c.teamIds());
            }
            case ALL -> { }
        }
        if (c.teamId() != null) {
            sql.append("    AND d.marketing_team_id = :teamId\n");
            p.put("teamId", c.teamId());
        }
        if (c.personId() != null) {
            sql.append("    AND d.marketing_person_id = :personId\n");
            p.put("personId", c.personId());
        }
        if (c.buyerId() != null) {
            sql.append("    AND d.party_id = :buyerId\n");
            p.put("buyerId", c.buyerId());
        }
        if (c.currency() != null) {
            sql.append("    AND d.currency_code = :currency\n");
            p.put("currency", c.currency());
        }
        if (c.bookingType() != null) {
            sql.append("    AND d.booking_type = :bookingType\n");
            p.put("bookingType", c.bookingType());
        }
        if (c.statusGroup() != null) {
            sql.append("    AND ").append(STATUS_GROUP.strip()).append(" = :statusGroup\n");
            p.put("statusGroup", c.statusGroup());
        }
        if (c.search() != null) {
            sql.append("""
                    AND (lower(d.document_no) LIKE :search OR lower(COALESCE(d.reference_no, '')) LIKE :search
                         OR EXISTS (SELECT 1 FROM pty_parties sp WHERE sp.id = d.party_id AND lower(sp.name) LIKE :search))
                """);
            p.put("search", "%" + c.search().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.append(")\n");
        return sql.toString();
    }

    /** The colour lines under {@code b} - quantity, price, what has been drawn, and the costing's break-even. */
    static final String LINES = """
        , lines AS (
          SELECT b.id, b.grp, g.fabric_type, g.construction, g.break_even_price,
                 cl.quantity, cl.rate, LEAST(cl.fulfilled_quantity, cl.quantity) AS fulfilled
          FROM b
          JOIN gbl_business_document_line_groups g ON g.document_id = b.id
          JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
        )
        """;

    // ------------------------------------------------------------------------------ headline figures

    /**
     * One row of headline figures. Margin is against the costing's break-even, over confirmed lines
     * that have one; {@code margin_covered_qty} says how much of the confirmed quantity that is.
     */
    public static Query kpis(Criteria c) {
        Map<String, Object> p = new LinkedHashMap<>();
        String sql = base(c, true, p) + LINES + """
            SELECT
              count(*) FILTER (WHERE grp <> 'CANCELLED')                       AS bookings,
              count(*) FILTER (WHERE grp = 'CONFIRMED')                        AS confirmed,
              count(*) FILTER (WHERE grp IN ('DRAFT', 'PENDING'))              AS pipeline,
              count(*) FILTER (WHERE grp = 'PENDING')                          AS pending,
              count(*) FILTER (WHERE grp = 'REJECTED')                         AS rejected,
              count(*) FILTER (WHERE grp = 'CANCELLED')                        AS cancelled,
              count(*) FILTER (WHERE revision_no > 0 AND grp <> 'CANCELLED')   AS revised,
              count(DISTINCT party_id) FILTER (WHERE grp <> 'CANCELLED')       AS buyers,
              COALESCE(sum(qty) FILTER (WHERE grp <> 'CANCELLED'), 0)          AS quantity,
              COALESCE(sum(qty) FILTER (WHERE grp = 'CONFIRMED'), 0)           AS confirmed_qty,
              COALESCE(sum(amount) FILTER (WHERE grp = 'CONFIRMED'), 0)        AS confirmed_value,
              COALESCE(sum(amount) FILTER (WHERE grp IN ('DRAFT', 'PENDING')), 0) AS pipeline_value,
              avg(required_date - document_date) FILTER (WHERE grp <> 'CANCELLED' AND required_date IS NOT NULL) AS avg_lead_days,
              (SELECT sum(quantity * rate) FROM lines WHERE grp = 'CONFIRMED' AND break_even_price > 0)             AS margin_sales,
              (SELECT sum(quantity * break_even_price) FROM lines WHERE grp = 'CONFIRMED' AND break_even_price > 0) AS margin_cost,
              (SELECT sum(quantity) FROM lines WHERE grp = 'CONFIRMED' AND break_even_price > 0)                    AS margin_covered_qty,
              (SELECT sum(quantity) FROM lines WHERE grp = 'CONFIRMED')                                             AS confirmed_line_qty,
              (SELECT sum(fulfilled) FROM lines WHERE grp = 'CONFIRMED')                                            AS fulfilled_qty,
              (SELECT avg(EXTRACT(EPOCH FROM (r.settled_at - r.created_at))) / 3600
                 FROM apr_requests r JOIN b rb ON rb.id = r.document_id
                 WHERE r.outcome = 'APPROVED' AND r.settled_at IS NOT NULL)                                         AS avg_approval_hours,
              (SELECT count(*) FROM apr_requests r JOIN b rb ON rb.id = r.document_id WHERE r.outcome = 'APPROVED') AS approvals_approved,
              (SELECT count(*) FROM apr_requests r JOIN b rb ON rb.id = r.document_id
                 WHERE r.outcome IN ('APPROVED', 'REJECTED', 'RETURNED'))                                          AS approvals_decided
            FROM b
            """;
        return new Query(sql, p);
    }

    /** Which currencies the bookings in scope are in, most used first - the currency filter's options. */
    public static Query currencies(Criteria c) {
        Map<String, Object> p = new LinkedHashMap<>();
        String sql = base(c.withoutCurrency(), true, p) + """
            SELECT currency_code AS currency, count(*) AS bookings
            FROM b WHERE grp <> 'CANCELLED'
            GROUP BY currency_code ORDER BY count(*) DESC, currency_code
            """;
        return new Query(sql, p);
    }

    // ------------------------------------------------------------------------------ over time

    /** Confirmed and pipeline per bucket, every bucket of the period present (zero when empty). */
    public static Query trend(Criteria c, Grain grain) {
        Map<String, Object> p = new LinkedHashMap<>();
        String sql = base(c, true, p) + """
            , axis AS (
              SELECT generate_series(date_trunc('%1$s', CAST(:from AS timestamp)),
                                     CAST(:to AS timestamp), interval '%2$s') AS bucket
            )
            SELECT to_char(a.bucket, 'YYYY-MM-DD') AS bucket,
                   count(b.id) FILTER (WHERE b.grp = 'CONFIRMED')                    AS confirmed,
                   count(b.id) FILTER (WHERE b.grp IN ('DRAFT', 'PENDING'))          AS pipeline,
                   COALESCE(sum(b.qty) FILTER (WHERE b.grp = 'CONFIRMED'), 0)        AS confirmed_qty,
                   COALESCE(sum(b.qty) FILTER (WHERE b.grp IN ('DRAFT', 'PENDING')), 0) AS pipeline_qty,
                   COALESCE(sum(b.amount) FILTER (WHERE b.grp = 'CONFIRMED'), 0)     AS confirmed_value,
                   COALESCE(sum(b.amount) FILTER (WHERE b.grp IN ('DRAFT', 'PENDING')), 0) AS pipeline_value
            FROM axis a
            LEFT JOIN b ON date_trunc('%1$s', CAST(b.document_date AS timestamp)) = a.bucket
            GROUP BY a.bucket ORDER BY a.bucket
            """.formatted(grain.field, grain.step);
        return new Query(sql, p);
    }

    /** Count, quantity and value per status - the status mix. */
    public static Query statusMix(Criteria c) {
        Map<String, Object> p = new LinkedHashMap<>();
        String sql = base(c, true, p) + """
            SELECT grp AS status_group, count(*) AS bookings,
                   COALESCE(sum(qty), 0) AS quantity, COALESCE(sum(amount), 0) AS value
            FROM b GROUP BY grp
            """;
        return new Query(sql, p);
    }

    // ------------------------------------------------------------------------------ breakdowns

    /**
     * One row per team, person, buyer, month, fabric type or construction: bookings, quantity,
     * value, and - where the lines carry a costing break-even - price and margin. Largest first
     * (months in calendar order). The service folds the tail into "Other" for charts.
     */
    public static Query breakdown(Criteria c, Dimension dimension) {
        Map<String, Object> p = new LinkedHashMap<>();
        String header = switch (dimension) {
            case TEAM -> """
                SELECT b.marketing_team_id AS key, COALESCE(t.name, 'No team') AS label,
                """;
            case PERSON -> """
                SELECT b.marketing_person_id AS key,
                       COALESCE(NULLIF(u.full_name, ''), u.username, 'Unassigned') AS label,
                """;
            case BUYER -> """
                SELECT b.party_id AS key, COALESCE(pt.name, 'No buyer') AS label,
                """;
            case MONTH -> """
                SELECT to_char(date_trunc('month', CAST(b.document_date AS timestamp)), 'YYYY-MM') AS key,
                       to_char(date_trunc('month', CAST(b.document_date AS timestamp)), 'YYYY-MM') AS label,
                """;
            case FABRIC_TYPE, CONSTRUCTION -> null;
        };
        if (header != null) {
            String join = switch (dimension) {
                case TEAM -> "LEFT JOIN org_marketing_teams t ON t.id = b.marketing_team_id";
                case PERSON -> "LEFT JOIN sec_fabric_users u ON u.id = b.marketing_person_id";
                case BUYER -> "LEFT JOIN pty_parties pt ON pt.id = b.party_id";
                default -> "";
            };
            String order = dimension == Dimension.MONTH ? "key" : "confirmed_value DESC, quantity DESC, label";
            String sql = base(c, true, p) + LINES + header + """
                       count(*) FILTER (WHERE b.grp <> 'CANCELLED')                 AS bookings,
                       count(*) FILTER (WHERE b.grp = 'CONFIRMED')                  AS confirmed,
                       count(*) FILTER (WHERE b.grp = 'PENDING')                    AS pending,
                       count(*) FILTER (WHERE b.grp = 'REJECTED')                   AS rejected,
                       COALESCE(sum(b.qty) FILTER (WHERE b.grp <> 'CANCELLED'), 0)  AS quantity,
                       COALESCE(sum(b.qty) FILTER (WHERE b.grp = 'CONFIRMED'), 0)   AS confirmed_qty,
                       COALESCE(sum(b.amount) FILTER (WHERE b.grp = 'CONFIRMED'), 0) AS confirmed_value,
                       COALESCE(sum(b.amount) FILTER (WHERE b.grp IN ('DRAFT', 'PENDING')), 0) AS pipeline_value,
                       sum(m.margin_sales) AS margin_sales, sum(m.margin_cost) AS margin_cost,
                       sum(m.fulfilled) AS fulfilled_qty, sum(m.line_qty) AS confirmed_line_qty
                FROM b
                LEFT JOIN (SELECT id,
                                  sum(quantity * rate) FILTER (WHERE break_even_price > 0)             AS margin_sales,
                                  sum(quantity * break_even_price) FILTER (WHERE break_even_price > 0) AS margin_cost,
                                  sum(fulfilled) AS fulfilled, sum(quantity) AS line_qty
                           FROM lines WHERE grp = 'CONFIRMED' GROUP BY id) m ON m.id = b.id
                %s
                GROUP BY 1, 2
                ORDER BY %s
                """.formatted(join, order);
            return new Query(sql, p);
        }
        // Fabric type and construction live on the fabric lines, so these are summed line by line.
        String column = dimension == Dimension.FABRIC_TYPE ? "fabric_type" : "construction";
        String sql = base(c, true, p) + LINES + """
            SELECT COALESCE(NULLIF(trim(%1$s), ''), 'Unspecified') AS key,
                   COALESCE(NULLIF(trim(%1$s), ''), 'Unspecified') AS label,
                   count(DISTINCT id) FILTER (WHERE grp <> 'CANCELLED')                     AS bookings,
                   count(DISTINCT id) FILTER (WHERE grp = 'CONFIRMED')                      AS confirmed,
                   count(DISTINCT id) FILTER (WHERE grp = 'PENDING')                        AS pending,
                   count(DISTINCT id) FILTER (WHERE grp = 'REJECTED')                       AS rejected,
                   COALESCE(sum(quantity) FILTER (WHERE grp <> 'CANCELLED'), 0)             AS quantity,
                   COALESCE(sum(quantity) FILTER (WHERE grp = 'CONFIRMED'), 0)              AS confirmed_qty,
                   COALESCE(sum(quantity * rate) FILTER (WHERE grp = 'CONFIRMED'), 0)       AS confirmed_value,
                   COALESCE(sum(quantity * rate) FILTER (WHERE grp IN ('DRAFT', 'PENDING')), 0) AS pipeline_value,
                   sum(quantity * rate) FILTER (WHERE grp = 'CONFIRMED' AND break_even_price > 0)             AS margin_sales,
                   sum(quantity * break_even_price) FILTER (WHERE grp = 'CONFIRMED' AND break_even_price > 0) AS margin_cost,
                   sum(fulfilled) FILTER (WHERE grp = 'CONFIRMED')                          AS fulfilled_qty,
                   sum(quantity) FILTER (WHERE grp = 'CONFIRMED')                           AS confirmed_line_qty
            FROM lines
            GROUP BY 1, 2
            ORDER BY confirmed_qty DESC, quantity DESC, label
            """.formatted(column);
        return new Query(sql, p);
    }

    // ------------------------------------------------------------------------------ forward-looking

    /**
     * Confirmed bookings with quantity still to deliver, by the week they are due - every open booking
     * in scope, whenever booked. Buckets: OVERDUE, each week start (Monday) up to twelve weeks out,
     * LATER, and UNSCHEDULED (no delivery date).
     */
    public static Query deliverySchedule(Criteria c, LocalDate today) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("today", today);
        p.put("horizon", today.plusWeeks(12));
        String sql = base(c, false, p) + LINES + """
            , open AS (
              SELECT b.id, b.required_date,
                     sum(l.quantity - l.fulfilled)            AS open_qty,
                     sum((l.quantity - l.fulfilled) * l.rate) AS open_value
              FROM b JOIN lines l ON l.id = b.id
              WHERE b.grp = 'CONFIRMED'
              GROUP BY b.id, b.required_date
              HAVING sum(l.quantity - l.fulfilled) > 0
            )
            SELECT CASE WHEN required_date IS NULL THEN 'UNSCHEDULED'
                        WHEN required_date < :today THEN 'OVERDUE'
                        WHEN required_date >= :horizon THEN 'LATER'
                        ELSE to_char(date_trunc('week', CAST(required_date AS timestamp)), 'YYYY-MM-DD') END AS bucket,
                   count(*) AS bookings, sum(open_qty) AS open_qty, sum(open_value) AS open_value
            FROM open GROUP BY 1
            """;
        return new Query(sql, p);
    }

    /** The approval queue by how long each booking has waited - every pending booking in scope. */
    public static Query approvalAging(Criteria c, LocalDateTime now) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("now", now);
        String sql = base(c, false, p) + """
            SELECT CASE WHEN age < 1 THEN '0-1' WHEN age < 3 THEN '1-3' WHEN age < 7 THEN '3-7'
                        WHEN age < 14 THEN '7-14' ELSE '14+' END AS bucket,
                   count(*) AS bookings, COALESCE(sum(amount), 0) AS value, COALESCE(sum(qty), 0) AS quantity
            FROM (SELECT b.amount, b.qty,
                         EXTRACT(EPOCH FROM (CAST(:now AS timestamp) - r.created_at)) / 86400 AS age
                  FROM b JOIN apr_requests r ON r.document_id = b.id AND r.pending) x
            GROUP BY 1
            """;
        return new Query(sql, p);
    }

    // ------------------------------------------------------------------------------ row-level reports

    /** Columns the register may be sorted by: request key -> SQL expression. */
    public static final Map<String, String> REGISTER_SORT = Map.of(
        "documentNo", "b.document_no",
        "documentDate", "b.document_date",
        "requiredDate", "b.required_date",
        "buyer", "buyer",
        "team", "team",
        "person", "person",
        "status", "b.status",
        "quantity", "b.qty",
        "value", "b.amount");

    /** One row per booking, with its buyer, team, person, delivered quantity and live approval. */
    public static Query register(Criteria c, String sortKey, boolean ascending, int limit, int offset) {
        Map<String, Object> p = new LinkedHashMap<>();
        String sort = REGISTER_SORT.getOrDefault(sortKey, "b.document_date");
        p.put("limit", limit);
        p.put("offset", offset);
        String sql = base(c, true, p) + """
            SELECT b.id, b.document_no, b.reference_no, b.document_date, b.required_date, b.status, b.grp AS status_group,
                   b.revision_no, b.currency_code, b.qty AS quantity, b.amount AS value,
                   COALESCE(pt.name, '') AS buyer, COALESCE(t.name, '') AS team,
                   COALESCE(NULLIF(u.full_name, ''), u.username, '') AS person,
                   (SELECT COALESCE(sum(LEAST(cl.fulfilled_quantity, cl.quantity)), 0)
                      FROM gbl_business_document_line_groups g
                      JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
                      WHERE g.document_id = b.id) AS fulfilled,
                   r.current_level, r.total_levels, r.created_at AS submitted_at
            FROM b
            LEFT JOIN pty_parties pt ON pt.id = b.party_id
            LEFT JOIN org_marketing_teams t ON t.id = b.marketing_team_id
            LEFT JOIN sec_fabric_users u ON u.id = b.marketing_person_id
            LEFT JOIN apr_requests r ON r.document_id = b.id AND r.pending
            ORDER BY %s %s NULLS LAST, b.id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(sort, ascending ? "ASC" : "DESC");
        return new Query(sql, p);
    }

    public static Query registerCount(Criteria c) {
        Map<String, Object> p = new LinkedHashMap<>();
        return new Query(base(c, true, p) + "SELECT count(*) AS total FROM b\n", p);
    }

    /** Every pending booking in scope, longest waiting first - the approval queue report. */
    public static Query pendingApprovals(Criteria c, LocalDateTime now) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("now", now);
        String sql = base(c, false, p) + """
            SELECT b.id, b.document_no, b.document_date, b.currency_code, b.qty AS quantity, b.amount AS value,
                   COALESCE(pt.name, '') AS buyer, COALESCE(t.name, '') AS team,
                   COALESCE(NULLIF(u.full_name, ''), u.username, '') AS person,
                   r.created_at AS submitted_at, r.current_level, r.total_levels,
                   round(CAST(EXTRACT(EPOCH FROM (CAST(:now AS timestamp) - r.created_at)) / 86400 AS numeric), 1) AS days_waiting
            FROM b
            JOIN apr_requests r ON r.document_id = b.id AND r.pending
            LEFT JOIN pty_parties pt ON pt.id = b.party_id
            LEFT JOIN org_marketing_teams t ON t.id = b.marketing_team_id
            LEFT JOIN sec_fabric_users u ON u.id = b.marketing_person_id
            ORDER BY r.created_at, b.id
            LIMIT 5000
            """;
        return new Query(sql, p);
    }

    /** Confirmed bookings still to deliver, soonest due first - the delivery schedule report. */
    public static Query openDeliveries(Criteria c, LocalDate today) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("today", today);
        String sql = base(c, false, p) + LINES + """
            SELECT b.id, b.document_no, b.document_date, b.required_date, b.currency_code,
                   COALESCE(pt.name, '') AS buyer, COALESCE(t.name, '') AS team,
                   COALESCE(NULLIF(u.full_name, ''), u.username, '') AS person,
                   sum(l.quantity) AS quantity, sum(l.fulfilled) AS fulfilled,
                   sum(l.quantity - l.fulfilled) AS open_qty, sum((l.quantity - l.fulfilled) * l.rate) AS open_value,
                   b.required_date - CAST(:today AS date) AS days_to_due
            FROM b
            JOIN lines l ON l.id = b.id
            LEFT JOIN pty_parties pt ON pt.id = b.party_id
            LEFT JOIN org_marketing_teams t ON t.id = b.marketing_team_id
            LEFT JOIN sec_fabric_users u ON u.id = b.marketing_person_id
            WHERE b.grp = 'CONFIRMED'
            GROUP BY b.id, b.document_no, b.document_date, b.required_date, b.currency_code, pt.name, t.name, u.full_name, u.username
            HAVING sum(l.quantity - l.fulfilled) > 0
            ORDER BY b.required_date NULLS LAST, b.id
            LIMIT 5000
            """;
        return new Query(sql, p);
    }

    /** Marketing persons with bookings in scope, for the person filter. */
    public static Query persons(Criteria c, String q, int limit, int offset) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("limit", limit);
        p.put("offset", offset);
        StringBuilder sql = new StringBuilder(base(c, false, p)).append("""
            SELECT u.id, u.username, COALESCE(NULLIF(u.full_name, ''), u.username) AS name
            FROM sec_fabric_users u
            WHERE u.id IN (SELECT marketing_person_id FROM b)
            """);
        if (q != null && !q.isBlank()) {
            sql.append("  AND (lower(u.username) LIKE :q OR lower(COALESCE(u.full_name, '')) LIKE :q)\n");
            p.put("q", "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.append("ORDER BY name LIMIT :limit OFFSET :offset\n");
        return new Query(sql.toString(), p);
    }
}

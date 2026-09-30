package com.asg.fabricerp.production;

import com.asg.fabricerp.common.LookupPage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * The Fabrics production report: every production order, one row each, and how far its fabric has
 * gone - covered by LC, woven, put on dyeing, greige received and issued, finished, delivered
 * against its delivery orders - with its due date and whether it will make it.
 *
 * <p>Built on the Production board's own line figures ({@link ProductionBoardService#BOARD}), summed
 * per order, so the report and the board can never disagree. LC quantity is traced back the way the
 * commercial documents are raised: Export LC line → Export PI line → delivery schedule line →
 * production order line; an LC superseded by an approved amendment is not counted twice.
 *
 * <p>Each stage is measured against what it is due to reach: weaving, greige received and greige
 * issued against the greige the order needs (quantity plus the route's allowance); dyeing and
 * finished against the part of the order that is processed (a greige-delivered order has neither);
 * LC against the order quantity; delivery against the quantity on delivery orders.
 */
@Service
@Transactional(readOnly = true)
public class ProductionReportService {

    /** Due within this many days and not complete: "Due in n days". */
    public static final int DUE_SOON_DAYS = 7;
    /** At most this many rows go into one printed report. */
    public static final int PRINT_LIMIT = 5000;

    /** {@code bpoId} narrows the report to one order (its row's "Print this order"). */
    public record Filter(String q, Long marketingPersonId, Long garmentsId, LocalDate from, LocalDate to, Long bpoId) { }

    public record Page(List<Map<String, Object>> rows, long total, Map<String, Object> totals) { }

    /** Sortable columns: the request's key → the SQL it orders by. */
    static final Map<String, String> SORTS = Map.of(
        "bpoNo", "r.bpo_no",
        "bpoDate", "r.bpo_date",
        "marketingPerson", "lower(r.marketing_person)",
        "garments", "lower(r.garments)",
        "dispoNo", "lower(r.dispo_no)",
        "dueDate", "r.due_date",
        "deliveryPct", "r.delivery_pct");

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductionBoardService boards;

    public ProductionReportService(NamedParameterJdbcTemplate jdbc, ProductionBoardService boards) {
        this.jdbc = jdbc;
        this.boards = boards;
    }

    static final String REPORT = """
        WITH board AS (""" + ProductionBoardService.BOARD + """
        ), lc AS (
            SELECT rl.source_color_line_id AS bpo_line_id, SUM(ll.quantity - COALESCE(ll.short_closed_quantity, 0)) AS lc_quantity
            FROM gbl_business_documents lcd
            JOIN gbl_business_document_line_groups lg ON lg.document_id = lcd.id
            JOIN gbl_business_document_color_lines ll ON ll.line_group_id = lg.id
            JOIN gbl_business_document_color_lines pl ON pl.id = ll.source_color_line_id
            JOIN gbl_business_document_color_lines rl ON rl.id = pl.source_color_line_id
            WHERE lcd.organization_id = :org AND lcd.document_type = 'EXPORT_LETTER_OF_CREDIT' AND lcd.deleted = false
              AND lcd.status IN ('APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED')
              AND NOT EXISTS (SELECT 1 FROM gbl_business_documents n
                              WHERE n.revision_of_id = COALESCE(lcd.revision_of_id, lcd.id) AND n.revision_no > lcd.revision_no
                                AND n.deleted = false AND n.status IN ('APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED'))
            GROUP BY rl.source_color_line_id
        ), agg AS (
            SELECT b.bpo_id,
                   SUM(b.quantity) AS quantity,
                   SUM(b.greige_required) AS greige_required,
                   SUM(b.quantity) FILTER (WHERE b.needs_processing) AS process_quantity,
                   SUM(b.greige_required) FILTER (WHERE b.needs_processing) AS process_greige,
                   COALESCE(SUM(lc.lc_quantity), 0) AS lc_quantity,
                   SUM(b.weaving) AS weaving,
                   SUM(b.dyeing) FILTER (WHERE b.needs_processing) AS processing,
                   SUM(b.greige_received) AS greige_received,
                   SUM(b.greige_issued) FILTER (WHERE b.needs_processing) AS greige_issued,
                   SUM(b.finished_a + b.finished_b) FILTER (WHERE b.needs_processing) AS finished,
                   SUM(b.on_delivery_orders) AS do_quantity,
                   SUM(b.delivered) AS delivered,
                   SUM(b.balance) AS pending,
                   COUNT(*) AS colour_lines,
                   CASE WHEN COUNT(DISTINCT b.uom) = 1 THEN MIN(b.uom) END AS uom
            FROM board b
            LEFT JOIN lc ON lc.bpo_line_id = b.line_id
            GROUP BY b.bpo_id
        ), r AS (
            SELECT a.*, d.document_no AS bpo_no, d.document_date AS bpo_date, d.required_date AS due_date,
                   d.status, d.marketing_person_id, d.garments_id,
                   COALESCE(NULLIF(mp.full_name, ''), mp.username) AS marketing_person,
                   gp.name AS garments, bp.name AS buyer,
                   (SELECT string_agg(DISTINCT g.dispo_reference, ', ') FROM gbl_business_document_line_groups g
                     WHERE g.document_id = d.id AND COALESCE(g.dispo_reference, '') <> '') AS dispo_no,
                   (d.status IN ('COMPLETED', 'CLOSED') OR (a.quantity > 0 AND a.pending <= 0)) AS completed,
                   (d.required_date - CAST(:today AS DATE)) AS days_due,
                   CASE WHEN COALESCE(a.do_quantity, 0) > 0 THEN a.delivered / a.do_quantity ELSE 0 END AS delivery_pct
            FROM agg a
            JOIN gbl_business_documents d ON d.id = a.bpo_id
            LEFT JOIN sec_fabric_users mp ON mp.id = d.marketing_person_id
            LEFT JOIN pty_parties gp ON gp.id = d.garments_id
            LEFT JOIN pty_parties bp ON bp.id = d.party_id
        )
        SELECT r.*,
               CASE WHEN r.completed THEN 'COMPLETED'
                    WHEN r.due_date IS NULL THEN 'NO_DUE_DATE'
                    WHEN r.days_due < 0 THEN 'OVERDUE'
                    WHEN r.days_due <= :dueSoon THEN 'DUE_SOON'
                    ELSE 'ON_TIME' END AS due_state
        FROM r
        WHERE (CAST(:personId AS BIGINT) IS NULL OR r.marketing_person_id = :personId)
          AND (CAST(:garmentsId AS BIGINT) IS NULL OR r.garments_id = :garmentsId)
          AND (:rq = '%' OR lower(r.bpo_no) LIKE :rq OR lower(COALESCE(r.marketing_person, '')) LIKE :rq
               OR lower(COALESCE(r.garments, '')) LIKE :rq OR lower(COALESCE(r.dispo_no, '')) LIKE :rq
               OR lower(COALESCE(r.buyer, '')) LIKE :rq)
        """;

    /** One page of orders, sorted, with the figures for the whole filtered set. */
    public Page report(Filter filter, String sort, String dir, int page, int size) {
        MapSqlParameterSource p = params(filter)
            .addValue("limit", size).addValue("offset", Math.max(0, page) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM (" + REPORT + ") r ORDER BY " + orderBy(sort, dir)
            + " LIMIT :limit OFFSET :offset", p);
        Map<String, Object> totals = totals(p);
        return new Page(rows.stream().map(ProductionReportService::row).toList(), ((Number) totals.get("orders")).longValue(), totals);
    }

    /** Every order the filter finds, for the printed report - capped at {@link #PRINT_LIMIT}. */
    public Page all(Filter filter, String sort, String dir) {
        return report(filter, sort, dir, 0, PRINT_LIMIT);
    }

    /** The colour lines behind one order's row: the Production board's own figures, line by line. */
    public List<Map<String, Object>> lines(Long bpoId) {
        MapSqlParameterSource p = boardParams(bpoId, null, null);
        return jdbc.queryForList("SELECT * FROM (" + ProductionBoardService.BOARD + ") b ORDER BY b.group_no, b.line_id", p)
            .stream().map(ProductionBoardService::camel).toList();
    }

    /** The marketing people who have production orders in the caller's scope - the filter's choices. */
    public LookupPage<LookupPage.Option> marketingPersons(String q, Long id) {
        return options("""
            SELECT DISTINCT u.id, COALESCE(NULLIF(u.full_name, ''), u.username) AS text FROM gbl_business_documents d
            JOIN sec_fabric_users u ON u.id = d.marketing_person_id
            """, q, id);
    }

    /** The garment factories production orders are made for. */
    public LookupPage<LookupPage.Option> garments(String q, Long id) {
        return options("""
            SELECT DISTINCT g.id, g.name AS text FROM gbl_business_documents d
            JOIN pty_parties g ON g.id = d.garments_id
            """, q, id);
    }

    // --------------------------------------------------------------------------------- helpers

    private LookupPage<LookupPage.Option> options(String select, String q, Long id) {
        MapSqlParameterSource p = boards.scopeParams().addValue("q", ProductionBoardService.like(q)).addValue("id", id);
        List<LookupPage.Option> found = jdbc.query("SELECT * FROM (" + select + """
                WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
                  AND d.document_type = 'BULK_PRODUCTION_ORDER'
                  AND (:allTeams OR d.marketing_team_id IN (:teams))
            ) o WHERE (CAST(:id AS BIGINT) IS NULL OR o.id = :id) AND lower(COALESCE(o.text, '')) LIKE :q
            ORDER BY lower(o.text) LIMIT 51
            """, p, (rs, i) -> new LookupPage.Option(rs.getLong("id"), null, rs.getString("text"), null));
        return LookupPage.of(found.stream().limit(50).toList(), found.size() > 50);
    }

    private Map<String, Object> totals(MapSqlParameterSource p) {
        Map<String, Object> t = jdbc.queryForMap("""
            SELECT count(*) AS orders,
                   COUNT(*) FILTER (WHERE date_trunc('month', bpo_date) = date_trunc('month', CAST(:today AS DATE))) AS this_month,
                   COUNT(*) FILTER (WHERE date_trunc('month', bpo_date) = date_trunc('month', CAST(:today AS DATE)) - INTERVAL '1 month') AS last_month,
                   COUNT(*) FILTER (WHERE due_state = 'OVERDUE') AS overdue,
                   COUNT(*) FILTER (WHERE due_state = 'DUE_SOON') AS due_soon,
                   COALESCE(SUM(quantity), 0) AS quantity, COALESCE(SUM(lc_quantity), 0) AS lc_quantity,
                   COALESCE(SUM(greige_received), 0) AS in_production, COALESCE(SUM(finished), 0) AS finished,
                   COALESCE(SUM(delivered), 0) AS delivered, COALESCE(SUM(pending), 0) AS pending,
                   CASE WHEN COUNT(DISTINCT uom) FILTER (WHERE uom IS NOT NULL) = 1 AND COUNT(*) FILTER (WHERE uom IS NULL) = 0
                        THEN MIN(uom) END AS uom
            FROM (""" + REPORT + ") r", p);
        return ProductionBoardService.camel(t);
    }

    private MapSqlParameterSource params(Filter f) {
        return boardParams(f.bpoId(), f.from(), f.to())
            .addValue("personId", f.marketingPersonId()).addValue("garmentsId", f.garmentsId())
            .addValue("rq", ProductionBoardService.like(f.q())).addValue("dueSoon", DUE_SOON_DAYS);
    }

    /** The board's parameters: every committed order (completed ones too), none of its own filters. */
    private MapSqlParameterSource boardParams(Long bpoId, LocalDate from, LocalDate to) {
        return boards.scopeParams()
            .addValue("statuses", List.of("APPROVED", "PROCESSING", "PARTIAL", "COMPLETED", "CLOSED"))
            .addValue("teamId", null).addValue("buyerId", null).addValue("fabricType", null).addValue("dueBy", null)
            .addValue("bpoId", bpoId).addValue("color", null).addValue("fromDate", from).addValue("toDate", to)
            .addValue("allBookings", true).addValue("bookingIds", List.of(-1L)).addValue("q", "%")
            .addValue("lateBy", LocalDate.now().plusDays(ProductionBoardService.LATE_WITHIN_DAYS))
            .addValue("today", LocalDate.now());
    }

    static String orderBy(String sort, String dir) {
        String column = SORTS.getOrDefault(sort == null ? "" : sort, "r.bpo_date");
        String direction = "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
        return column + " " + direction + " NULLS LAST, r.bpo_no DESC";
    }

    /** A row with each stage's done/of pair and its percentage, as the screen and the printout show it. */
    static Map<String, Object> row(Map<String, Object> sql) {
        Map<String, Object> r = ProductionBoardService.camel(sql);
        // Dates as dates, not instants: a java.sql.Date serialises as midnight UTC and shows a day early east of it.
        r.replaceAll((k, v) -> v instanceof java.sql.Date d ? d.toLocalDate() : v);
        Integer days = r.get("daysDue") == null ? null : ((Number) r.get("daysDue")).intValue();
        r.put("dueText", dueText((String) r.get("dueState"), days));
        stage(r, "lc", "lcQuantity", "quantity");
        stage(r, "weaving", "weaving", "greigeRequired");
        stage(r, "processing", "processing", "processQuantity");
        stage(r, "greigeReceived", "greigeReceived", "greigeRequired");
        stage(r, "greigeIssued", "greigeIssued", "processGreige");
        stage(r, "finished", "finished", "processQuantity");
        stage(r, "delivery", "delivered", "doQuantity");
        return r;
    }

    /** What the due badge says: "Completed", "2 days overdue", "Due today", "Due in 3 days", "On time". */
    static String dueText(String state, Integer days) {
        if (state == null) return null;
        return switch (state) {
            case "COMPLETED" -> "Completed";
            case "OVERDUE" -> (-days) + (days == -1 ? " day overdue" : " days overdue");
            case "DUE_SOON" -> days == 0 ? "Due today" : "Due in " + days + (days == 1 ? " day" : " days");
            case "ON_TIME" -> "On time";
            default -> "No due date";
        };
    }

    /** Adds {@code <name>Done}, {@code <name>Of} and {@code <name>Pct} (null when the stage does not apply). */
    private static void stage(Map<String, Object> r, String name, String done, String of) {
        BigDecimal o = decimal(r.get(of));
        BigDecimal d = o == null ? null : Objects.requireNonNullElse(decimal(r.get(done)), BigDecimal.ZERO);
        r.put(name + "Done", d);
        r.put(name + "Of", o);
        r.put(name + "Pct", o == null || o.signum() <= 0 ? null
            : d.multiply(BigDecimal.valueOf(100)).divide(o, 0, java.math.RoundingMode.HALF_UP).intValue());
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return null;
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }
}

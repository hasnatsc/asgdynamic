package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The production dashboard: the whole chain from Booking to the buyer's gate on one page, for
 * management. Nothing here is typed or stored - every figure is read from the documents, the draw
 * streams and the fabric stock ledger, through the same two queries the Production board and Ready
 * to deliver run ({@link ProductionBoardService#BOARD}, {@link ProductionBoardService#READY}), so a
 * number on the dashboard is the number on the board.
 *
 * <p>The filters pick a set of production order lines (order date, fabric type, colour, buyer,
 * order, status); everything else - work orders, stock, movements, deliveries - is what belongs
 * to those orders. Team scope applies as on every other production screen.
 */
@Service
@Transactional(readOnly = true)
public class ProductionDashboardService {

    /** More order lines than this and the page says it is showing the first ones. */
    static final int MAX_LINES = 5000;
    /** A document waiting for a signature this long is an alert. */
    static final int STUCK_DAYS = 3;

    private static final List<String> ACTIVE = List.of("DRAFT", "SUBMITTED", "APPROVED", "PARTIAL", "PROCESSING", "COMPLETED", "CLOSED");
    private static final List<String> OPEN = List.of("APPROVED", "PARTIAL", "PROCESSING");

    public record Filter(LocalDate from, LocalDate to, String fabricType, String color, Long buyerId, Long bpoId, String status) {

        /** The order statuses the filter means: one, the open ones, or every one not cancelled or rejected. */
        List<String> statuses() {
            if (status == null || status.isBlank()) return ACTIVE;
            if ("OPEN".equalsIgnoreCase(status)) return OPEN;
            return List.of(BusinessDocumentStatus.valueOf(status.toUpperCase(Locale.ROOT)).name());
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductionBoardService boards;

    public ProductionDashboardService(NamedParameterJdbcTemplate jdbc, ProductionBoardService boards) {
        this.jdbc = jdbc;
        this.boards = boards;
    }

    // ================================================================================ dashboard

    public Map<String, Object> dashboard(Filter f, YearMonth month) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> lines = lines(f);
        boolean truncated = lines.size() > MAX_LINES;
        if (truncated) lines = lines.subList(0, MAX_LINES);
        List<Long> bpoIds = lines.stream().map(l -> (Long) l.get("bpoId")).distinct().toList();

        List<Map<String, Object>> orders = orders(lines, today);
        Map<String, BigDecimal> totals = sum(lines);
        MapSqlParameterSource scope = scope(bpoIds);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", today);
        out.put("truncated", truncated);
        out.put("lineCount", lines.size());
        Map<String, Object> bookings = bookings(f);
        List<Map<String, Object>> documents = documentCounts(scope);
        Map<String, Object> stock = stock(scope);
        out.put("kpis", kpis(orders, totals, bookings, documents, stock, scope, today));
        out.put("pipeline", pipeline(bookings, documents, totals));
        attachPlans(orders, scope);
        out.put("statusBoard", statusBoard(orders));
        out.put("orders", orders);
        out.put("workOrders", workOrders(scope));
        out.put("byFabricType", group(lines, l -> str(l.get("fabricType"), "Not set")));
        out.put("byColour", groupColour(lines));
        out.put("flow", flow(lines, scope));
        out.put("pending", pending(lines));
        out.put("efficiency", efficiency(totals, scope));
        out.put("stock", stock);
        out.put("movements", movements(f, scope));
        out.put("calendar", calendar(scope, month == null ? YearMonth.from(today) : month, today));
        out.put("alerts", alerts(orders, scope, today));
        return out;
    }

    /** The filtered production order lines, as the Production board reads them. */
    List<Map<String, Object>> lines(Filter f) {
        MapSqlParameterSource p = boards.scopeParams()
            .addValue("statuses", f.statuses())
            .addValue("teamId", null).addValue("buyerId", f.buyerId())
            .addValue("fabricType", blank(f.fabricType())).addValue("dueBy", null).addValue("q", "%")
            .addValue("bpoId", f.bpoId()).addValue("color", blank(f.color()))
            .addValue("fromDate", f.from()).addValue("toDate", f.to())
            .addValue("allBookings", true).addValue("bookingIds", List.of(-1L))
            .addValue("lateBy", LocalDate.now().plusDays(ProductionBoardService.LATE_WITHIN_DAYS))
            .addValue("limit", MAX_LINES + 1);
        return jdbc.queryForList("SELECT * FROM (" + ProductionBoardService.BOARD + ") b "
                + "ORDER BY b.required_date NULLS LAST, b.bpo_no, b.group_no, b.line_id LIMIT :limit", p)
            .stream().map(ProductionBoardService::camel).toList();
    }

    /**
     * The production order lines raised on these bookings (any version of them), as the Production
     * board reads them. The bookings are already the caller's scope - Booking analytics decides whose
     * bookings a viewer sees - so the team filter is not applied again here.
     */
    public List<Map<String, Object>> linesForBookings(Collection<Long> bookingIds) {
        if (bookingIds.isEmpty()) return List.of();
        MapSqlParameterSource p = boards.scopeParams().addValue("allTeams", true)
            .addValue("statuses", ACTIVE)
            .addValue("teamId", null).addValue("buyerId", null).addValue("fabricType", null).addValue("dueBy", null).addValue("q", "%")
            .addValue("bpoId", null).addValue("color", null).addValue("fromDate", null).addValue("toDate", null)
            .addValue("allBookings", false).addValue("bookingIds", List.copyOf(bookingIds))
            .addValue("lateBy", LocalDate.now().plusDays(ProductionBoardService.LATE_WITHIN_DAYS));
        return jdbc.queryForList("SELECT * FROM (" + ProductionBoardService.BOARD + ") b "
                + "ORDER BY b.required_date NULLS LAST, b.bpo_no, b.group_no, b.line_id", p)
            .stream().map(ProductionBoardService::camel).toList();
    }

    // ------------------------------------------------------------------------------ orders

    /** One row per production order: its lines summed, how far along it is, and whether it is late. */
    public static List<Map<String, Object>> orders(List<Map<String, Object>> lines, LocalDate today) {
        Map<Long, List<Map<String, Object>>> byOrder = lines.stream()
            .collect(Collectors.groupingBy(l -> (Long) l.get("bpoId"), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        byOrder.forEach((id, ls) -> {
            Map<String, Object> first = ls.get(0);
            Map<String, BigDecimal> s = sum(ls);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id);
            o.put("documentNo", first.get("bpoNo"));
            o.put("status", first.get("status"));
            o.put("documentDate", first.get("documentDate"));
            o.put("requiredDate", first.get("requiredDate"));
            o.put("buyer", first.get("buyer"));
            o.put("team", first.get("team"));
            o.put("fabricTypes", ls.stream().map(l -> str(l.get("fabricType"), "Not set")).distinct().toList());
            o.put("colours", ls.stream().map(l -> str(l.get("colorName"), "—")).distinct().toList());
            o.put("uom", first.get("uom"));
            o.putAll(s);
            boolean dyed = ls.stream().anyMatch(l -> Boolean.TRUE.equals(l.get("needsProcessing")));
            o.put("dyed", dyed);
            o.put("dyedQuantity", ls.stream().filter(l -> Boolean.TRUE.equals(l.get("needsProcessing")))
                .map(l -> dec(l.get("quantity"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            o.put("late", ls.stream().anyMatch(l -> Boolean.TRUE.equals(l.get("late"))));
            LocalDate required = date(first.get("requiredDate"));
            boolean open = OPEN.contains(String.valueOf(first.get("status")));
            o.put("overdue", open && required != null && required.isBefore(today) && s.get("balance").signum() > 0);
            o.put("daysLeft", required == null ? null : ChronoUnit.DAYS.between(today, required));
            o.put("noRoute", ls.stream().anyMatch(l -> l.get("routeCode") == null));
            o.put("stage", stage(s, dyed));
            out.add(o);
        });
        return out;
    }

    /** Each order's planned pre-deliveries (its Pre-delivery schedule tab), for the timeline. */
    private void attachPlans(List<Map<String, Object>> orders, MapSqlParameterSource scope) {
        Map<Long, List<Map<String, Object>>> plans = new HashMap<>();
        jdbc.queryForList("""
            SELECT pd.document_id, pd.delivery_date, t.name AS delivery_type, c.color_name, pd.quantity, pd.serial_no
            FROM fab_bpo_pre_deliveries pd
            JOIN fab_delivery_types t ON t.id = pd.delivery_type_id
            JOIN gbl_business_document_color_lines c ON c.id = pd.source_color_line_id
            WHERE pd.document_id IN (:bpoIds) ORDER BY pd.document_id, pd.line_no
            """, scope).forEach(r -> plans.computeIfAbsent(((Number) r.get("document_id")).longValue(), k -> new ArrayList<>())
            .add(ProductionBoardService.camel(r)));
        orders.forEach(o -> o.put("plans", plans.getOrDefault((Long) o.get("id"), List.of())));
    }

    /** The furthest the order has got - what a planner would say it is "at". */
    public static String stage(Map<String, BigDecimal> s, boolean dyed) {
        if (s.get("delivered").signum() > 0) return s.get("balance").signum() == 0 ? "Delivered" : "Delivering";
        if (s.get("scheduled").signum() > 0) return "Scheduled";
        if (dyed && s.get("finished").signum() > 0) return "Finishing";
        if (dyed && s.get("dyeing").signum() > 0) return "Dyeing";
        if (s.get("greigeReceived").signum() > 0) return "Greige in store";
        if (s.get("weaving").signum() > 0) return "Weaving";
        return "Not started";
    }

    private static final List<String> MEASURES = List.of("quantity", "greigeRequired", "weaving", "greigeReceived",
        "greigeOnHand", "dyeing", "greigeIssued", "finishedA", "finishedB", "finishedReady", "scheduled",
        "onDeliveryOrders", "delivered", "balance", "ready");

    /** The measures, summed over lines; finished = A + B. */
    public static Map<String, BigDecimal> sum(List<Map<String, Object>> lines) {
        Map<String, BigDecimal> s = new LinkedHashMap<>();
        for (String m : MEASURES) s.put(m, BigDecimal.ZERO);
        for (Map<String, Object> l : lines) {
            for (String m : MEASURES) s.merge(m, dec(l.get(m)), BigDecimal::add);
        }
        s.put("finished", s.get("finishedA").add(s.get("finishedB")));
        // What the order has made ready at the stage it delivers from: finished cloth, or greige.
        BigDecimal produced = BigDecimal.ZERO;
        for (Map<String, Object> l : lines) {
            produced = produced.add("FINISHED".equals(l.get("deliverStage"))
                ? dec(l.get("finishedA")).add(dec(l.get("finishedB"))) : dec(l.get("greigeReceived")));
        }
        s.put("produced", produced);
        s.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        return s;
    }

    // ------------------------------------------------------------------------------ kpis

    private Map<String, Object> kpis(List<Map<String, Object>> orders, Map<String, BigDecimal> t, Map<String, Object> bookings,
                                     List<Map<String, Object>> documents, Map<String, Object> stock,
                                     MapSqlParameterSource scope, LocalDate today) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("bookings", bookings);
        k.put("orders", Map.of("count", orders.size(),
            "open", orders.stream().filter(o -> OPEN.contains(String.valueOf(o.get("status")))).count(),
            "quantity", t.get("quantity"), "delivered", t.get("delivered")));
        k.put("weaving", workOrderKpi(documents, "WEAVING_WORK_ORDER", t.get("weaving"), t.get("greigeReceived")));
        k.put("dyeing", workOrderKpi(documents, "PROCESSING_WORK_ORDER", t.get("dyeing"), t.get("finished")));
        k.put("greigeStock", stock.get("greige"));
        k.put("finishedStock", stock.get("finished"));
        Map<String, Object> pending = jdbc.queryForMap("""
            SELECT count(*) AS lines, COALESCE(SUM(GREATEST(scheduled - delivered, 0)), 0) AS quantity,
                   COALESCE(SUM(deliverable), 0) AS deliverable,
                   COUNT(*) FILTER (WHERE days_due < 0 AND scheduled > delivered) AS overdue
            FROM (""" + ProductionBoardService.READY + ") r WHERE r.scheduled > r.delivered", ready(scope, today));
        k.put("pendingDelivery", ProductionBoardService.camel(pending));
        k.put("overdue", Map.of("orders", orders.stream().filter(o -> Boolean.TRUE.equals(o.get("overdue"))).count(),
            // At risk: due soon and not ready - not counting the ones already overdue.
            "late", orders.stream().filter(o -> Boolean.TRUE.equals(o.get("late")) && !Boolean.TRUE.equals(o.get("overdue"))).count(),
            "balance", orders.stream().filter(o -> Boolean.TRUE.equals(o.get("overdue")))
                .map(o -> (BigDecimal) o.get("balance")).reduce(BigDecimal.ZERO, BigDecimal::add)));
        return k;
    }

    private static Map<String, Object> workOrderKpi(List<Map<String, Object>> documents, String type, BigDecimal ordered,
                                                    BigDecimal done) {
        long open = documents.stream().filter(d -> type.equals(d.get("documentType")) && OPEN.contains(String.valueOf(d.get("status"))))
            .mapToLong(d -> ((Number) d.get("documents")).longValue()).sum();
        long all = documents.stream().filter(d -> type.equals(d.get("documentType")))
            .mapToLong(d -> ((Number) d.get("documents")).longValue()).sum();
        return Map.of("documents", all, "open", open, "ordered", ordered, "done", done);
    }

    // ------------------------------------------------------------------------------ bookings

    /** Bookings in the period that match the filters, and how much of the approved ones still needs a production order. */
    private Map<String, Object> bookings(Filter f) {
        Map<String, Object> m = jdbc.queryForMap("""
            SELECT count(*) AS count,
                   COUNT(*) FILTER (WHERE d.status IN ('APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED')) AS approved,
                   COUNT(*) FILTER (WHERE d.status = 'SUBMITTED') AS awaiting_approval,
                   COALESCE(SUM(d.total_quantity) FILTER (WHERE d.status NOT IN ('CANCELLED', 'REJECTED')), 0) AS quantity,
                   COALESCE(SUM((SELECT SUM(GREATEST(cl.quantity - cl.fulfilled_quantity, 0))
                                 FROM gbl_business_document_line_groups g
                                 JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
                                 WHERE g.document_id = d.id))
                                FILTER (WHERE d.status IN ('APPROVED', 'PARTIAL')), 0) AS awaiting_order
            """ + BOOKINGS, bookingParams(f));
        return ProductionBoardService.camel(m);
    }

    /** The bookings behind the Booking figures. */
    public List<Map<String, Object>> bookingDocuments(Filter f) {
        return jdbc.queryForList("""
            SELECT d.id, d.document_no, d.document_date, d.required_date, d.status, d.total_quantity, pty.name AS buyer,
                   'booking' AS slug
            """ + BOOKINGS + " ORDER BY d.document_date DESC, d.document_no DESC LIMIT 500", bookingParams(f))
            .stream().map(ProductionBoardService::camel).toList();
    }

    private MapSqlParameterSource bookingParams(Filter f) {
        return boards.scopeParams().addValue("from", f.from()).addValue("to", f.to())
            .addValue("buyerId", f.buyerId()).addValue("fabricType", blank(f.fabricType())).addValue("color", blank(f.color()))
            .addValue("bpoId", f.bpoId());
    }

    /** Bookings in the period that match the filters (the booking of the chosen order, when one is chosen). */
    private static final String BOOKINGS = """
            FROM gbl_business_documents d
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.document_type = 'BOOKING' AND d.deleted = false
              AND d.status NOT IN ('CANCELLED', 'REJECTED')
              AND (:allTeams OR d.marketing_team_id IN (:teams))
              AND (CAST(:from AS DATE) IS NULL OR d.document_date >= :from)
              AND (CAST(:to AS DATE) IS NULL OR d.document_date <= :to)
              AND (CAST(:buyerId AS BIGINT) IS NULL OR d.party_id = :buyerId)
              AND (CAST(:bpoId AS BIGINT) IS NULL OR d.id = (SELECT b.parent_document_id FROM gbl_business_documents b WHERE b.id = :bpoId))
              AND (CAST(:fabricType AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM gbl_business_document_line_groups g
                   WHERE g.document_id = d.id AND lower(g.fabric_type) = lower(CAST(:fabricType AS VARCHAR))))
              AND (CAST(:color AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM gbl_business_document_line_groups g
                   JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
                   WHERE g.document_id = d.id AND lower(COALESCE(cl.color_name, '')) = lower(CAST(:color AS VARCHAR))))
            """;

    // ------------------------------------------------------------------------------ pipeline

    /**
     * The chain's documents that belong to the filtered orders, counted by type and status. A
     * document belongs to the order at the top of its parent chain.
     */
    private List<Map<String, Object>> documentCounts(MapSqlParameterSource scope) {
        return jdbc.queryForList("""
            SELECT d.document_type, d.status, count(*) AS documents, COALESCE(SUM(d.total_quantity), 0) AS quantity,
                   COUNT(*) FILTER (WHERE d.status = 'SUBMITTED' AND d.updated_at < now() - make_interval(days => :stuck)) AS stuck
            FROM gbl_business_documents d""" + ROOT_JOIN + """
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.document_type IN (:chainTypes) AND d.status NOT IN ('CANCELLED')
              AND """ + ROOT + """
             IN (:bpoIds)
            GROUP BY d.document_type, d.status
            """, scope).stream().map(ProductionBoardService::camel).toList();
    }

    /** Joins up to the production order a chain document was raised under. */
    private static final String ROOT_JOIN = """

            LEFT JOIN gbl_business_documents p1 ON p1.id = d.parent_document_id
            LEFT JOIN gbl_business_documents p2 ON p2.id = p1.parent_document_id
            LEFT JOIN gbl_business_documents p3 ON p3.id = p2.parent_document_id
        """;

    /** The production order at the top of a chain document's parents. */
    private static final String ROOT = """
        (CASE d.document_type
                WHEN 'BULK_PRODUCTION_ORDER' THEN d.id
                WHEN 'WEAVING_WORK_ORDER' THEN p1.id WHEN 'PROCESSING_WORK_ORDER' THEN p1.id WHEN 'REQUEST_FOR_PI' THEN p1.id
                WHEN 'GREIGE_RECEIVE' THEN p2.id WHEN 'GREIGE_ISSUE' THEN p2.id WHEN 'FINISHED_FABRICS_RECEIVE' THEN p2.id
                WHEN 'DELIVERY_ORDER' THEN p2.id
                WHEN 'FABRICS_DELIVERY' THEN p3.id END)""";

    private static final List<String> CHAIN_TYPES = Arrays.stream(ChainStep.values()).map(s -> s.type().name()).toList();

    private static List<Map<String, Object>> pipeline(Map<String, Object> bookings, List<Map<String, Object>> documents,
                                                      Map<String, BigDecimal> t) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("key", "BOOKING");
        booking.put("label", "Booking");
        booking.put("slug", "booking");
        booking.put("documents", bookings.get("count"));
        booking.put("awaiting", bookings.get("awaitingApproval"));
        booking.put("quantity", bookings.get("quantity"));
        booking.put("measure", "Booked");
        booking.put("next", Map.of("label", "Still to order", "value", bookings.get("awaitingOrder")));
        nodes.add(booking);
        record Stage(ChainStep step, String measure, BigDecimal value, String nextLabel, BigDecimal next) { }
        List<Stage> stages = List.of(
            new Stage(ChainStep.BPO, "Ordered", t.get("quantity"), "Not yet scheduled", t.get("quantity").subtract(t.get("scheduled")).max(BigDecimal.ZERO)),
            new Stage(ChainStep.WWO, "On weaving WOs", t.get("weaving"), "Still to weave", t.get("greigeRequired").subtract(t.get("weaving")).max(BigDecimal.ZERO)),
            new Stage(ChainStep.GR, "Greige received", t.get("greigeReceived"), "In greige store", t.get("greigeOnHand")),
            new Stage(ChainStep.PWO, "On dyeing WOs", t.get("dyeing"), "Dyed, not yet finished", t.get("dyeing").subtract(t.get("finished")).max(BigDecimal.ZERO)),
            new Stage(ChainStep.GI, "Greige issued", t.get("greigeIssued"), null, null),
            new Stage(ChainStep.FFR, "Finished received", t.get("finished"), "Grade B", t.get("finishedB")),
            new Stage(ChainStep.RPI, "Scheduled", t.get("scheduled"), "Awaiting a delivery order", t.get("scheduled").subtract(t.get("onDeliveryOrders")).max(BigDecimal.ZERO)),
            new Stage(ChainStep.DO, "On delivery orders", t.get("onDeliveryOrders"), null, null),
            new Stage(ChainStep.FD, "Delivered", t.get("delivered"), "Balance to deliver", t.get("balance")));
        for (Stage s : stages) {
            String type = s.step().type().name();
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("key", s.step().name());
            n.put("label", s.step().label());
            n.put("slug", s.step().slug());
            n.put("documents", count(documents, type, null));
            n.put("open", count(documents, type, OPEN));
            n.put("awaiting", count(documents, type, List.of("DRAFT", "SUBMITTED")));
            n.put("stuck", documents.stream().filter(d -> type.equals(d.get("documentType")))
                .mapToLong(d -> ((Number) d.get("stuck")).longValue()).sum());
            n.put("measure", s.measure());
            n.put("quantity", s.value());
            if (s.nextLabel() != null) n.put("next", Map.of("label", s.nextLabel(), "value", s.next()));
            nodes.add(n);
        }
        return nodes;
    }

    private static long count(List<Map<String, Object>> documents, String type, List<String> statuses) {
        return documents.stream().filter(d -> type.equals(d.get("documentType"))
                && (statuses == null || statuses.contains(String.valueOf(d.get("status")))))
            .mapToLong(d -> ((Number) d.get("documents")).longValue()).sum();
    }

    // ------------------------------------------------------------------------------ status board

    private static final List<String> BOARD_COLUMNS = List.of("DRAFT", "SUBMITTED", "APPROVED", "IN_PROGRESS", "COMPLETED");

    /** Orders by where they stand, late ones first, each column capped; the rest are a count. */
    private static List<Map<String, Object>> statusBoard(List<Map<String, Object>> orders) {
        Map<String, List<Map<String, Object>>> byColumn = new LinkedHashMap<>();
        BOARD_COLUMNS.forEach(c -> byColumn.put(c, new ArrayList<>()));
        for (Map<String, Object> o : orders) {
            String s = String.valueOf(o.get("status"));
            String column = switch (s) {
                case "PROCESSING", "PARTIAL" -> "IN_PROGRESS";
                case "CLOSED" -> "COMPLETED";
                default -> s;
            };
            byColumn.computeIfAbsent(column, k -> new ArrayList<>()).add(o);
        }
        Comparator<Map<String, Object>> urgent = Comparator
            .comparing((Map<String, Object> o) -> !Boolean.TRUE.equals(o.get("overdue")))
            .thenComparing(o -> !Boolean.TRUE.equals(o.get("late")))
            .thenComparing(o -> date(o.get("requiredDate")), Comparator.nullsLast(Comparator.naturalOrder()));
        List<Map<String, Object>> out = new ArrayList<>();
        byColumn.forEach((column, list) -> {
            list.sort(urgent);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("status", column);
            c.put("count", list.size());
            c.put("quantity", list.stream().map(o -> (BigDecimal) o.get("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add));
            c.put("ids", list.stream().map(o -> o.get("id")).toList());
            out.add(c);
        });
        return out;
    }

    // ------------------------------------------------------------------------------ work orders

    /** Open weaving and dyeing work orders of the filtered orders, with what has come back against each. */
    private List<Map<String, Object>> workOrders(MapSqlParameterSource scope) {
        return jdbc.queryForList("""
            SELECT d.id, d.document_no, d.document_type, d.status, d.document_date, d.required_date, d.process_kind,
                   d.batch_closed, v.name AS vendor, bpo.id AS bpo_id, bpo.document_no AS bpo_no, pty.name AS buyer,
                   string_agg(DISTINCT g.fabric_type, ', ') AS fabric_type,
                   string_agg(DISTINCT cl.color_name, ', ') AS colours,
                   SUM(cl.quantity) AS ordered,
                   SUM(COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                         AND dr.source_id = cl.id AND dr.stream = 'GREIGE_RECEIVE'), 0)) AS greige_received,
                   SUM(COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                         AND dr.source_id = cl.id AND dr.stream = 'GREIGE_ISSUE'), 0)) AS greige_issued,
                   SUM(COALESCE((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                         AND dr.source_id = cl.id AND dr.stream = 'FINISHED_FABRICS_RECEIVE'), 0)) AS finished
            FROM gbl_business_documents d
            JOIN gbl_business_documents bpo ON bpo.id = d.parent_document_id
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
            LEFT JOIN pty_parties v ON v.id = d.vendor_party_id
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.document_type IN ('WEAVING_WORK_ORDER', 'PROCESSING_WORK_ORDER')
              AND d.status IN ('SUBMITTED', 'APPROVED', 'PARTIAL', 'PROCESSING')
              AND bpo.id IN (:bpoIds)
            GROUP BY d.id, v.name, bpo.id, pty.name
            ORDER BY d.required_date NULLS LAST, d.document_no
            LIMIT 200
            """, scope).stream().map(r -> {
            Map<String, Object> m = ProductionBoardService.camel(r);
            boolean weaving = "WEAVING_WORK_ORDER".equals(m.get("documentType"));
            m.put("kind", weaving ? "WWO" : "PWO");
            m.put("slug", weaving ? ChainStep.WWO.slug() : ChainStep.PWO.slug());
            m.put("done", weaving ? m.get("greigeReceived") : m.get("finished"));
            LocalDate due = date(m.get("requiredDate"));
            m.put("overdue", due != null && due.isBefore(LocalDate.now())
                && dec(m.get("done")).compareTo(dec(m.get("ordered"))) < 0);
            return m;
        }).toList();
    }

    // ------------------------------------------------------------------------------ groupings

    /** Fabric-type-wise figures - one row per value of {@code key}. */
    static List<Map<String, Object>> group(List<Map<String, Object>> lines, Function<Map<String, Object>, String> key) {
        Map<String, List<Map<String, Object>>> by = lines.stream()
            .collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        by.forEach((k, ls) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            m.put("orders", ls.stream().map(l -> l.get("bpoId")).distinct().count());
            m.put("lines", ls.size());
            m.put("uom", ls.get(0).get("uom"));
            m.putAll(sum(ls));
            out.add(m);
        });
        out.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("quantity")).reversed());
        return out;
    }

    /** Colour-wise figures, per fabric type and colour. */
    static List<Map<String, Object>> groupColour(List<Map<String, Object>> lines) {
        List<Map<String, Object>> rows = group(lines, l -> str(l.get("fabricType"), "Not set") + "\u0000" + str(l.get("colorName"), "—"));
        rows.forEach(r -> {
            String[] parts = ((String) r.get("key")).split("\u0000", 2);
            r.put("fabricType", parts[0]);
            r.put("colour", parts[1]);
            r.put("key", parts[0] + " · " + parts[1]);
        });
        return rows;
    }

    // ------------------------------------------------------------------------------ flow

    /** Greige to dyeing to finished, for the dyed lines, with the loss measured on closed batches. */
    private Map<String, Object> flow(List<Map<String, Object>> lines, MapSqlParameterSource scope) {
        Map<String, BigDecimal> s = sum(lines.stream().filter(l -> Boolean.TRUE.equals(l.get("needsProcessing"))).toList());
        Map<String, Object> loss = ProductionBoardService.camel(jdbc.queryForMap("""
            SELECT count(DISTINCT d.id) AS batches,
                   COALESCE(SUM((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                         AND dr.source_id = cl.id AND dr.stream = 'GREIGE_ISSUE')), 0) AS issued,
                   COALESCE(SUM((SELECT dr.drawn_quantity FROM gbl_line_draws dr WHERE dr.source_kind = 'COLOUR'
                         AND dr.source_id = cl.id AND dr.stream = 'FINISHED_FABRICS_RECEIVE')), 0) AS finished
            FROM gbl_business_documents d
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
            WHERE d.organization_id = :org AND d.document_type = 'PROCESSING_WORK_ORDER' AND d.deleted = false
              AND d.batch_closed = true AND d.parent_document_id IN (:bpoIds)
            """, scope));
        BigDecimal issued = dec(loss.get("issued")), finished = dec(loss.get("finished"));
        loss.put("loss", issued.subtract(finished).max(BigDecimal.ZERO));
        loss.put("lossPct", pct(issued.subtract(finished).max(BigDecimal.ZERO), issued));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stages", List.of(
            stageRow("GREIGE_REQUIRED", "Greige required", s.get("greigeRequired"), null),
            stageRow("WOVEN", "Greige received from weaving", s.get("greigeReceived"), "GR"),
            stageRow("ISSUED", "Issued to dyeing", s.get("greigeIssued"), "GI"),
            stageRow("FINISHED_A", "Finished, grade A", s.get("finishedA"), "FFR"),
            stageRow("FINISHED_B", "Finished, grade B", s.get("finishedB"), "FFR"),
            stageRow("DELIVERED", "Delivered", s.get("delivered"), "FD")));
        out.put("ordered", s.get("quantity"));
        out.put("greigeOnHand", s.get("greigeOnHand"));
        out.put("inProcess", s.get("greigeIssued").subtract(s.get("finished")).max(BigDecimal.ZERO));
        out.put("closedBatches", loss);
        return out;
    }

    private static Map<String, Object> stageRow(String key, String label, BigDecimal value, String step) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("value", value);
        m.put("step", step);
        return m;
    }

    // ------------------------------------------------------------------------------ pending

    /** What is still to do at each stage, over the open, not short-closed lines. */
    static List<Map<String, Object>> pending(List<Map<String, Object>> lines) {
        BigDecimal weave = BigDecimal.ZERO, receive = BigDecimal.ZERO, dye = BigDecimal.ZERO, finish = BigDecimal.ZERO,
            schedule = BigDecimal.ZERO, deliver = BigDecimal.ZERO;
        for (Map<String, Object> l : lines) {
            if (Boolean.TRUE.equals(l.get("shortClosed")) || !OPEN.contains(String.valueOf(l.get("status")))) continue;
            if (l.get("routeCode") != null) weave = weave.add(pos(dec(l.get("greigeRequired")).subtract(dec(l.get("weaving")))));
            receive = receive.add(pos(dec(l.get("weaving")).subtract(dec(l.get("greigeReceived")))));
            if (Boolean.TRUE.equals(l.get("needsProcessing"))) {
                dye = dye.add(pos(dec(l.get("quantity")).subtract(dec(l.get("dyeing")))));
                finish = finish.add(pos(dec(l.get("dyeing")).subtract(dec(l.get("finishedA"))).subtract(dec(l.get("finishedB")))));
            }
            schedule = schedule.add(pos(dec(l.get("quantity")).subtract(dec(l.get("scheduled")))));
            deliver = deliver.add(dec(l.get("balance")));
        }
        return List.of(
            Map.of("key", "weave", "label", "To put on weaving WOs", "value", weave, "step", "WWO"),
            Map.of("key", "receive", "label", "Greige to receive", "value", receive, "step", "GR"),
            Map.of("key", "dye", "label", "To put on dyeing WOs", "value", dye, "step", "PWO"),
            Map.of("key", "finish", "label", "Finished to receive", "value", finish, "step", "FFR"),
            Map.of("key", "schedule", "label", "To schedule for delivery", "value", schedule, "step", "RPI"),
            Map.of("key", "deliver", "label", "To deliver", "value", deliver, "step", "FD"));
    }

    // ------------------------------------------------------------------------------ efficiency

    private Map<String, Object> efficiency(Map<String, BigDecimal> t, MapSqlParameterSource scope) {
        Map<String, Object> onTime = ProductionBoardService.camel(jdbc.queryForMap("""
            WITH s AS (
                SELECT rl.id, rl.quantity, COALESCE(rl.delivery_date, d.required_date) AS due,
                       (SELECT SUM(fl.quantity) FROM gbl_business_document_color_lines fl
                        JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
                        JOIN gbl_business_documents fd ON fd.id = fg.document_id
                        JOIN gbl_business_document_color_lines dl ON dl.id = fl.source_color_line_id
                        WHERE fd.document_type = 'FABRICS_DELIVERY' AND fd.status = 'APPROVED' AND fd.deleted = false
                          AND dl.source_color_line_id = rl.id) AS delivered,
                       (SELECT MAX(fd.document_date) FROM gbl_business_document_color_lines fl
                        JOIN gbl_business_document_line_groups fg ON fg.id = fl.line_group_id
                        JOIN gbl_business_documents fd ON fd.id = fg.document_id
                        JOIN gbl_business_document_color_lines dl ON dl.id = fl.source_color_line_id
                        WHERE fd.document_type = 'FABRICS_DELIVERY' AND fd.status = 'APPROVED' AND fd.deleted = false
                          AND dl.source_color_line_id = rl.id) AS last_delivery
                FROM gbl_business_documents d
                JOIN gbl_business_document_line_groups g ON g.document_id = d.id
                JOIN gbl_business_document_color_lines rl ON rl.line_group_id = g.id
                JOIN gbl_business_document_color_lines bl ON bl.id = rl.source_color_line_id
                JOIN gbl_business_document_line_groups bg ON bg.id = bl.line_group_id
                WHERE d.organization_id = :org AND d.document_type = 'REQUEST_FOR_PI' AND d.deleted = false
                  AND d.status NOT IN ('CANCELLED', 'REJECTED', 'DRAFT') AND bg.document_id IN (:bpoIds)
            )
            SELECT COUNT(*) FILTER (WHERE delivered >= quantity) AS completed,
                   COUNT(*) FILTER (WHERE delivered >= quantity AND due IS NOT NULL AND last_delivery <= due) AS on_time
            FROM s
            """, scope));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("weavingCompletion", pct(t.get("greigeReceived"), t.get("weaving")));
        e.put("dyeingCompletion", pct(t.get("finished"), t.get("dyeing")));
        e.put("gradeA", pct(t.get("finishedA"), t.get("finished")));
        e.put("fulfilment", pct(t.get("delivered"), t.get("quantity")));
        e.put("onTime", pct(dec(onTime.get("onTime")), dec(onTime.get("completed"))));
        e.put("onTimeLines", onTime.get("onTime"));
        e.put("completedLines", onTime.get("completed"));
        return e;
    }

    // ------------------------------------------------------------------------------ stock

    private Map<String, Object> stock(MapSqlParameterSource scope) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.stage, COALESCE(l.grade, '') AS grade, w.id AS warehouse_id, w.name AS store,
                   SUM(b.quantity) AS quantity, SUM(b.reserved_quantity) AS reserved, SUM(b.quantity - b.reserved_quantity) AS free,
                   SUM(b.rolls) AS rolls, count(DISTINCT l.id) AS lots
            FROM inv_fabric_balances b
            JOIN inv_fabric_lots l ON l.id = b.lot_id
            JOIN org_warehouses w ON w.id = b.warehouse_id
            WHERE b.organization_id = :org AND b.quantity > 0 AND l.bpo_document_id IN (:bpoIds)
            GROUP BY l.stage, COALESCE(l.grade, ''), w.id, w.name
            ORDER BY l.stage DESC, w.name, 2
            """, scope).stream().map(ProductionBoardService::camel).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        for (String stage : List.of("GREIGE", "FINISHED")) {
            List<Map<String, Object>> of = rows.stream().filter(r -> stage.equals(r.get("stage"))).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quantity", total(of, "quantity"));
            m.put("reserved", total(of, "reserved"));
            m.put("free", total(of, "free"));
            m.put("lots", of.stream().mapToLong(r -> ((Number) r.get("lots")).longValue()).sum());
            if ("FINISHED".equals(stage)) {
                m.put("gradeA", total(of.stream().filter(r -> "A".equals(r.get("grade"))).toList(), "quantity"));
                m.put("gradeB", total(of.stream().filter(r -> "B".equals(r.get("grade"))).toList(), "quantity"));
            }
            out.put(stage.toLowerCase(Locale.ROOT), m);
        }
        return out;
    }

    /** Fabric moves of the filtered orders in the period: by type, and in and out over time. */
    private Map<String, Object> movements(Filter f, MapSqlParameterSource scope) {
        LocalDate to = f.to() != null ? f.to() : LocalDate.now();
        LocalDate from = f.from() != null ? f.from() : to.minusDays(89);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        String bucket = days <= 62 ? "day" : days <= 370 ? "week" : "month";
        MapSqlParameterSource p = new MapSqlParameterSource(scope.getValues())
            .addValue("from", from).addValue("to", to.plusDays(1)).addValue("bucket", bucket);
        List<Map<String, Object>> byType = jdbc.queryForList("""
            SELECT m.move_type, l.stage, count(*) AS moves, SUM(m.quantity) AS quantity, SUM(m.rolls) AS rolls
            FROM inv_fabric_moves m JOIN inv_fabric_lots l ON l.id = m.lot_id
            WHERE m.organization_id = :org AND l.bpo_document_id IN (:bpoIds)
              AND m.posted_at >= :from AND m.posted_at < :to
            GROUP BY m.move_type, l.stage ORDER BY l.stage DESC, m.move_type
            """, p).stream().map(ProductionBoardService::camel).toList();
        List<Map<String, Object>> series = jdbc.queryForList("""
            SELECT date_trunc(:bucket, m.posted_at)::date AS period,
                   COALESCE(SUM(m.quantity) FILTER (WHERE m.quantity > 0), 0) AS in_qty,
                   COALESCE(-SUM(m.quantity) FILTER (WHERE m.quantity < 0), 0) AS out_qty
            FROM inv_fabric_moves m JOIN inv_fabric_lots l ON l.id = m.lot_id
            WHERE m.organization_id = :org AND l.bpo_document_id IN (:bpoIds)
              AND m.posted_at >= :from AND m.posted_at < :to
            GROUP BY 1 ORDER BY 1
            """, p).stream().map(ProductionBoardService::camel).toList();
        return Map.of("from", from, "to", to, "bucket", bucket, "byType", byType, "series", series);
    }

    // ------------------------------------------------------------------------------ calendar

    /**
     * The month's deliveries: approved delivery schedule lines by due date (from Ready to deliver's
     * query), and the production orders' planned pre-deliveries.
     */
    private Map<String, Object> calendar(MapSqlParameterSource scope, YearMonth month, LocalDate today) {
        MapSqlParameterSource p = ready(scope, today).addValue("from", month.atDay(1)).addValue("to", month.atEndOfMonth());
        List<Map<String, Object>> schedule = jdbc.queryForList("SELECT * FROM (" + ProductionBoardService.READY + ") r "
                + "WHERE r.due_date BETWEEN :from AND :to ORDER BY r.due_date, r.schedule_no, r.line_id", p)
            .stream().map(r -> {
                Map<String, Object> m = ProductionBoardService.camel(r);
                BigDecimal left = dec(m.get("scheduled")).subtract(dec(m.get("delivered")));
                LocalDate due = date(m.get("dueDate"));
                m.put("state", left.signum() <= 0 ? "done" : due != null && due.isBefore(today) ? "overdue"
                    : dec(m.get("ready")).compareTo(left) >= 0 ? "ready" : "short");
                return m;
            }).toList();
        List<Map<String, Object>> planned = jdbc.queryForList("""
            SELECT pd.document_id AS bpo_id, d.document_no AS bpo_no, pd.delivery_date, t.name AS delivery_type,
                   c.color_name, pd.quantity, pd.serial_no, pty.name AS buyer
            FROM fab_bpo_pre_deliveries pd
            JOIN gbl_business_documents d ON d.id = pd.document_id
            JOIN fab_delivery_types t ON t.id = pd.delivery_type_id
            JOIN gbl_business_document_color_lines c ON c.id = pd.source_color_line_id
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            WHERE pd.document_id IN (:bpoIds) AND pd.delivery_date BETWEEN :from AND :to
            ORDER BY pd.delivery_date, d.document_no, pd.line_no
            """, p).stream().map(ProductionBoardService::camel).toList();
        return Map.of("month", month.toString(), "schedule", schedule, "planned", planned);
    }

    private MapSqlParameterSource ready(MapSqlParameterSource scope, LocalDate today) {
        return new MapSqlParameterSource(scope.getValues()).addValue("buyerId", null).addValue("q", "%")
            .addValue("scheduleStatuses", List.of("APPROVED", "PARTIAL", "COMPLETED", "CLOSED"))
            .addValue("allOrders", false).addValue("today", today);
    }

    // ------------------------------------------------------------------------------ alerts

    /** What needs someone's attention: overdue, at risk, blocked, stuck waiting for a signature, short of stock. */
    private List<Map<String, Object>> alerts(List<Map<String, Object>> orders, MapSqlParameterSource scope, LocalDate today) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            if (Boolean.TRUE.equals(o.get("overdue"))) {
                out.add(alert("critical", "OVERDUE", "Past its required date", o, "%s left, %d day(s) late".formatted(
                    qty(o.get("balance"), o.get("uom")), -((Number) o.get("daysLeft")).longValue())));
            } else if (Boolean.TRUE.equals(o.get("late"))) {
                out.add(alert("warning", "AT_RISK", "Due within %d days and not ready".formatted(ProductionBoardService.LATE_WITHIN_DAYS),
                    o, "Due " + o.get("requiredDate") + " · " + o.get("stage")));
            }
            if (Boolean.TRUE.equals(o.get("noRoute")) && List.of("DRAFT", "SUBMITTED").contains(String.valueOf(o.get("status")))) {
                out.add(alert("critical", "BLOCKED", "No process route for its fabric type", o,
                    "It cannot be submitted until Master data → Process routes covers " + String.join(", ", castList(o.get("fabricTypes")))));
            }
        }
        jdbc.queryForList("""
            SELECT d.id, d.document_no, d.document_type, d.status, d.updated_at::date AS since, pty.name AS buyer
            FROM gbl_business_documents d""" + ROOT_JOIN + """
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.document_type IN (:chainTypes)
              AND ((d.status = 'SUBMITTED' AND d.updated_at < now() - make_interval(days => :stuck)) OR d.status = 'REJECTED')
              AND """ + ROOT + """
             IN (:bpoIds)
            ORDER BY d.updated_at LIMIT 50
            """, scope).forEach(r -> {
            ChainStep step = ChainStep.of(com.asg.fabricerp.global.documents.DocumentType.valueOf((String) r.get("document_type"))).orElseThrow();
            boolean rejected = "REJECTED".equals(r.get("status"));
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("severity", rejected ? "critical" : "warning");
            a.put("kind", rejected ? "REJECTED" : "STUCK");
            a.put("title", rejected ? step.label() + " rejected" : step.label() + " waiting for approval");
            a.put("documentId", r.get("id"));
            a.put("documentNo", r.get("document_no"));
            a.put("slug", step.slug());
            a.put("buyer", r.get("buyer"));
            a.put("detail", rejected ? "Returned by the approver - correct and resubmit, or cancel"
                : "Submitted on " + r.get("since") + ", more than " + STUCK_DAYS + " days ago");
            out.add(a);
        });
        jdbc.queryForList("SELECT * FROM (" + ProductionBoardService.READY + ") r "
            + "WHERE r.days_due <= 3 AND r.to_order > 0 AND r.ready <= 0 ORDER BY r.due_date LIMIT 50", ready(scope, today)).forEach(r -> {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("severity", ((Number) r.get("days_due")).intValue() < 0 ? "critical" : "warning");
            a.put("kind", "NO_STOCK");
            a.put("title", "Due for delivery with nothing in stock");
            a.put("documentId", r.get("schedule_id"));
            a.put("documentNo", r.get("schedule_no"));
            a.put("slug", ChainStep.RPI.slug());
            a.put("buyer", r.get("buyer"));
            a.put("detail", "%s %s due %s; %s still to put on delivery orders".formatted(
                str(r.get("color_name"), ""), str(r.get("bpo_no"), ""), r.get("due_date"), qty(r.get("to_order"), r.get("uom"))));
            out.add(a);
        });
        out.sort(Comparator.comparing(a -> "critical".equals(a.get("severity")) ? 0 : 1));
        return out;
    }

    private static Map<String, Object> alert(String severity, String kind, String title, Map<String, Object> o, String detail) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("severity", severity);
        a.put("kind", kind);
        a.put("title", title);
        a.put("documentId", o.get("id"));
        a.put("documentNo", o.get("documentNo"));
        a.put("slug", ChainStep.BPO.slug());
        a.put("buyer", o.get("buyer"));
        a.put("detail", detail);
        return a;
    }

    // ================================================================================ drill-down

    /** The documents behind a pipeline stage or a KPI: one step's documents of the filtered orders. */
    public List<Map<String, Object>> documents(Filter f, ChainStep step, String statusGroup) {
        List<Long> bpoIds = lines(f).stream().limit(MAX_LINES).map(l -> (Long) l.get("bpoId")).distinct().toList();
        List<String> statuses = switch (statusGroup == null ? "" : statusGroup) {
            case "open" -> OPEN;
            case "awaiting" -> List.of("DRAFT", "SUBMITTED");
            default -> List.of("DRAFT", "SUBMITTED", "APPROVED", "PARTIAL", "PROCESSING", "COMPLETED", "CLOSED", "REJECTED");
        };
        MapSqlParameterSource p = scope(bpoIds).addValue("type", step.type().name()).addValue("statuses", statuses);
        return jdbc.queryForList("""
            SELECT d.id, d.document_no, d.document_date, d.required_date, d.status, d.total_quantity, pty.name AS buyer,
                   w.name AS store, CASE WHEN d.document_type = 'BULK_PRODUCTION_ORDER' THEN NULL ELSE p1.document_no END AS parent_no
            FROM gbl_business_documents d""" + ROOT_JOIN + """
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            LEFT JOIN org_warehouses w ON w.id = d.warehouse_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.document_type = :type AND d.status IN (:statuses)
              AND """ + ROOT + """
             IN (:bpoIds)
            ORDER BY d.document_date DESC, d.document_no DESC LIMIT 500
            """, p).stream().map(r -> {
            Map<String, Object> m = ProductionBoardService.camel(r);
            m.put("slug", step.slug());
            return m;
        }).toList();
    }

    // ================================================================================ helpers

    private MapSqlParameterSource scope(List<Long> bpoIds) {
        return boards.scopeParams().addValue("bpoIds", bpoIds.isEmpty() ? List.of(-1L) : bpoIds)
            .addValue("chainTypes", CHAIN_TYPES).addValue("stuck", STUCK_DAYS);
    }

    private static BigDecimal total(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> dec(r.get(key))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    private static BigDecimal pos(BigDecimal v) {
        return v.max(BigDecimal.ZERO);
    }

    public static LocalDate date(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(v.toString().substring(0, 10));
    }

    private static String str(Object v, String fallback) {
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }

    private static String qty(Object v, Object uom) {
        return String.format(Locale.ROOT, "%,.0f", dec(v)) + (uom == null ? "" : " " + uom);
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object v) {
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}

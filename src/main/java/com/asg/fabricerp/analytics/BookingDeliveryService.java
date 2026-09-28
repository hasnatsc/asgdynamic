package com.asg.fabricerp.analytics;

import com.asg.fabricerp.analytics.BookingAnalyticsSql.Criteria;
import com.asg.fabricerp.analytics.BookingAnalyticsSql.Query;
import com.asg.fabricerp.production.ProductionBoardService;
import com.asg.fabricerp.production.ProductionDashboardService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.asg.fabricerp.production.ProductionDashboardService.dec;

/**
 * Booking analytics' "Booking to delivery" tab: every booking in the viewer's scope, followed down
 * the chain to the buyer's gate - how much is on production orders, woven, finished, scheduled and
 * delivered, what stage it is at, and whether it is late.
 *
 * <p>The bookings are the page's own ({@link BookingAnalyticsSql#base}, the same filters and
 * {@link AnalyticsScope}); what happened to them is read from their production orders' lines by
 * the Production board's query ({@link ProductionDashboardService#linesForBookings}), so a booking's
 * figures here add up to its orders' figures on the Production board and dashboard.
 */
@Service
public class BookingDeliveryService {

    /** More bookings than this and the tab says it is showing the newest. */
    static final int MAX_BOOKINGS = 2000;
    /** A confirmed booking not yet on production orders this long is an alert. */
    static final int ORDER_WITHIN_DAYS = 3;
    /** Due within this many days and not ready: at risk (the Production board's rule). */
    static final int AT_RISK_DAYS = 7;

    /** Stages in the order a booking passes through them. */
    static final List<String> STAGES = List.of("Draft", "Awaiting approval", "Not ordered", "Ordered", "Weaving",
        "Greige in store", "Dyeing", "Finishing", "Scheduled", "Delivering", "Delivered", "Rejected");

    private final BookingAnalyticsService analytics;
    private final ProductionDashboardService production;
    private final NamedParameterJdbcTemplate jdbc;

    public BookingDeliveryService(BookingAnalyticsService analytics, ProductionDashboardService production,
                                  NamedParameterJdbcTemplate jdbc) {
        this.analytics = analytics;
        this.production = production;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> delivery(BookingAnalyticsService.Request r) {
        AnalyticsScope scope = analytics.scope();
        String currency = r.currency() == null || r.currency().isBlank()
            || BookingAnalyticsService.ALL_CURRENCIES.equalsIgnoreCase(r.currency()) ? null : r.currency().trim();
        Criteria c = analytics.criteria(scope, r, currency);
        LocalDate today = LocalDate.now();

        Query q = BookingAnalyticsSql.deliveryBookings(c, MAX_BOOKINGS + 1);
        List<Map<String, Object>> bookings = jdbc.queryForList(q.sql(), q.params()).stream()
            .map(ProductionBoardService::camel).collect(Collectors.toCollection(ArrayList::new));
        boolean truncated = bookings.size() > MAX_BOOKINGS;
        if (truncated) bookings = new ArrayList<>(bookings.subList(0, MAX_BOOKINGS));

        List<Long> ids = bookings.stream().map(b -> ((Number) b.get("id")).longValue()).toList();
        Map<Long, Long> versionToBooking = new HashMap<>();
        if (!ids.isEmpty()) {
            Query v = BookingAnalyticsSql.bookingVersions(ids);
            jdbc.queryForList(v.sql(), v.params()).forEach(row ->
                versionToBooking.put(((Number) row.get("version_id")).longValue(), ((Number) row.get("booking_id")).longValue()));
        }
        Map<Long, List<Map<String, Object>>> linesByBooking = production.linesForBookings(versionToBooking.keySet()).stream()
            .collect(Collectors.groupingBy(l -> versionToBooking.get(((Number) l.get("bookingId")).longValue())));

        List<Map<String, Object>> rows = bookings.stream()
            .map(b -> follow(b, linesByBooking.getOrDefault(((Number) b.get("id")).longValue(), List.of()), today))
            .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", today);
        out.put("from", c.from());
        out.put("to", c.to());
        out.put("truncated", truncated);
        out.put("view", c.view());
        out.put("bookings", rows);
        List<Map<String, Object>> confirmed = rows.stream().filter(BookingDeliveryService::confirmed).toList();
        Map<String, BigDecimal> t = totals(confirmed);
        out.put("kpis", kpis(rows, confirmed, t));
        out.put("funnel", funnel(t));
        out.put("stageMix", stageMix(rows));
        out.put("byMonth", byMonth(confirmed));
        out.put("alerts", alerts(rows, today));
        return out;
    }

    // ------------------------------------------------------------------------------ one booking

    /** One booking, followed through its production orders. */
    static Map<String, Object> follow(Map<String, Object> b, List<Map<String, Object>> lines, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>(b);
        Map<String, BigDecimal> s = ProductionDashboardService.sum(lines);
        BigDecimal booked = dec(b.get("quantity"));
        boolean dyed = lines.stream().anyMatch(l -> Boolean.TRUE.equals(l.get("needsProcessing")));
        m.put("booked", booked);
        m.put("ordered", s.get("quantity"));
        m.put("greigeRequired", s.get("greigeRequired"));
        m.put("greigeReceived", s.get("greigeReceived"));
        m.put("dyeing", s.get("dyeing"));
        m.put("finished", s.get("finished"));
        m.put("produced", s.get("produced"));
        m.put("scheduled", s.get("scheduled"));
        m.put("onDeliveryOrders", s.get("onDeliveryOrders"));
        m.put("delivered", s.get("delivered"));
        m.put("ready", s.get("ready"));
        m.put("dyed", dyed);
        m.put("uom", lines.isEmpty() ? null : lines.get(0).get("uom"));
        m.put("stillToOrder", booked.subtract(s.get("quantity")).max(BigDecimal.ZERO));
        BigDecimal balance = booked.subtract(s.get("delivered")).max(BigDecimal.ZERO);
        m.put("balance", balance);

        String group = String.valueOf(b.get("statusGroup"));
        String status = String.valueOf(b.get("status"));
        m.put("stage", stage(group, lines.isEmpty(), s, dyed, booked));
        LocalDate required = ProductionDashboardService.date(b.get("requiredDate"));
        boolean open = "CONFIRMED".equals(group) && !"CLOSED".equals(status) && balance.signum() > 0;
        boolean overdue = open && required != null && required.isBefore(today);
        boolean atRisk = open && !overdue && required != null && !required.isAfter(today.plusDays(AT_RISK_DAYS))
            && s.get("ready").add(s.get("delivered")).compareTo(booked) < 0;
        m.put("open", open);
        m.put("overdue", overdue);
        m.put("atRisk", atRisk);
        m.put("daysLeft", required == null ? null : ChronoUnit.DAYS.between(today, required));
        m.put("orders", ProductionDashboardService.orders(lines, today).stream().map(o -> {
            Map<String, Object> x = new LinkedHashMap<>();
            for (String k : List.of("id", "documentNo", "status", "stage", "requiredDate", "quantity", "delivered",
                "balance", "overdue", "late", "fabricTypes", "colours")) x.put(k, o.get(k));
            return x;
        }).toList());
        return m;
    }

    /** Where a booking is: its own approval first, then the furthest its production orders have got. */
    static String stage(String group, boolean noOrders, Map<String, BigDecimal> s, boolean dyed, BigDecimal booked) {
        switch (group) {
            case "DRAFT": return "Draft";
            case "PENDING": return "Awaiting approval";
            case "REJECTED": return "Rejected";
            default: break;
        }
        if (noOrders) return "Not ordered";
        if (s.get("delivered").compareTo(booked) >= 0 && booked.signum() > 0) return "Delivered";
        String furthest = ProductionDashboardService.stage(s, dyed);
        return switch (furthest) {
            case "Not started" -> "Ordered";
            case "Delivered" -> "Delivering";          // its orders are delivered, but not all it booked is on them
            default -> furthest;
        };
    }

    private static boolean confirmed(Map<String, Object> b) {
        return "CONFIRMED".equals(b.get("statusGroup"));
    }

    // ------------------------------------------------------------------------------ roll-ups

    private static final List<String> MEASURES = List.of("booked", "ordered", "greigeRequired", "greigeReceived", "dyeing",
        "finished", "produced", "scheduled", "onDeliveryOrders", "delivered", "ready", "stillToOrder", "balance");

    static Map<String, BigDecimal> totals(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> t = new LinkedHashMap<>();
        MEASURES.forEach(k -> t.put(k, BigDecimal.ZERO));
        rows.forEach(r -> MEASURES.forEach(k -> t.merge(k, dec(r.get(k)), BigDecimal::add)));
        return t;
    }

    private static Map<String, Object> kpis(List<Map<String, Object>> rows, List<Map<String, Object>> confirmed,
                                            Map<String, BigDecimal> t) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("bookings", rows.size());
        k.put("confirmed", confirmed.size());
        k.put("pipeline", rows.stream().filter(r -> List.of("DRAFT", "PENDING").contains(r.get("statusGroup"))).count());
        k.put("booked", t.get("booked"));
        k.put("ordered", t.get("ordered"));
        k.put("orderedPct", ProductionDashboardService.pct(t.get("ordered"), t.get("booked")));
        k.put("stillToOrder", t.get("stillToOrder"));
        k.put("notOrdered", confirmed.stream().filter(r -> "Not ordered".equals(r.get("stage"))).count());
        k.put("produced", t.get("produced"));
        k.put("producedPct", ProductionDashboardService.pct(t.get("produced"), t.get("booked")));
        k.put("delivered", t.get("delivered"));
        k.put("deliveredPct", ProductionDashboardService.pct(t.get("delivered"), t.get("booked")));
        k.put("balance", t.get("balance"));
        k.put("fullyDelivered", confirmed.stream().filter(r -> "Delivered".equals(r.get("stage"))).count());
        k.put("overdue", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("overdue"))).count());
        k.put("overdueBalance", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("overdue")))
            .map(r -> dec(r.get("balance"))).reduce(BigDecimal.ZERO, BigDecimal::add));
        k.put("atRisk", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("atRisk"))).count());
        return k;
    }

    /** Booked, and how much of it has reached each stage - confirmed bookings only. */
    private static List<Map<String, Object>> funnel(Map<String, BigDecimal> t) {
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal booked = t.get("booked");
        for (String[] s : new String[][] {
            {"booked", "Booked (confirmed)"}, {"ordered", "On production orders"}, {"greigeReceived", "Greige woven and received"},
            {"finished", "Finished received (dyed fabric)"}, {"scheduled", "Scheduled for delivery"},
            {"onDeliveryOrders", "On delivery orders"}, {"delivered", "Delivered"}}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", s[0]);
            m.put("label", s[1]);
            m.put("value", t.get(s[0]));
            m.put("pct", ProductionDashboardService.pct(t.get(s[0]), booked));
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> stageMix(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> by = rows.stream().collect(Collectors.groupingBy(r -> (String) r.get("stage")));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String stage : STAGES) {
            List<Map<String, Object>> of = by.getOrDefault(stage, List.of());
            if (of.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", stage);
            m.put("bookings", of.size());
            m.put("booked", of.stream().map(r -> dec(r.get("booked"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("balance", of.stream().map(r -> dec(r.get("balance"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            out.add(m);
        }
        return out;
    }

    /** By the month booked: booked, ordered, produced and delivered, oldest month first. */
    static List<Map<String, Object>> byMonth(List<Map<String, Object>> confirmed) {
        Map<String, List<Map<String, Object>>> by = confirmed.stream().collect(Collectors.groupingBy(
            r -> String.valueOf(ProductionDashboardService.date(r.get("documentDate"))).substring(0, 7), TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        by.forEach((month, of) -> {
            Map<String, BigDecimal> t = totals(of);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("bookings", of.size());
            for (String k : List.of("booked", "ordered", "produced", "delivered", "balance")) m.put(k, t.get(k));
            out.add(m);
        });
        return out;
    }

    // ------------------------------------------------------------------------------ alerts

    static List<Map<String, Object>> alerts(List<Map<String, Object>> rows, LocalDate today) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> b : rows) {
            Object uom = b.get("uom");
            if (Boolean.TRUE.equals(b.get("overdue"))) {
                out.add(alert(b, "critical", "OVERDUE", "Past its delivery date", "%s still to deliver, %d day(s) late"
                    .formatted(qty(b.get("balance"), uom), -((Number) b.get("daysLeft")).longValue())));
            } else if (Boolean.TRUE.equals(b.get("atRisk"))) {
                out.add(alert(b, "warning", "AT_RISK", "Due within %d days and not ready".formatted(AT_RISK_DAYS),
                    "Due " + b.get("requiredDate") + " · " + b.get("stage") + " · " + qty(b.get("balance"), uom) + " to deliver"));
            }
            LocalDate booked = ProductionDashboardService.date(b.get("documentDate"));
            if (confirmed(b) && dec(b.get("stillToOrder")).signum() > 0 && booked != null
                    && booked.isBefore(today.minusDays(ORDER_WITHIN_DAYS))) {
                out.add(alert(b, "warning", "NOT_ORDERED", "Confirmed but not on production orders",
                    "%s of %s still to put on production orders; booked %s".formatted(qty(b.get("stillToOrder"), uom),
                        qty(b.get("booked"), uom), booked)));
            }
        }
        out.sort(Comparator.comparing((Map<String, Object> a) -> "critical".equals(a.get("severity")) ? 0 : 1)
            .thenComparing(a -> String.valueOf(a.get("requiredDate"))));
        return out;
    }

    private static Map<String, Object> alert(Map<String, Object> b, String severity, String kind, String title, String detail) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("severity", severity);
        a.put("kind", kind);
        a.put("title", title);
        a.put("bookingId", b.get("id"));
        a.put("documentNo", b.get("documentNo"));
        a.put("buyer", b.get("buyer"));
        a.put("requiredDate", b.get("requiredDate"));
        a.put("detail", detail);
        return a;
    }

    private static String qty(Object v, Object uom) {
        return String.format(Locale.ROOT, "%,.0f", dec(v).setScale(0, RoundingMode.HALF_UP)) + (uom == null ? "" : " " + uom);
    }
}

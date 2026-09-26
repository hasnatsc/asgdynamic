package com.asg.fabricerp.analytics;

import com.asg.fabricerp.analytics.BookingAnalyticsSql.Criteria;
import com.asg.fabricerp.analytics.BookingAnalyticsSql.Dimension;
import com.asg.fabricerp.analytics.BookingAnalyticsSql.Grain;
import com.asg.fabricerp.analytics.BookingAnalyticsSql.Query;
import com.asg.fabricerp.approval.ApprovalActors;
import com.asg.fabricerp.approval.ApprovalMatrixRepository;
import com.asg.fabricerp.approval.Approver;
import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Booking analytics and reports: one set of figures for team members, supervisors, approvers and
 * management, each seeing the bookings their {@link AnalyticsScope} covers.
 *
 * <p>The SQL, and what every figure means, is in {@link BookingAnalyticsSql}. This class decides
 * whose bookings a request may cover, runs the statements and shapes the answer for the screen.
 * Every figure on one screen is computed from the same filters, so the tiles, charts and tables
 * always agree.
 */
@Service
public class BookingAnalyticsService {

    /** The most rows one register download or report carries. */
    static final int EXPORT_LIMIT = 50_000;
    /** The longest period one request may cover. */
    static final int MAX_DAYS = 3 * 366;
    static final String ALL_CURRENCIES = "ALL";

    private static final Map<String, String> STATUS_LABELS = Map.of(
        "CONFIRMED", "Confirmed", "PENDING", "Awaiting approval", "DRAFT", "Draft",
        "REJECTED", "Rejected", "CANCELLED", "Cancelled");
    private static final Map<String, String> AGING_LABELS = new LinkedHashMap<>();
    static {
        AGING_LABELS.put("0-1", "Under a day");
        AGING_LABELS.put("1-3", "1-3 days");
        AGING_LABELS.put("3-7", "3-7 days");
        AGING_LABELS.put("7-14", "1-2 weeks");
        AGING_LABELS.put("14+", "Over 2 weeks");
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;
    private final ApprovalActors actors;
    private final MarketingTeamRepository teams;
    private final ApprovalMatrixRepository matrices;

    public BookingAnalyticsService(NamedParameterJdbcTemplate jdbc, OrgContext context, ApprovalActors actors,
                                   MarketingTeamRepository teams, ApprovalMatrixRepository matrices) {
        this.jdbc = jdbc;
        this.context = context;
        this.actors = actors;
        this.teams = teams;
        this.matrices = matrices;
    }

    /** What the screen asks for. Every field optional; see {@link #criteria}. */
    public record Request(LocalDate from, LocalDate to, AnalyticsView view, Long teamId, Long personId, Long buyerId,
                          String currency, String status, String bookingType, String q) { }

    // ------------------------------------------------------------------------------ scope

    /** Whose bookings the signed-in user may analyse. */
    @Transactional(readOnly = true)
    public AnalyticsScope scope() {
        Long orgId = context.requireOrganizationId();
        RowScope rowScope = context.requireRowScope();
        Approver.Actor actor = actors.current();
        boolean management = !rowScope.restricts(ScopeDimension.MARKETING_TEAM);
        Set<Long> memberTeams = rowScope.allowedOn(ScopeDimension.MARKETING_TEAM);
        Set<Long> led = actor.userId() == null ? Set.of() : Set.copyOf(teams.findIdsLedBy(orgId, actor.userId()));
        List<AnalyticsScope.Matrix> bookingMatrices = matrices
            .findActiveForType(orgId, context.requireBusinessUnitId(), DocumentType.BOOKING).stream()
            .map(m -> new AnalyticsScope.Matrix(m.getMarketingTeamId(), m.getLevels().stream()
                .map(l -> new AnalyticsScope.MatrixLevel(l.getRoleId(), l.getUserId())).toList()))
            .toList();
        Set<Long> allTeams = teams.lookup(orgId).stream().map(MarketingTeam::getId).collect(Collectors.toSet());
        return AnalyticsScope.resolve(actor.userId(), actor.username(), management, memberTeams, led, bookingMatrices,
            actor.roleIds(), actor.authorities().contains(DocumentType.BOOKING.approveAuthority()), allTeams);
    }

    /**
     * The request, checked against the scope: an unoffered view falls back to the broadest one
     * offered; a team outside the scope is refused; a person filter needs a team-wide view. With no
     * period, the last twelve months (whole months) to today.
     */
    Criteria criteria(AnalyticsScope scope, Request r, String currency) {
        LocalDate today = LocalDate.now();
        LocalDate to = r.to() == null ? today : r.to();
        LocalDate from = r.from() == null ? to.withDayOfMonth(1).minusMonths(11) : r.from();
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The period starts after it ends");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new IllegalArgumentException("Choose a period of at most three years");
        }
        AnalyticsView view = scope.offers(r.view()) ? r.view() : scope.defaultView();
        Long teamId = r.teamId();
        if (teamId != null && view == AnalyticsView.TEAM && !scope.teamIds().contains(teamId)) {
            throw new AccessDeniedException("That team is outside what you can analyse");
        }
        if (teamId != null && view == AnalyticsView.MINE) teamId = null;
        Long personId = view == AnalyticsView.MINE ? null : r.personId();
        String status = r.status() == null || r.status().isBlank() ? null : r.status().trim().toUpperCase(Locale.ROOT);
        if (status != null && !BookingAnalyticsSql.STATUS_GROUPS.contains(status)) {
            throw new IllegalArgumentException("Unknown status " + r.status());
        }
        String bookingType = r.bookingType() == null || r.bookingType().isBlank() ? null : r.bookingType().trim();
        String q = r.q() == null || r.q().isBlank() ? null : r.q().trim();
        return new Criteria(context.requireOrganizationId(), context.requireBusinessUnitId(), from, to,
            view, scope.username(), scope.userId(), List.copyOf(scope.teamIds()),
            teamId, personId, r.buyerId(), currency, status, bookingType, q);
    }

    /**
     * The currency the figures are in: the one asked for; for "ALL", none (counts and quantities
     * only); for nothing, the one most bookings in scope are in.
     */
    private String currencyFor(Request r, List<Map<String, Object>> currencies) {
        String asked = r.currency() == null ? "" : r.currency().trim().toUpperCase(Locale.ROOT);
        if (ALL_CURRENCIES.equals(asked)) return null;
        if (!asked.isEmpty()) {
            if (!asked.matches("[A-Z]{3}")) throw new IllegalArgumentException("Unknown currency " + r.currency());
            return asked;
        }
        return currencies.isEmpty() ? null : (String) currencies.getFirst().get("currency");
    }

    // ------------------------------------------------------------------------------ overview

    /** Everything the Overview tab draws, from one set of filters. */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Request r) {
        AnalyticsScope scope = scope();
        Criteria probe = criteria(scope, r, null);
        List<Map<String, Object>> currencies = rows(BookingAnalyticsSql.currencies(probe));
        String currency = currencyFor(r, currencies);
        Criteria c = criteria(scope, r, currency);
        boolean money = currency != null;
        Grain grain = Grain.forPeriod(c.from(), c.to());
        Criteria previous = c.previousPeriod();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", scopeView(scope, c.view()));
        out.put("period", Map.of("from", c.from().toString(), "to", c.to().toString(),
            "previousFrom", previous.from().toString(), "previousTo", previous.to().toString(),
            "grain", grain.name()));
        out.put("currency", currency);
        out.put("currencies", currencies.stream()
            .map(m -> Map.of("code", m.get("currency"), "bookings", num(m.get("bookings"))))
            .toList());
        out.put("kpis", kpis(one(BookingAnalyticsSql.kpis(c)), money));
        out.put("previous", kpis(one(BookingAnalyticsSql.kpis(previous)), money));
        out.put("trend", rows(BookingAnalyticsSql.trend(c, grain)).stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", m.get("bucket"));
            row.put("confirmed", num(m.get("confirmed")));
            row.put("pipeline", num(m.get("pipeline")));
            row.put("confirmedQty", num(m.get("confirmed_qty")));
            row.put("pipelineQty", num(m.get("pipeline_qty")));
            row.put("confirmedValue", money ? num(m.get("confirmed_value")) : null);
            row.put("pipelineValue", money ? num(m.get("pipeline_value")) : null);
            return row;
        }).toList());
        out.put("status", statusMix(c, money));
        boolean teamWide = c.view() != AnalyticsView.MINE;
        out.put("byTeam", teamWide && c.teamId() == null ? top(breakdown(c, Dimension.TEAM, money), 10, money) : null);
        out.put("byPerson", teamWide && c.personId() == null ? top(breakdown(c, Dimension.PERSON, money), 10, money) : null);
        out.put("byBuyer", c.buyerId() == null ? top(breakdown(c, Dimension.BUYER, money), 10, money) : null);
        out.put("byFabric", top(breakdown(c, Dimension.FABRIC_TYPE, money), 8, money));
        out.put("delivery", delivery(c, money));
        out.put("aging", aging(c, money));
        return out;
    }

    private Map<String, Object> scopeView(AnalyticsScope scope, AnalyticsView view) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("view", view.name());
        s.put("views", scope.views().stream().map(v -> Map.of("key", v.name(), "label", v.label())).toList());
        s.put("reasons", scope.reasons());
        List<MarketingTeam> all = teams.lookup(context.requireOrganizationId());
        s.put("teams", switch (view) {
            case MINE -> List.of();
            case TEAM -> all.stream().filter(t -> scope.teamIds().contains(t.getId()))
                .map(t -> Map.of("id", t.getId(), "name", t.getName())).toList();
            case ALL -> all.stream().map(t -> Map.of("id", t.getId(), "name", t.getName())).toList();
        });
        return s;
    }

    private Map<String, Object> kpis(Map<String, Object> m, boolean money) {
        Map<String, Object> k = new LinkedHashMap<>();
        for (String key : List.of("bookings", "confirmed", "pipeline", "pending", "rejected", "cancelled", "revised", "buyers")) {
            k.put(key, num(m.get(key)));
        }
        BigDecimal confirmedQty = num(m.get("confirmed_qty"));
        BigDecimal confirmedValue = num(m.get("confirmed_value"));
        k.put("quantity", num(m.get("quantity")));
        k.put("confirmedQty", confirmedQty);
        k.put("confirmedValue", money ? confirmedValue : null);
        k.put("pipelineValue", money ? num(m.get("pipeline_value")) : null);
        k.put("avgRate", money ? ratio(confirmedValue, confirmedQty, 4) : null);
        k.put("marginPct", money ? marginPct(m.get("margin_sales"), m.get("margin_cost")) : null);
        k.put("marginCoveragePct", percent(num(m.get("margin_covered_qty")), num(m.get("confirmed_line_qty"))));
        k.put("fulfilmentPct", percent(num(m.get("fulfilled_qty")), num(m.get("confirmed_line_qty"))));
        k.put("avgLeadDays", scale(numOrNull(m.get("avg_lead_days")), 1));
        k.put("avgApprovalHours", scale(numOrNull(m.get("avg_approval_hours")), 1));
        k.put("approvalRatePct", percent(num(m.get("approvals_approved")), num(m.get("approvals_decided"))));
        return k;
    }

    private List<Map<String, Object>> statusMix(Criteria c, boolean money) {
        Map<String, Map<String, Object>> found = new HashMap<>();
        for (Map<String, Object> m : rows(BookingAnalyticsSql.statusMix(c))) found.put((String) m.get("status_group"), m);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String group : BookingAnalyticsSql.STATUS_GROUPS) {
            Map<String, Object> m = found.getOrDefault(group, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("group", group);
            row.put("label", STATUS_LABELS.get(group));
            row.put("bookings", num(m.get("bookings")));
            row.put("quantity", num(m.get("quantity")));
            row.put("value", money ? num(m.get("value")) : null);
            out.add(row);
        }
        return out;
    }

    /** One breakdown, shaped: sums kept for folding, ratios computed. */
    List<Map<String, Object>> breakdown(Criteria c, Dimension dimension, boolean money) {
        return rows(BookingAnalyticsSql.breakdown(c, dimension)).stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", m.get("key") == null ? null : String.valueOf(m.get("key")));
            row.put("label", m.get("label"));
            for (String key : List.of("bookings", "confirmed", "pending", "rejected")) row.put(key, num(m.get(key)));
            row.put("quantity", num(m.get("quantity")));
            row.put("confirmedQty", num(m.get("confirmed_qty")));
            row.put("confirmedValue", money ? num(m.get("confirmed_value")) : null);
            row.put("pipelineValue", money ? num(m.get("pipeline_value")) : null);
            row.put("marginSales", numOrNull(m.get("margin_sales")));
            row.put("marginCost", numOrNull(m.get("margin_cost")));
            row.put("fulfilledQty", num(m.get("fulfilled_qty")));
            row.put("confirmedLineQty", num(m.get("confirmed_line_qty")));
            return finish(row, money);
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    private static Map<String, Object> finish(Map<String, Object> row, boolean money) {
        row.put("avgRate", money ? ratio((BigDecimal) row.get("confirmedValue"), (BigDecimal) row.get("confirmedQty"), 4) : null);
        row.put("marginPct", money ? marginPct(row.get("marginSales"), row.get("marginCost")) : null);
        row.put("fulfilmentPct", percent((BigDecimal) row.get("fulfilledQty"), (BigDecimal) row.get("confirmedLineQty")));
        return row;
    }

    /** The first {@code n} rows, the rest folded into one "Other" row - a chart never cycles past its palette. */
    static List<Map<String, Object>> top(List<Map<String, Object>> rows, int n, boolean money) {
        if (rows.size() <= n + 1) return rows;
        List<Map<String, Object>> out = new ArrayList<>(rows.subList(0, n));
        Map<String, Object> other = new LinkedHashMap<>();
        List<Map<String, Object>> tail = rows.subList(n, rows.size());
        other.put("key", "OTHER");
        other.put("label", "Other (" + tail.size() + ")");
        other.put("other", true);
        for (String key : List.of("bookings", "confirmed", "pending", "rejected", "quantity", "confirmedQty",
                "confirmedValue", "pipelineValue", "marginSales", "marginCost", "fulfilledQty", "confirmedLineQty")) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean any = false;
            for (Map<String, Object> r : tail) {
                if (r.get(key) instanceof BigDecimal v) { sum = sum.add(v); any = true; }
            }
            other.put(key, any ? sum : null);
        }
        out.add(finish(other, money));
        return out;
    }

    private List<Map<String, Object>> delivery(Criteria c, boolean money) {
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> found = new HashMap<>();
        for (Map<String, Object> m : rows(BookingAnalyticsSql.deliverySchedule(c, today))) found.put((String) m.get("bucket"), m);
        List<String[]> buckets = new ArrayList<>();
        buckets.add(new String[] {"OVERDUE", "Overdue"});
        LocalDate week = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        for (int i = 0; i < 12; i++) {
            LocalDate start = week.plusWeeks(i);
            buckets.add(new String[] {start.toString(), i == 0 ? "This week" : "w/c " + start});
        }
        buckets.add(new String[] {"LATER", "Later"});
        buckets.add(new String[] {"UNSCHEDULED", "No date"});
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] b : buckets) {
            Map<String, Object> m = found.getOrDefault(b[0], Map.of());
            if (m.isEmpty() && (b[0].equals("UNSCHEDULED") || b[0].equals("LATER"))) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", b[0]);
            row.put("label", b[1]);
            row.put("bookings", num(m.get("bookings")));
            row.put("openQty", num(m.get("open_qty")));
            row.put("openValue", money ? num(m.get("open_value")) : null);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> aging(Criteria c, boolean money) {
        Map<String, Map<String, Object>> found = new HashMap<>();
        for (Map<String, Object> m : rows(BookingAnalyticsSql.approvalAging(c, LocalDateTime.now()))) found.put((String) m.get("bucket"), m);
        List<Map<String, Object>> out = new ArrayList<>();
        AGING_LABELS.forEach((bucket, label) -> {
            Map<String, Object> m = found.getOrDefault(bucket, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", bucket);
            row.put("label", label);
            row.put("bookings", num(m.get("bookings")));
            row.put("quantity", num(m.get("quantity")));
            row.put("value", money ? num(m.get("value")) : null);
            out.add(row);
        });
        return out;
    }

    // ------------------------------------------------------------------------------ reports

    /** A report's column: {@code type} is text, count, qty, money, rate, pct, date, datetime or days. */
    public record Column(String key, String label, String type) { }

    /** The reports the Reports tab offers, in its order. The register is paged separately. */
    public enum Report {
        BUYER("Buyer-wise summary", Dimension.BUYER, "Buyer"),
        PERSON("Marketing person-wise summary", Dimension.PERSON, "Marketing person"),
        TEAM("Team-wise summary", Dimension.TEAM, "Team"),
        MONTH("Month-wise summary", Dimension.MONTH, "Month"),
        FABRIC("Fabric type-wise summary", Dimension.FABRIC_TYPE, "Fabric type"),
        CONSTRUCTION("Price & margin by construction", Dimension.CONSTRUCTION, "Construction"),
        DELIVERY("Delivery schedule (open quantity)", null, null),
        PENDING("Pending approvals", null, null);

        final String title;
        final Dimension dimension;
        final String dimensionLabel;

        Report(String title, Dimension dimension, String dimensionLabel) {
            this.title = title;
            this.dimension = dimension;
            this.dimensionLabel = dimensionLabel;
        }

        public String title() { return title; }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(Report report, Request r) {
        AnalyticsScope scope = scope();
        String currency = currencyFor(r, rows(BookingAnalyticsSql.currencies(criteria(scope, r, null))));
        Criteria c = criteria(scope, r, currency);
        boolean money = currency != null;
        if (report == Report.PERSON && c.view() == AnalyticsView.MINE) {
            throw new IllegalArgumentException("The marketing person summary needs a team view");
        }
        if (report == Report.TEAM && c.view() == AnalyticsView.MINE) {
            throw new IllegalArgumentException("The team summary needs a team view");
        }

        List<Column> columns = new ArrayList<>();
        List<Map<String, Object>> rows;
        switch (report) {
            case DELIVERY -> {
                columns.addAll(List.of(new Column("documentNo", "Booking No", "text"), new Column("buyer", "Buyer", "text"),
                    new Column("team", "Team", "text"), new Column("person", "Marketing person", "text"),
                    new Column("requiredDate", "Delivery date", "date"), new Column("daysToDue", "Days to due", "days"),
                    new Column("quantity", "Booked qty", "qty"), new Column("fulfilled", "Drawn qty", "qty"),
                    new Column("openQty", "Open qty", "qty")));
                if (money) columns.add(new Column("openValue", "Open value", "money"));
                rows = rows(BookingAnalyticsSql.openDeliveries(c, LocalDate.now())).stream().map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", m.get("id"));
                    row.put("documentNo", m.get("document_no"));
                    row.put("buyer", m.get("buyer"));
                    row.put("team", m.get("team"));
                    row.put("person", m.get("person"));
                    row.put("requiredDate", date(m.get("required_date")));
                    row.put("daysToDue", m.get("days_to_due"));
                    row.put("quantity", num(m.get("quantity")));
                    row.put("fulfilled", num(m.get("fulfilled")));
                    row.put("openQty", num(m.get("open_qty")));
                    row.put("openValue", money ? num(m.get("open_value")) : null);
                    return row;
                }).toList();
            }
            case PENDING -> {
                columns.addAll(List.of(new Column("documentNo", "Booking No", "text"), new Column("buyer", "Buyer", "text"),
                    new Column("team", "Team", "text"), new Column("person", "Marketing person", "text"),
                    new Column("submittedAt", "Submitted", "datetime"), new Column("daysWaiting", "Days waiting", "days"),
                    new Column("level", "Level", "text"), new Column("quantity", "Quantity", "qty")));
                if (money) columns.add(new Column("value", "Value", "money"));
                rows = rows(BookingAnalyticsSql.pendingApprovals(c, LocalDateTime.now())).stream().map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", m.get("id"));
                    row.put("documentNo", m.get("document_no"));
                    row.put("buyer", m.get("buyer"));
                    row.put("team", m.get("team"));
                    row.put("person", m.get("person"));
                    row.put("submittedAt", dateTime(m.get("submitted_at")));
                    row.put("daysWaiting", num(m.get("days_waiting")));
                    row.put("level", m.get("current_level") + " of " + m.get("total_levels"));
                    row.put("quantity", num(m.get("quantity")));
                    row.put("value", money ? num(m.get("value")) : null);
                    return row;
                }).toList();
            }
            default -> {
                columns.add(new Column("label", report.dimensionLabel, "text"));
                columns.addAll(List.of(new Column("bookings", "Bookings", "count"), new Column("confirmed", "Confirmed", "count"),
                    new Column("pending", "Awaiting approval", "count"), new Column("rejected", "Rejected", "count"),
                    new Column("quantity", "Booked qty", "qty"), new Column("confirmedQty", "Confirmed qty", "qty")));
                if (money) {
                    columns.addAll(List.of(new Column("confirmedValue", "Confirmed value", "money"),
                        new Column("sharePct", "Share of value", "pct"), new Column("pipelineValue", "Pipeline value", "money"),
                        new Column("avgRate", "Avg price", "rate"), new Column("marginPct", "Margin vs break-even", "pct")));
                } else {
                    columns.add(new Column("sharePct", "Share of qty", "pct"));
                }
                columns.add(new Column("fulfilmentPct", "Drawn", "pct"));
                List<Map<String, Object>> found = breakdown(c, report.dimension, money);
                String shareOf = money ? "confirmedValue" : "confirmedQty";
                BigDecimal total = found.stream().map(m -> (BigDecimal) m.get(shareOf)).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                found.forEach(m -> m.put("sharePct", percent((BigDecimal) m.get(shareOf), total)));
                rows = found;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", report.name());
        out.put("title", report.title());
        out.put("currency", currency);
        out.put("period", Map.of("from", c.from().toString(), "to", c.to().toString()));
        out.put("forwardLooking", report == Report.DELIVERY || report == Report.PENDING);
        out.put("columns", columns);
        out.put("rows", rows);
        out.put("totals", totals(columns, rows, money));
        return out;
    }

    /** Sums of the count, quantity and money columns; the ratios recomputed from the sums, never averaged. */
    private static Map<String, Object> totals(List<Column> columns, List<Map<String, Object>> rows, boolean money) {
        Map<String, Object> t = new LinkedHashMap<>();
        for (Column col : columns) {
            if (!Set.of("count", "qty", "money").contains(col.type())) continue;
            BigDecimal sum = BigDecimal.ZERO;
            for (Map<String, Object> r : rows) if (r.get(col.key()) instanceof BigDecimal v) sum = sum.add(v);
            t.put(col.key(), sum);
        }
        if (rows.stream().anyMatch(r -> r.containsKey("marginSales"))) {
            BigDecimal sales = sumOf(rows, "marginSales"), cost = sumOf(rows, "marginCost");
            if (money) {
                t.put("marginPct", marginPct(sales, cost));
                t.put("avgRate", ratio((BigDecimal) t.get("confirmedValue"), (BigDecimal) t.get("confirmedQty"), 4));
            }
            t.put("fulfilmentPct", percent(sumOf(rows, "fulfilledQty"), sumOf(rows, "confirmedLineQty")));
            t.put("sharePct", rows.isEmpty() ? null : new BigDecimal("100.0"));
        }
        return t;
    }

    // ------------------------------------------------------------------------------ register

    @Transactional(readOnly = true)
    public DataTableResponse<Map<String, Object>> register(Request r, int draw, int start, int length,
                                                         String sortColumn, String sortDir) {
        AnalyticsScope scope = scope();
        String currency = registerCurrency(r);
        Criteria c = criteria(scope, r, currency);
        int size = Math.min(Math.max(length, 1), 100);
        List<Map<String, Object>> rows = rows(BookingAnalyticsSql.register(c, sortColumn, "asc".equalsIgnoreCase(sortDir),
            size, Math.max(start, 0))).stream().map(BookingAnalyticsService::registerRow).toList();
        long total = num(one(BookingAnalyticsSql.registerCount(c)).get("total")).longValue();
        return new DataTableResponse<>(draw, total, total, rows);
    }

    /** The register as CSV, every row in scope up to {@link #EXPORT_LIMIT}. */
    @Transactional(readOnly = true)
    public void registerCsv(Request r, Writer out) throws IOException {
        Criteria c = criteria(scope(), r, registerCurrency(r));
        out.write('﻿');   // Excel reads UTF-8
        out.write(csvLine(List.of("Booking No", "Reference", "Booking date", "Delivery date", "Status", "Revision",
            "Buyer", "Team", "Marketing person", "Currency", "Quantity", "Drawn qty", "Value", "Approval level")));
        for (Map<String, Object> m : rows(BookingAnalyticsSql.register(c, "documentDate", false, EXPORT_LIMIT, 0))) {
            Map<String, Object> row = registerRow(m);
            out.write(csvLine(Arrays.asList(row.get("documentNo"), row.get("referenceNo"), row.get("documentDate"),
                row.get("requiredDate"), row.get("status"), row.get("revisionNo"), row.get("buyer"), row.get("team"),
                row.get("person"), row.get("currency"), row.get("quantity"), row.get("fulfilled"), row.get("value"),
                row.get("approvalLevel"))));
        }
    }

    /** The register lists every currency unless one is chosen: each row carries its own. */
    private static String registerCurrency(Request r) {
        String asked = r.currency() == null ? "" : r.currency().trim().toUpperCase(Locale.ROOT);
        return asked.isEmpty() || ALL_CURRENCIES.equals(asked) || !asked.matches("[A-Z]{3}") ? null : asked;
    }

    private static Map<String, Object> registerRow(Map<String, Object> m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.get("id"));
        row.put("documentNo", m.get("document_no"));
        row.put("referenceNo", m.get("reference_no"));
        row.put("documentDate", date(m.get("document_date")));
        row.put("requiredDate", date(m.get("required_date")));
        row.put("status", m.get("status"));
        row.put("statusGroup", m.get("status_group"));
        row.put("revisionNo", m.get("revision_no"));
        row.put("buyer", m.get("buyer"));
        row.put("team", m.get("team"));
        row.put("person", m.get("person"));
        row.put("currency", m.get("currency_code"));
        row.put("quantity", num(m.get("quantity")));
        row.put("fulfilled", num(m.get("fulfilled")));
        row.put("value", num(m.get("value")));
        row.put("approvalLevel", m.get("current_level") == null ? null : m.get("current_level") + " of " + m.get("total_levels"));
        return row;
    }

    /** Marketing persons with bookings in scope - the person filter's feed. */
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> persons(Request r, String q, Integer page, Integer size) {
        Criteria c = criteria(scope(), r, null);
        if (c.view() == AnalyticsView.MINE) return LookupPage.of(List.of(), false);
        int s = size == null || size < 1 ? LookupPage.DEFAULT_SIZE : Math.min(size, LookupPage.MAX_SIZE);
        int p = page == null || page < 1 ? 0 : page - 1;
        List<Map<String, Object>> found = rows(BookingAnalyticsSql.persons(c, q, s + 1, p * s));
        List<LookupPage.Option> options = found.stream().limit(s)
            .map(m -> new LookupPage.Option(((Number) m.get("id")).longValue(), (String) m.get("username"), (String) m.get("name"), null))
            .toList();
        return LookupPage.of(options, found.size() > s);
    }

    // ------------------------------------------------------------------------------ helpers

    private List<Map<String, Object>> rows(Query q) {
        return jdbc.queryForList(q.sql(), q.params());
    }

    private Map<String, Object> one(Query q) {
        List<Map<String, Object>> rows = rows(q);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    /** A number from a result set, zero when absent, without trailing zeros (and never in E-notation). */
    static BigDecimal num(Object o) {
        BigDecimal b = numOrNull(o);
        return b == null ? BigDecimal.ZERO : b;
    }

    /** The same, but null stays null - for averages and margins that have no value, rather than a zero one. */
    static BigDecimal numOrNull(Object o) {
        if (o == null) return null;
        BigDecimal b = o instanceof BigDecimal d ? d : new BigDecimal(o.toString());
        BigDecimal stripped = b.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    private static BigDecimal sumOf(List<Map<String, Object>> rows, String key) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) if (r.get(key) instanceof BigDecimal v) sum = sum.add(v);
        return sum;
    }

    static BigDecimal ratio(BigDecimal a, BigDecimal b, int scale) {
        if (a == null || b == null || b.signum() == 0) return null;
        return a.divide(b, scale, RoundingMode.HALF_UP);
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** (sales - cost) / sales: the share of the booked price above the costing's break-even. */
    static BigDecimal marginPct(Object sales, Object cost) {
        if (sales == null || cost == null) return null;
        BigDecimal s = num(sales), k = num(cost);
        return s.signum() == 0 ? null : s.subtract(k).multiply(BigDecimal.valueOf(100)).divide(s, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v, int scale) {
        return v == null ? null : v.setScale(scale, RoundingMode.HALF_UP);
    }

    private static String date(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date d) return d.toLocalDate().toString();
        return o.toString();
    }

    private static String dateTime(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().withNano(0).toString();
        return o.toString();
    }

    static String csvLine(List<?> cells) {
        StringJoiner line = new StringJoiner(",", "", "\r\n");
        for (Object cell : cells) {
            String text = cell == null ? "" : cell instanceof BigDecimal b ? b.toPlainString() : cell.toString();
            // A leading = + - @ would run as a formula in Excel: quote it as text.
            if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !(cell instanceof Number)) text = "'" + text;
            line.add(text.matches("(?s).*[\",\r\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text);
        }
        return line.toString();
    }
}

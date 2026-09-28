package com.asg.fabricerp.web;

import com.asg.fabricerp.approval.ApprovalRowView;
import com.asg.fabricerp.approval.ApprovalService;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.production.ProductionBoardService;
import com.asg.fabricerp.security.Screen;
import com.asg.fabricerp.security.Verb;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The home page's "Needs your attention" and "All apps", and each app's own dashboard.
 *
 * <p>An app is a module of the menu ({@link Screen.Section}); a user has the apps in which they
 * may VIEW at least one screen - the same rule that draws the sidebar ({@link Navigation}), so the
 * apps, the menu and the {@code @PreAuthorize} checks never disagree. A module's documents are the
 * document types of its screens ({@link DocumentType#roleRoot()} names the screen).
 *
 * <p>Every count is read from the documents, limited as the lists limit them: the operating
 * business unit and the user's row scope on store and marketing team
 * ({@link com.asg.fabricerp.global.documents.BusinessDocument#isVisibleTo}). "Yours" means
 * raised by the signed-in user; "awaiting you" is their approval inbox.
 */
@Service
@Transactional(readOnly = true)
public class ModuleDashboardService {

    private static final List<String> OPEN = List.of("APPROVED", "PARTIAL", "PROCESSING");
    private static final List<String> DONE = List.of("COMPLETED", "CLOSED");
    private static final int INBOX_READ = 500;

    /** What each app is for, shown under its name. */
    private static final Map<Screen.Section, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry(Screen.Section.WORKFLOW, "Approvals waiting for you and every request in the unit"),
        Map.entry(Screen.Section.ANALYTICS, "Booking analytics, the production dashboard and reports"),
        Map.entry(Screen.Section.SETUP, "Parties, fabric setup, qualities, routes and terms"),
        Map.entry(Screen.Section.SALES, "Bookings and delivery schedules"),
        Map.entry(Screen.Section.PURCHASE, "Requisitions, purchase orders, receipts and returns"),
        Map.entry(Screen.Section.COMMERCIAL, "Export and import PI, LC and CI"),
        Map.entry(Screen.Section.INVENTORY, "Items, stock, store requisitions, issues and transfers"),
        Map.entry(Screen.Section.PRODUCTION, "Production orders, weaving and dyeing work orders"),
        Map.entry(Screen.Section.STORES, "Greige and finished stores, delivery orders and deliveries"),
        Map.entry(Screen.Section.ACCOUNTS, "Chart of accounts, journals, credit control and reports"),
        Map.entry(Screen.Section.ADMINISTRATION, "Users, roles, numbering and approval matrices"));

    /** Dashboards kept in another app that belong on this one's page too. */
    private static final Map<Screen.Section, List<Screen>> RELATED = Map.of(
        Screen.Section.SALES, List.of(Screen.BOOKING_ANALYTICS, Screen.PROD_DASHBOARD),
        Screen.Section.PRODUCTION, List.of(Screen.PROD_DASHBOARD, Screen.FABRIC_STOCK),
        Screen.Section.STORES, List.of(Screen.PROD_DASHBOARD, Screen.ITEM_STOCK),
        Screen.Section.INVENTORY, List.of(Screen.FABRIC_STOCK),
        Screen.Section.PURCHASE, List.of(Screen.ITEM_STOCK),
        Screen.Section.COMMERCIAL, List.of(Screen.BOOKING_ANALYTICS));

    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;
    private final ApprovalService approvals;

    public ModuleDashboardService(NamedParameterJdbcTemplate jdbc, OrgContext context, ApprovalService approvals) {
        this.jdbc = jdbc;
        this.context = context;
        this.approvals = approvals;
    }

    // ================================================================================ modules

    /** The URL key of a module: {@code sales}, {@code production}... */
    public static String key(Screen.Section section) {
        return section.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Screen.Section> section(String key) {
        return Arrays.stream(Screen.Section.values()).filter(s -> key(s).equals(key)).findFirst();
    }

    /** The screens of a module the user may open. */
    public static List<Screen> screens(Screen.Section section, Set<String> authorities) {
        return Arrays.stream(Screen.values())
            .filter(s -> s.section() == section && authorities.contains(s.authority(Verb.VIEW))).toList();
    }

    /** The document types a screen lists (a screen is named by its types' role root). */
    static List<DocumentType> typesOf(Screen screen) {
        return Arrays.stream(DocumentType.values()).filter(t -> {
            try {
                return t.roleRoot().equals(screen.name());
            } catch (UnsupportedOperationException none) {
                return false;
            }
        }).toList();
    }

    /** The screen a document type is kept on, when it has one. */
    static Optional<Screen> screenOf(DocumentType type) {
        try {
            String root = type.roleRoot();
            return Arrays.stream(Screen.values()).filter(s -> s.name().equals(root)).findFirst();
        } catch (UnsupportedOperationException none) {
            return Optional.empty();
        }
    }

    /** Where a document opens: its screen, told to open it. */
    static String openPath(Screen screen, Object id) {
        return screen.path() + "?open=" + id;
    }

    // ================================================================================ home

    /** "Needs your attention" and "All apps", for the user's authorities. */
    public Map<String, Object> home(Set<String> authorities) {
        List<Screen.Section> sections = Arrays.stream(Screen.Section.values())
            .filter(s -> !screens(s, authorities).isEmpty()).toList();
        List<DocumentType> types = sections.stream().flatMap(s -> screens(s, authorities).stream())
            .flatMap(s -> typesOf(s).stream()).distinct().toList();

        List<ApprovalRowView> inbox = inbox();
        Map<String, Long> inboxBySection = inbox.stream().collect(Collectors.groupingBy(
            r -> sectionOf(r.documentType()).map(ModuleDashboardService::key).orElse(""), Collectors.counting()));
        List<Map<String, Object>> mine = mine(types);
        Map<String, Long> mineBySection = mine.stream().collect(Collectors.groupingBy(m -> (String) m.get("module"), Collectors.counting()));

        List<Map<String, Object>> apps = sections.stream().map(s -> {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("key", key(s));
            a.put("label", s.label());
            a.put("icon", Navigation.iconFor(s));
            a.put("description", DESCRIPTIONS.get(s));
            a.put("screens", screens(s, authorities).size());
            a.put("awaitingYou", inboxBySection.getOrDefault(key(s), 0L));
            a.put("yours", mineBySection.getOrDefault(key(s), 0L));
            return a;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attention", attention(inbox.size(), mine));
        out.put("apps", apps);
        return out;
    }

    private static Map<String, Object> attention(long awaiting, List<Map<String, Object>> mine) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("awaitingYou", awaiting);
        a.put("returned", mine.stream().filter(m -> "returned".equals(m.get("kind"))).count());
        a.put("rejected", mine.stream().filter(m -> "rejected".equals(m.get("kind"))).count());
        a.put("drafts", mine.stream().filter(m -> "draft".equals(m.get("kind"))).count());
        return a;
    }

    /** The user's own documents that are theirs to act on, of one kind (draft, returned, rejected) or all. */
    public List<Map<String, Object>> myWork(Set<String> authorities, String module, String kind) {
        List<Screen.Section> sections = module == null || module.isBlank() ? Arrays.asList(Screen.Section.values())
            : section(module).map(List::of).orElseThrow(() -> new IllegalArgumentException("No such app: " + module));
        List<DocumentType> types = sections.stream().flatMap(s -> screens(s, authorities).stream())
            .flatMap(s -> typesOf(s).stream()).distinct().toList();
        if ("approval".equals(kind)) {
            Set<String> names = types.stream().map(Enum::name).collect(Collectors.toSet());
            boolean everywhere = module == null || module.isBlank();
            return inbox().stream().filter(r -> everywhere || names.contains(r.documentType())).map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.documentId());
                m.put("documentNo", r.documentNo());
                m.put("typeLabel", r.documentTypeLabel());
                m.put("party", r.partyName());
                m.put("status", "SUBMITTED");
                m.put("kind", "approval");
                m.put("detail", "Level %d of %d · raised by %s".formatted(r.level(), r.totalLevels(), r.raisedBy()));
                m.put("at", r.submittedAt());
                m.put("path", r.screenPath() == null ? null : r.screenPath() + "?open=" + r.documentId());
                sectionOf(r.documentType()).ifPresent(sec -> m.put("moduleLabel", sec.label()));
                return m;
            }).toList();
        }
        return mine(types).stream().filter(m -> kind == null || kind.isBlank() || kind.equals(m.get("kind"))).toList();
    }

    /** What waits for the user's signature, as their inbox lists it. */
    private List<ApprovalRowView> inbox() {
        return approvals.inbox(ApprovalService.InboxFilter.NONE, PageRequest.of(0, INBOX_READ)).getContent();
    }

    private static Optional<Screen.Section> sectionOf(String documentType) {
        try {
            return screenOf(DocumentType.valueOf(documentType)).map(Screen::section);
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /**
     * The user's drafts, and what came back to them: returned to correct (a draft whose last
     * approval step was RETURNED) or rejected.
     */
    private List<Map<String, Object>> mine(List<DocumentType> types) {
        if (types.isEmpty() || context.username() == null) return List.of();
        MapSqlParameterSource p = new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("unit", context.requireBusinessUnitId()).addValue("me", context.username())
            .addValue("types", types.stream().map(Enum::name).toList());
        return jdbc.queryForList("""
            SELECT d.id, d.document_no, d.document_type, d.status, d.document_date, d.updated_at, pty.name AS party,
                   h.action AS last_action, h.remarks AS last_remarks, h.created_at AS last_action_at
            FROM gbl_business_documents d
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            LEFT JOIN LATERAL (SELECT x.action, x.remarks, x.created_at FROM apr_document_history x
                               WHERE x.document_id = d.id ORDER BY x.created_at DESC, x.id DESC LIMIT 1) h ON true
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.created_by = :me AND d.status IN ('DRAFT', 'REJECTED') AND d.document_type IN (:types)
            ORDER BY COALESCE(h.created_at, d.updated_at, d.created_at) DESC
            LIMIT 500
            """, p).stream().map(r -> {
            Map<String, Object> m = ProductionBoardService.camel(r);
            DocumentType type = DocumentType.valueOf((String) r.get("document_type"));
            Screen screen = screenOf(type).orElseThrow();
            String kind = "REJECTED".equals(r.get("status")) ? "rejected" : "RETURNED".equals(r.get("last_action")) ? "returned" : "draft";
            m.put("kind", kind);
            m.put("typeLabel", type.label());
            m.put("module", key(screen.section()));
            m.put("moduleLabel", screen.section().label());
            m.put("path", openPath(screen, r.get("id")));
            return m;
        }).toList();
    }

    // ================================================================================ one module

    /** One app's dashboard: its menu, its documents by status, the user's work in it, recent activity. */
    public Map<String, Object> module(Screen.Section section, Set<String> authorities) {
        List<Screen> screens = screens(section, authorities);
        Map<Screen, List<DocumentType>> documentScreens = new LinkedHashMap<>();
        List<Screen> others = new ArrayList<>();
        for (Screen s : screens) {
            List<DocumentType> types = typesOf(s);
            if (types.isEmpty()) others.add(s); else documentScreens.put(s, types);
        }
        List<DocumentType> types = documentScreens.values().stream().flatMap(List::stream).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key(section));
        out.put("label", section.label());
        out.put("icon", Navigation.iconFor(section));
        out.put("description", DESCRIPTIONS.get(section));
        out.put("today", LocalDate.now());
        out.put("documents", documentRows(documentScreens, authorities));
        out.put("tools", others.stream().map(s -> link(s)).toList());
        out.put("related", RELATED.getOrDefault(section, List.of()).stream()
            .filter(s -> s.section() != section && authorities.contains(s.authority(Verb.VIEW))).map(ModuleDashboardService::link).toList());

        List<Map<String, Object>> mine = mine(types);
        Set<String> moduleTypes = types.stream().map(Enum::name).collect(Collectors.toSet());
        long awaiting = inbox().stream().filter(r -> moduleTypes.contains(r.documentType())).count();
        out.put("attention", attention(awaiting, mine));
        out.put("recent", recent(types));
        out.put("activity", activity(types));
        return out;
    }

    private static Map<String, Object> link(Screen s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", s.name());
        m.put("label", s.label());
        m.put("path", s.path());
        m.put("icon", Navigation.iconFor(s));
        m.put("module", s.section().label());
        return m;
    }

    /** Each document screen with its documents counted by status, and whether the user may raise one. */
    private List<Map<String, Object>> documentRows(Map<Screen, List<DocumentType>> documentScreens, Set<String> authorities) {
        if (documentScreens.isEmpty()) return List.of();
        Map<String, Map<String, Long>> byType = new HashMap<>();
        Map<String, Long> thisMonth = new HashMap<>();
        MapSqlParameterSource p = visible(documentScreens.values().stream().flatMap(List::stream).toList())
            .addValue("month", LocalDate.now().withDayOfMonth(1));
        jdbc.query("""
            SELECT d.document_type, d.status, count(*) AS n, count(*) FILTER (WHERE d.document_date >= :month) AS month_n
            FROM gbl_business_documents d
            WHERE """ + VISIBLE + """
            GROUP BY d.document_type, d.status
            """, p, rs -> {
            byType.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getLong(3));
            thisMonth.merge(rs.getString(1), rs.getLong(4), Long::sum);
        });
        List<Map<String, Object>> rows = new ArrayList<>();
        documentScreens.forEach((screen, types) -> {
            Map<String, Long> counts = new HashMap<>();
            long month = 0;
            for (DocumentType t : types) {
                byType.getOrDefault(t.name(), Map.of()).forEach((status, n) -> counts.merge(status, n, Long::sum));
                month += thisMonth.getOrDefault(t.name(), 0L);
            }
            Map<String, Object> r = new LinkedHashMap<>(link(screen));
            r.put("draft", counts.getOrDefault("DRAFT", 0L));
            r.put("submitted", counts.getOrDefault("SUBMITTED", 0L));
            r.put("open", OPEN.stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum());
            r.put("done", DONE.stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum());
            r.put("rejected", counts.getOrDefault("REJECTED", 0L));
            r.put("cancelled", counts.getOrDefault("CANCELLED", 0L));
            r.put("total", counts.values().stream().mapToLong(Long::longValue).sum());
            r.put("thisMonth", month);
            boolean canCreate = authorities.contains(screen.authority(Verb.CREATE));
            r.put("newPath", !canCreate ? null : screen == Screen.BOOKING ? screen.path() : screen.path() + "?new=1");
            rows.add(r);
        });
        return rows;
    }

    /** The module's documents most recently changed. */
    private List<Map<String, Object>> recent(List<DocumentType> types) {
        if (types.isEmpty()) return List.of();
        return jdbc.queryForList("""
            SELECT d.id, d.document_no, d.document_type, d.status, d.document_date, d.updated_at, d.updated_by, d.created_by,
                   pty.name AS party
            FROM gbl_business_documents d
            LEFT JOIN pty_parties pty ON pty.id = d.party_id
            WHERE """ + VISIBLE + """
            ORDER BY COALESCE(d.updated_at, d.created_at) DESC NULLS LAST, d.id DESC
            LIMIT 12
            """, visible(types)).stream().map(r -> {
            Map<String, Object> m = ProductionBoardService.camel(r);
            DocumentType type = DocumentType.valueOf((String) r.get("document_type"));
            m.put("typeLabel", type.label());
            m.put("path", openPath(screenOf(type).orElseThrow(), r.get("id")));
            return m;
        }).toList();
    }

    /** Documents raised each week over the last twelve. */
    private List<Map<String, Object>> activity(List<DocumentType> types) {
        LocalDate since = LocalDate.now().minusWeeks(11).with(java.time.DayOfWeek.MONDAY);
        Map<LocalDate, Long> weeks = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) weeks.put(since.plusWeeks(i), 0L);
        if (!types.isEmpty()) {
            jdbc.query("""
                SELECT date_trunc('week', d.document_date)::date AS week, count(*) AS n
                FROM gbl_business_documents d
                WHERE """ + VISIBLE + """
                  AND d.document_date >= :since
                GROUP BY 1
                """, visible(types).addValue("since", since), rs -> {
                LocalDate week = rs.getDate(1).toLocalDate();
                if (weeks.containsKey(week)) weeks.put(week, rs.getLong(2));
            });
        }
        return weeks.entrySet().stream().map(e -> Map.<String, Object>of("week", e.getKey().toString(), "documents", e.getValue())).toList();
    }

    /** Documents the user may see, as the lists show them: this unit, their stores and teams. */
    private static final String VISIBLE = " " + """
        d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false AND d.document_type IN (:types)
              AND (:allTeams OR d.marketing_team_id IN (:teams))
              AND (:allStores OR d.warehouse_id IS NULL OR d.warehouse_id IN (:stores) OR d.to_warehouse_id IN (:stores))
        """;

    private MapSqlParameterSource visible(List<DocumentType> types) {
        RowScope scope = context.requireRowScope();
        return new MapSqlParameterSource("org", context.requireOrganizationId())
            .addValue("unit", context.requireBusinessUnitId())
            .addValue("types", types.stream().map(Enum::name).toList())
            .addValue("allTeams", !scope.restricts(ScopeDimension.MARKETING_TEAM))
            .addValue("teams", ids(scope, ScopeDimension.MARKETING_TEAM))
            .addValue("allStores", !scope.restricts(ScopeDimension.WAREHOUSE))
            .addValue("stores", ids(scope, ScopeDimension.WAREHOUSE));
    }

    /** A SQL IN list may not be empty; no row has id -1. */
    private static List<Long> ids(RowScope scope, ScopeDimension dimension) {
        List<Long> ids = scope.idsForQuery(dimension);
        return ids.isEmpty() ? List.of(-1L) : ids;
    }
}

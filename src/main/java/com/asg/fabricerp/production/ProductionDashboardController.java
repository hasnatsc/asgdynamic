package com.asg.fabricerp.production;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Analytics & reports -> Production dashboard. The page, its data, the documents behind each
 * figure, and the lists its filters offer. Read-only: every figure opens the documents it came from.
 */
@Controller
public class ProductionDashboardController {

    private final ProductionDashboardService dashboard;
    private final ProductionBoardService boards;
    private final NamedParameterJdbcTemplate jdbc;

    public ProductionDashboardController(ProductionDashboardService dashboard, ProductionBoardService boards,
                                         NamedParameterJdbcTemplate jdbc) {
        this.dashboard = dashboard;
        this.boards = boards;
        this.jdbc = jdbc;
    }

    @GetMapping("/production/dashboard")
    @PreAuthorize("hasAuthority('SCREEN_PROD_DASHBOARD_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Production dashboard");
        model.addAttribute("statuses", List.of(BusinessDocumentStatus.DRAFT, BusinessDocumentStatus.SUBMITTED,
            BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL,
            BusinessDocumentStatus.COMPLETED, BusinessDocumentStatus.CLOSED));
        model.addAttribute("steps", Arrays.stream(ChainStep.values())
            .map(s -> Map.of("key", s.name(), "label", s.label(), "slug", s.slug())).toList());
        model.addAttribute("content", "production/dashboard :: content");
        return "layout/main";
    }

    @GetMapping("/api/production/dashboard")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROD_DASHBOARD_VIEW')")
    public Map<String, Object> data(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) String fabricType,
                                    @RequestParam(required = false) String color,
                                    @RequestParam(required = false) Long buyerId,
                                    @RequestParam(required = false) Long bpoId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String month) {
        return dashboard.dashboard(filter(from, to, fabricType, color, buyerId, bpoId, status), month(month));
    }

    /** The documents behind a pipeline stage or KPI. */
    @GetMapping("/api/production/dashboard/documents")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROD_DASHBOARD_VIEW')")
    public List<Map<String, Object>> documents(@RequestParam String step,
                                               @RequestParam(required = false) String group,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) String fabricType,
                                               @RequestParam(required = false) String color,
                                               @RequestParam(required = false) Long buyerId,
                                               @RequestParam(required = false) Long bpoId,
                                               @RequestParam(required = false) String status) {
        if ("BOOKING".equals(step)) return dashboard.bookingDocuments(filter(from, to, fabricType, color, buyerId, bpoId, status));
        ChainStep s = Arrays.stream(ChainStep.values()).filter(v -> v.name().equals(step)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No such step: " + step));
        return dashboard.documents(filter(from, to, fabricType, color, buyerId, bpoId, status), s, group);
    }

    /** The fabric types and colours the unit's production orders use, for the filters. */
    @GetMapping("/api/production/dashboard/options")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROD_DASHBOARD_VIEW')")
    public Map<String, Object> options() {
        MapSqlParameterSource p = boards.scopeParams();
        String from = """
            FROM gbl_business_documents d
            JOIN gbl_business_document_line_groups g ON g.document_id = d.id
            JOIN gbl_business_document_color_lines cl ON cl.line_group_id = g.id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.document_type = 'BULK_PRODUCTION_ORDER' AND d.status NOT IN ('CANCELLED', 'REJECTED')
              AND (:allTeams OR d.marketing_team_id IN (:teams))
            """;
        return Map.of(
            "fabricTypes", jdbc.queryForList("SELECT DISTINCT g.fabric_type " + from
                + " AND g.fabric_type IS NOT NULL ORDER BY 1", p, String.class),
            "colours", jdbc.queryForList("SELECT DISTINCT cl.color_name " + from
                + " AND cl.color_name IS NOT NULL ORDER BY 1 LIMIT 1000", p, String.class));
    }

    /** Production orders for the order filter, newest first (the RemoteSelect lookup contract). */
    @GetMapping("/api/production/dashboard/orders")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROD_DASHBOARD_VIEW')")
    public LookupPage<LookupPage.Option> orders(@RequestParam(required = false) String q, @RequestParam(required = false) Long id,
                                      @RequestParam(required = false) Integer page) {
        int size = 20, offset = page == null || page < 1 ? 0 : (page - 1) * size;
        MapSqlParameterSource p = boards.scopeParams().addValue("q", ProductionBoardService.like(q)).addValue("id", id)
            .addValue("limit", size + 1).addValue("offset", offset);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT d.id, d.document_no, d.status, pty.name AS buyer
            FROM gbl_business_documents d LEFT JOIN pty_parties pty ON pty.id = d.party_id
            WHERE d.organization_id = :org AND d.business_unit_id = :unit AND d.deleted = false
              AND d.document_type = 'BULK_PRODUCTION_ORDER' AND d.status NOT IN ('CANCELLED')
              AND (:allTeams OR d.marketing_team_id IN (:teams))
              AND (CAST(:id AS BIGINT) IS NULL OR d.id = :id)
              AND (:q = '%' OR lower(d.document_no) LIKE :q OR lower(COALESCE(pty.name, '')) LIKE :q)
            ORDER BY d.document_date DESC, d.id DESC LIMIT :limit OFFSET :offset
            """, p);
        boolean more = rows.size() > size;
        return LookupPage.of(rows.stream().limit(size).map(r -> new LookupPage.Option(((Number) r.get("id")).longValue(),
            null, (String) r.get("document_no"), (String) r.get("buyer"))).toList(), more);
    }

    private static ProductionDashboardService.Filter filter(LocalDate from, LocalDate to, String fabricType, String color,
                                                            Long buyerId, Long bpoId, String status) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("The date range ends before it starts");
        return new ProductionDashboardService.Filter(from, to, fabricType, color, buyerId, bpoId, status);
    }

    private static YearMonth month(String month) {
        if (month == null || month.isBlank()) return null;
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("A month is written 2026-09");
        }
    }
}

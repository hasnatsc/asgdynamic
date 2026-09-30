package com.asg.fabricerp.production;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.report.ReportService;
import net.sf.jasperreports.engine.JasperPrint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The Fabrics production report: the screen, its data, and its printout - the same rows, filtered
 * and sorted the same way, printed through JasperReports ({@code reports/fabrics-production-report.jrxml})
 * as a PDF or an Excel workbook.
 */
@Controller
@PreAuthorize("hasAuthority('SCREEN_PROD_REPORT_VIEW')")
public class ProductionReportController {

    static final String TEMPLATE = "fabrics-production-report";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ProductionReportService report;
    private final ReportService reports;

    public ProductionReportController(ProductionReportService report, ReportService reports) {
        this.report = report;
        this.reports = reports;
    }

    @GetMapping("/production/report")
    public String page(Model model) {
        model.addAttribute("title", "Fabrics production report");
        model.addAttribute("dueSoonDays", ProductionReportService.DUE_SOON_DAYS);
        model.addAttribute("printLimit", ProductionReportService.PRINT_LIMIT);
        model.addAttribute("content", "production/report :: content");
        return "layout/main";
    }

    @GetMapping("/api/production/report")
    @ResponseBody
    public ProductionReportService.Page data(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long personId,
                                             @RequestParam(required = false) Long garmentsId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return report.report(new ProductionReportService.Filter(q, personId, garmentsId, from, to, null), sort, dir,
            page, Math.min(Math.max(size, 1), 200));
    }

    /** One order's colour lines, stage by stage: its row's breakdown. */
    @GetMapping("/api/production/report/{bpoId:\\d+}/lines")
    @ResponseBody
    public List<Map<String, Object>> lines(@PathVariable Long bpoId) {
        return report.lines(bpoId);
    }

    @GetMapping("/api/production/report/marketing-persons")
    @ResponseBody
    public LookupPage<LookupPage.Option> marketingPersons(@RequestParam(required = false) String q, @RequestParam(required = false) Long id) {
        return report.marketingPersons(q, id);
    }

    @GetMapping("/api/production/report/garments")
    @ResponseBody
    public LookupPage<LookupPage.Option> garments(@RequestParam(required = false) String q, @RequestParam(required = false) Long id) {
        return report.garments(q, id);
    }

    /** The report as the screen is filtered and sorted - every matching order, not one page - as PDF or Excel. */
    @GetMapping("/production/report/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "pdf") String format,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Long personId,
                                         @RequestParam(required = false) Long garmentsId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long bpoId,
                                         @RequestParam(required = false) String sort, @RequestParam(required = false) String dir) {
        boolean excel = "xlsx".equalsIgnoreCase(format);
        var filter = new ProductionReportService.Filter(q, personId, garmentsId, from, to, bpoId);
        ProductionReportService.Page found = report.all(filter, sort, dir);

        Map<String, Object> params = new HashMap<>(kpis(found.totals()));
        params.put("filter_line", filterLine(filter, found));
        params.put("for_excel", excel);
        JasperPrint print = reports.fill(TEMPLATE, params, found.rows().stream().map(ProductionReportController::printRow).toList());

        String name = "fabrics-production-report-" + LocalDate.now() + (excel ? ".xlsx" : ".pdf");
        return excel ? ReportService.response(ReportService.xlsx(print), ReportService.XLSX, name)
                     : ReportService.response(ReportService.pdf(print), MediaType.APPLICATION_PDF, name);
    }

    // --------------------------------------------------------------------------------- helpers

    /** A screen row as the template's fields want it: dates printed, the rest as they are. */
    static Map<String, Object> printRow(Map<String, Object> row) {
        Map<String, Object> r = new HashMap<>(row);
        r.put("bpoDate", date(row.get("bpoDate")));
        r.put("dueDate", date(row.get("dueDate")));
        return r;
    }

    static Map<String, Object> kpis(Map<String, Object> t) {
        DecimalFormat n = new DecimalFormat("#,##0");
        Map<String, Object> p = new HashMap<>();
        p.put("kpi_orders", n.format(num(t.get("orders"))));
        p.put("kpi_lc", n.format(num(t.get("lcQuantity"))));
        p.put("kpi_production", n.format(num(t.get("inProduction"))));
        p.put("kpi_finished", n.format(num(t.get("finished"))));
        p.put("kpi_delivered", n.format(num(t.get("delivered"))));
        p.put("kpi_pending", n.format(num(t.get("pending"))));
        p.put("kpi_uom", t.get("uom"));
        return p;
    }

    /** What the printout was filtered by, in words - a report handed on must say what it covers. */
    private String filterLine(ProductionReportService.Filter f, ProductionReportService.Page found) {
        List<String> parts = new ArrayList<>();
        if (f.bpoId() != null && !found.rows().isEmpty()) parts.add("Production order " + found.rows().get(0).get("bpoNo"));
        if (f.q() != null && !f.q().isBlank()) parts.add("Search “" + f.q().strip() + "”");
        if (f.marketingPersonId() != null) parts.add("Marketing person: " + label(report.marketingPersons(null, f.marketingPersonId())));
        if (f.garmentsId() != null) parts.add("Garments: " + label(report.garments(null, f.garmentsId())));
        if (f.from() != null || f.to() != null) {
            parts.add("BPO date " + (f.from() == null ? "…" : f.from().format(DATE)) + " to " + (f.to() == null ? "…" : f.to().format(DATE)));
        }
        String what = parts.isEmpty() ? "All production orders" : String.join(" · ", parts);
        long shown = found.rows().size();
        return found.total() > shown
            ? "%s · first %,d of %,d orders".formatted(what, shown, found.total())
            : "%s · %,d %s".formatted(what, shown, shown == 1 ? "order" : "orders");
    }

    private static String label(LookupPage<LookupPage.Option> page) {
        return page.results().isEmpty() ? "—" : page.results().get(0).text();
    }

    private static String date(Object v) {
        return v instanceof LocalDate d ? d.format(DATE) : null;
    }

    private static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}

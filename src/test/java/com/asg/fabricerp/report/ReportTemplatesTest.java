package com.asg.fabricerp.report;

import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every printed report's template compiles, fills and exports - so a template broken by an edit
 * (or by Jaspersoft Studio) fails the build, not the first person who presses Export.
 */
class ReportTemplatesTest {

    @Test
    void theFabricsProductionReportPrintsToPdfAndExcel() throws Exception {
        List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(row("BPOAF000058", 100, 100, 80, 60, 60, 40, 70, "24-09-2026", "On time", "ON_TIME"));
        rows.add(row("BPOAF000057", 100, 100, 65, 40, 40, 20, 45, "23-09-2026", "2 days overdue", "OVERDUE"));
        Map<String, Object> greigeOnly = row("BPOAF000056", 90, 90, null, 20, null, null, 10, null, "No due date", "NO_DUE_DATE");
        rows.add(greigeOnly);

        Map<String, Object> params = new HashMap<>();
        params.put("company_name", "ASG Dynamic");
        params.put("unit_name", "Amanatshah Fabrics");
        params.put("printed_by", "tester");
        params.put("printed_at", "30-09-2026 10:00");
        params.put("filter_line", "Marketing person: Raihan · BPO date 01-01-2026 to 30-09-2026");
        params.put("kpi_orders", "3");
        params.put("kpi_lc", "2,485,600");
        params.put("kpi_production", "1,245,600");
        params.put("kpi_finished", "892,450");
        params.put("kpi_delivered", "706,300");
        params.put("kpi_pending", "1,779,300");
        params.put("kpi_uom", "m");

        JasperPrint pdfPrint = JasperFillManager.fillReport(ReportService.compile("fabrics-production-report"), params,
            new JRMapCollectionDataSource(rows));
        byte[] pdf = ReportService.pdf(pdfPrint);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdfPrint.getPages()).hasSize(1);

        Map<String, Object> excel = new HashMap<>(params);
        excel.put("for_excel", Boolean.TRUE);
        byte[] xlsx = ReportService.xlsx(JasperFillManager.fillReport(ReportService.compile("fabrics-production-report"), excel,
            new JRMapCollectionDataSource(rows)));
        assertThat(xlsx[0]).isEqualTo((byte) 'P');   // a zip: PK
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }

    @Test
    void anEmptyReportStillPrintsItsHeader() throws Exception {
        JasperPrint print = JasperFillManager.fillReport(ReportService.compile("fabrics-production-report"),
            new HashMap<>(Map.of("kpi_orders", "0")), new JRMapCollectionDataSource(List.of()));
        assertThat(print.getPages()).hasSize(1);
    }

    private static Map<String, Object> row(String bpo, Integer lc, Integer weaving, Integer processing, Integer greige,
                                           Integer issued, Integer finished, Integer delivery, String due, String dueText, String state) {
        Map<String, Object> r = new HashMap<>();
        r.put("bpoNo", bpo);
        r.put("bpoDate", "03-06-2026");
        r.put("marketingPerson", "Raihan");
        r.put("garments", "SF Fashion Wear Ltd.");
        r.put("dispoNo", "DP-D-4101 B");
        r.put("buyer", "Buyer Ltd");
        r.put("uom", "m");
        stage(r, "lc", lc);
        stage(r, "weaving", weaving);
        stage(r, "processing", processing);
        stage(r, "greigeReceived", greige);
        stage(r, "greigeIssued", issued);
        stage(r, "finished", finished);
        stage(r, "delivery", delivery);
        r.put("dueDate", due);
        r.put("dueText", dueText);
        r.put("dueState", state);
        return r;
    }

    private static void stage(Map<String, Object> r, String key, Integer pct) {
        r.put(key + "Pct", pct);
        r.put(key + "Of", pct == null ? null : new BigDecimal("18650"));
        r.put(key + "Done", pct == null ? null : new BigDecimal(18650L * pct / 100));
    }
}

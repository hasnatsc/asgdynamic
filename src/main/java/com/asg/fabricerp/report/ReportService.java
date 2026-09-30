package com.asg.fabricerp.report;

import com.asg.fabricerp.common.OrgContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The JasperReports engine for printed reports - HASSML's {@code ReportPdfService}, on this system.
 *
 * <p>Templates live in {@code src/main/resources/reports/<name>.jrxml}, are pre-compiled to
 * {@code <name>.jasper} (see {@link #load}) and loaded once, on first use. Every template is filled
 * from rows the caller has already read and scoped (a
 * {@link JRMapCollectionDataSource}), never from SQL of its own: a report can then never show a
 * row its screen would not, and the figures are the screen's own.
 *
 * <p>Every template receives the same header parameters - {@code company_name}, {@code unit_name},
 * {@code logo} (a URL to the classpath logo), {@code printed_by}, {@code printed_at} - plus its own.
 *
 * <p><b>Templates must use DejaVu Sans</b>, the one family jasperreports-fonts registers: any other
 * font fills fine and then fails at PDF export (HASSML learned this the hard way). Jaspersoft Studio
 * strips XML comments on save, so the rule is written here rather than in the templates.
 */
@Service
public class ReportService {

    private static final String FOLDER = "reports/";
    private static final String LOGO = "static/images/logo.png";
    private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final JdbcTemplate jdbc;
    private final OrgContext context;
    private final Map<String, JasperReport> compiled = new ConcurrentHashMap<>();

    public ReportService(JdbcTemplate jdbc, OrgContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    /** The template {@code reports/<name>.jasper}, loaded once. */
    public JasperReport template(String name) {
        return compiled.computeIfAbsent(name, ReportService::load);
    }

    /**
     * Loads the pre-compiled {@code .jasper} binary rather than compiling the {@code .jrxml} source
     * at runtime: JasperReports' runtime compiler shells out to javac against {@code java.class.path},
     * which doesn't resolve the engine's own classes when running from a Spring Boot fat jar (nested
     * jars under {@code BOOT-INF/lib/} aren't plain files on that classpath). Compile the {@code .jrxml}
     * to {@code .jasper} in Jaspersoft Studio (or {@code JasperCompileManager} offline) after every edit.
     */
    static JasperReport load(String name) {
        try (InputStream in = new ClassPathResource(FOLDER + name + ".jasper").getInputStream()) {
            return (JasperReport) JRLoader.loadObject(in);
        } catch (IOException | JRException e) {
            throw new IllegalStateException("Report template " + name + " could not be loaded: " + e.getMessage(), e);
        }
    }

    /** Fills a template with rows and parameters (the header parameters are added here). */
    public JasperPrint fill(String name, Map<String, Object> params, Collection<? extends Map<String, ?>> rows) {
        Map<String, Object> all = new HashMap<>(header());
        all.putAll(params);
        try {
            @SuppressWarnings("unchecked")
            Collection<Map<String, ?>> data = (Collection<Map<String, ?>>) rows;
            return JasperFillManager.fillReport(template(name), all, new JRMapCollectionDataSource(data));
        } catch (JRException e) {
            throw new IllegalStateException("Report " + name + " could not be filled: " + e.getMessage(), e);
        }
    }

    public static byte[] pdf(JasperPrint print) {
        try {
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new IllegalStateException("The report could not be exported to PDF: " + e.getMessage(), e);
        }
    }

    /** One sheet per report, cells detected as numbers, no page breaks or blank rows between pages. */
    public static byte[] xlsx(JasperPrint print) {
        JRXlsxExporter exporter = new JRXlsxExporter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
        config.setOnePagePerSheet(false);
        config.setDetectCellType(true);
        config.setRemoveEmptySpaceBetweenRows(true);
        config.setRemoveEmptySpaceBetweenColumns(true);
        config.setWhitePageBackground(false);
        config.setIgnorePageMargins(true);
        exporter.setConfiguration(config);
        try {
            exporter.exportReport();
        } catch (JRException e) {
            throw new IllegalStateException("The report could not be exported to Excel: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /** The file, inline for a PDF (it opens in the browser's viewer), as a download for anything else. */
    public static ResponseEntity<byte[]> response(byte[] body, MediaType type, String filename) {
        String safe = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        ContentDisposition disposition = (MediaType.APPLICATION_PDF.equals(type) ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(safe).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(disposition);
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /** The header every template prints: whose report, for which unit, who printed it and when. */
    Map<String, Object> header() {
        Map<String, Object> p = new HashMap<>();
        Long org = context.organizationId();
        Long unit = context.businessUnitId();
        p.put("company_name", org == null ? null : first(jdbc.queryForList(
            "SELECT name FROM org_organizations WHERE id = ?", String.class, org)));
        p.put("unit_name", unit == null ? null : first(jdbc.queryForList(
            "SELECT name FROM org_business_units WHERE id = ?", String.class, unit)));
        p.put("printed_by", context.username() == null ? "—" : context.username());
        p.put("printed_at", LocalDateTime.now().format(PRINTED));
        p.put("logo", logo());
        return p;
    }

    private static URL logo() {
        try {
            return new ClassPathResource(LOGO).getURL();
        } catch (IOException missing) {
            return null;   // the templates print a blank logo rather than fail
        }
    }

    private static String first(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }
}

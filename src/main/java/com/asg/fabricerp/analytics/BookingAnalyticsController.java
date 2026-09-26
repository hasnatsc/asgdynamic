package com.asg.fabricerp.analytics;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.global.documents.BookingType;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Booking analytics & reports - the first screen of the Analytics & reports area.
 *
 * <pre>
 *   GET /analytics/booking                          the page
 *   GET /api/analytics/booking/overview             tiles, trend and breakdowns for the filters
 *   GET /api/analytics/booking/reports/{report}     one summary report (columns, rows, totals)
 *   GET /api/analytics/booking/register             the booking register, paged (DataTables)
 *   GET /api/analytics/booking/register.csv         the booking register, every row, as CSV
 *   GET /api/analytics/booking/persons              marketing-person filter feed (LookupPage)
 * </pre>
 *
 * Every endpoint takes the same filters - {@code from, to, view, teamId, personId, buyerId,
 * currency, status, bookingType, q} - and every one narrows them to the signed-in user's
 * {@link AnalyticsScope}: a team member's own bookings, a supervisor's or approver's teams, or
 * every team for management. VIEW on the screen is all it takes to open it; what it shows is
 * decided by who is looking.
 */
@Controller
@PreAuthorize("hasAuthority('SCREEN_BOOKING_ANALYTICS_VIEW')")
public class BookingAnalyticsController {

    private final BookingAnalyticsService service;

    public BookingAnalyticsController(BookingAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/analytics/booking")
    public String page(Model model) {
        model.addAttribute("title", "Booking analytics");
        model.addAttribute("reports", Arrays.stream(BookingAnalyticsService.Report.values())
            .map(r -> Map.of("key", r.name(), "title", r.title())).toList());
        model.addAttribute("bookingTypes", BookingType.values());
        model.addAttribute("content", "analytics/booking :: content");
        return "layout/main";
    }

    @GetMapping("/api/analytics/booking/overview")
    @ResponseBody
    public Map<String, Object> overview(@ModelAttribute Filters f) {
        return service.overview(f.request());
    }

    @GetMapping("/api/analytics/booking/reports/{report}")
    @ResponseBody
    public Map<String, Object> report(@PathVariable String report, @ModelAttribute Filters f) {
        BookingAnalyticsService.Report which;
        try {
            which = BookingAnalyticsService.Report.valueOf(report.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown report " + report);
        }
        return service.report(which, f.request());
    }

    @GetMapping("/api/analytics/booking/register")
    @ResponseBody
    public DataTableResponse<Map<String, Object>> register(@ModelAttribute Filters f,
                                                          @RequestParam(defaultValue = "1") int draw,
                                                          @RequestParam(defaultValue = "0") int start,
                                                          @RequestParam(defaultValue = "25") int length,
                                                          @RequestParam(required = false) String sortColumn,
                                                          @RequestParam(required = false) String sortDir) {
        return service.register(f.request(), draw, start, length, sortColumn, sortDir);
    }

    @GetMapping("/api/analytics/booking/register.csv")
    public void registerCsv(@ModelAttribute Filters f, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"booking-register-" + LocalDate.now() + ".csv\"");
        Writer out = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        service.registerCsv(f.request(), out);
        out.flush();
    }

    @GetMapping("/api/analytics/booking/persons")
    @ResponseBody
    public LookupPage<LookupPage.Option> persons(@ModelAttribute Filters f,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return service.persons(f.request(), q, page, size);
    }

    /** The shared filter parameters, bound from the query string. */
    public static class Filters {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate from;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate to;
        private AnalyticsView view;
        private Long teamId;
        private Long personId;
        private Long buyerId;
        private String currency;
        private String status;
        private String bookingType;
        private String search;

        BookingAnalyticsService.Request request() {
            return new BookingAnalyticsService.Request(from, to, view, teamId, personId, buyerId, currency, status,
                bookingType, search);
        }

        public void setFrom(LocalDate v)       { this.from = v; }
        public void setTo(LocalDate v)         { this.to = v; }
        public void setView(AnalyticsView v)   { this.view = v; }
        public void setTeamId(Long v)          { this.teamId = v; }
        public void setPersonId(Long v)        { this.personId = v; }
        public void setBuyerId(Long v)         { this.buyerId = v; }
        public void setCurrency(String v)      { this.currency = v; }
        public void setStatus(String v)        { this.status = v; }
        public void setBookingType(String v)   { this.bookingType = v; }
        public void setSearch(String v)        { this.search = v; }
        public LocalDate getFrom()             { return from; }
        public LocalDate getTo()               { return to; }
        public AnalyticsView getView()         { return view; }
        public Long getTeamId()                { return teamId; }
        public Long getPersonId()              { return personId; }
        public Long getBuyerId()               { return buyerId; }
        public String getCurrency()            { return currency; }
        public String getStatus()              { return status; }
        public String getBookingType()         { return bookingType; }
        public String getSearch()              { return search; }
    }
}

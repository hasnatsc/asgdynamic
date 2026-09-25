package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.costing.CostingCatalog;
import com.asg.fabricerp.global.documents.BookingType;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.OrderType;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Page and grid are separate routes:
 *
 * <pre>
 *   GET  /booking                       Thymeleaf page
 *   GET  /api/booking                   grid rows
 *   GET  /api/booking/{id}              one booking, for the drawer and the editor
 *   POST /api/booking                   create or update
 *   GET  /api/booking/costing/{code}    what a costing number fills in (server calls costing)
 *   GET  /api/booking/marketing-persons picker feed
 * </pre>
 *
 * asgdynamic served both from {@code /booking/index}, discriminating on a
 * {@code conditionParams} request parameter, and called the costing API straight from the
 * page with its credentials in the source.
 *
 * <p>Authorization is declared here rather than in a separate URL-to-role table. In the
 * legacy system that table drifted: 14 URLs stayed permitted for controllers that had been
 * deleted.
 */
@Controller
public class BookingController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo",      "documentNo",
        "documentDate",    "documentDate",
        "requiredDate",    "requiredDate",
        "status",          "status",
        "totalQuantity",   "totalQuantity",
        "subtotalAmount",  "subtotalAmount",
        "revisionNo",      "revisionNo"
    ));

    /** The legacy currency list. */
    static final List<String> CURRENCIES = List.of("USD", "BDT", "EUR", "AUD");
    static final List<String> FABRIC_SOURCES = List.of("In-house", "Export");
    static final List<String> LIGHT_SOURCE_TYPES = List.of("Primary", "Secondary");
    static final List<String> BASE_MATERIALS = List.of("As per Swatch", "As per Specification");

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/booking")
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Booking");
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        model.addAttribute("draft", BusinessDocumentStatus.DRAFT);
        model.addAttribute("bookingTypes", BookingType.values());
        model.addAttribute("orderTypes", OrderType.values());
        model.addAttribute("currencies", CURRENCIES);
        model.addAttribute("fabricSources", FABRIC_SOURCES);
        model.addAttribute("singleColourFabricTypes", ColourStructure.SINGLE_COLOUR_TYPES);
        model.addAttribute("lightSourceTypes", LIGHT_SOURCE_TYPES);
        model.addAttribute("baseMaterials", BASE_MATERIALS);
        model.addAttribute("lcTenures", CostingCatalog.LC_TENURES);
        model.addAttribute("lcPaymentTypes", CostingCatalog.LC_PAYMENT_TYPES);
        // The Marketing team field: fixed to a restricted user's own team, a choice otherwise.
        model.addAttribute("teamRestricted", service.teamRestricted());
        model.addAttribute("ownTeam", service.ownTeam());
        model.addAttribute("content", "fabric/booking :: content");
        return "layout/main";
    }

    @GetMapping("/api/booking")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) BusinessDocumentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<Map<String, Object>> page = service.searchRows(
            status, from, to, request.searchOrNull(),
            request.toPageable(SORTABLE, "documentDate"));

        return DataTableResponse.from(draw, page, row -> row);
    }

    @GetMapping("/api/booking/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/booking")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_CREATE') or hasAuthority('SCREEN_BOOKING_AMEND')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument booking) {
        AuthorityChecks.require(booking.getId() == null ? "SCREEN_BOOKING_CREATE" : "SCREEN_BOOKING_AMEND");
        return service.detail(service.save(booking).getId());
    }

    /**
     * The costing lookup, server-side. {@code bookingId} is the booking being edited, so the
     * "already booked" warning does not name the booking itself.
     */
    @GetMapping("/api/booking/costing/{code}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_CREATE') or hasAuthority('SCREEN_BOOKING_AMEND')")
    public CostingPrefill costing(@PathVariable String code,
                                  @RequestParam(required = false) Long bookingId) {
        return service.costingPrefill(code, bookingId);
    }

    @GetMapping("/api/booking/marketing-persons")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_VIEW')")
    public LookupPage<LookupPage.Option> marketingPersons(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size,
                                                          @RequestParam(required = false) Long id) {
        return service.marketingPersons(q, page, size, id);
    }

    // Submit/approve/reject are the same action for every document type — see
    // /api/documents/{id}/submit|approve|reject in ApprovalController rather than a
    // per-type route here.

    @PostMapping("/api/booking/{id}/revise")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_AMEND')")
    public Map<String, Object> revise(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        return service.detail(service.revise(id, reason).getId());
    }

    @DeleteMapping("/api/booking/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BOOKING_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }
}

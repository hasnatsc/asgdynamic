package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentLine;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Page and grid are separate routes:
 *
 * <pre>
 *   GET  /booking          Thymeleaf page
 *   GET  /api/booking      grid rows
 *   POST /api/booking      create or update
 * </pre>
 *
 * asgdynamic served both from {@code /booking/index}, discriminating on a
 * {@code conditionParams} request parameter.
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
        "status",          "status",
        "totalQuantity",   "totalQuantity",
        "subtotalAmount",  "subtotalAmount",
        "revisionNo",      "revisionNo"
    ));

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/booking")
    @PreAuthorize("hasAnyRole('BOOKING_VIEW', 'BOOKING_MAKER', 'SALES')")
    public String page(Model model) {
        model.addAttribute("title", "Booking");
        model.addAttribute("booking", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        return "fabric/booking";
    }

    @GetMapping("/api/booking")
    @ResponseBody
    @PreAuthorize("hasAnyRole('BOOKING_VIEW', 'BOOKING_MAKER', 'SALES')")
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
        Page<BusinessDocument> page = service.search(
            status, from, to, request.searchOrNull(),
            request.toPageable(SORTABLE, "documentDate"));

        return DataTableResponse.from(draw, page, BookingController::toRow);
    }

    @GetMapping("/api/booking/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('BOOKING_VIEW', 'BOOKING_MAKER', 'SALES')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    @PostMapping("/api/booking")
    @ResponseBody
    @PreAuthorize("hasRole('BOOKING_MAKER')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument booking) {
        return toDetail(service.save(booking));
    }

    // Submit/approve/reject are the same action for every document type — see
    // /api/documents/{id}/submit|approve|reject in ApprovalController rather than a
    // per-type route here.

    @PostMapping("/api/booking/{id}/revise")
    @ResponseBody
    @PreAuthorize("hasRole('BOOKING_MAKER')")
    public Map<String, Object> revise(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        return toDetail(service.revise(id, reason));
    }

    @DeleteMapping("/api/booking/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('BOOKING_MAKER')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("documentDate", d.getDocumentDate() == null ? "" : d.getDocumentDate().toString());
        row.put("referenceNo", d.getReferenceNo() == null ? "" : d.getReferenceNo());
        row.put("currency", d.getCurrencyCode());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    private static Map<String, Object> toDetail(BusinessDocument d) {
        Map<String, Object> detail = new LinkedHashMap<>(toRow(d));
        detail.put("partyId", d.getPartyId());
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lines", d.getLines().stream().map(BookingController::toLine).toList());
        return detail;
    }

    private static Map<String, Object> toLine(BusinessDocumentLine l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("lineNo", l.getLineNo());
        row.put("costingCode", l.getFabric().getCostingCode());
        row.put("construction", l.getFabric().getConstruction());
        row.put("weaveType", l.getFabric().getWeaveType());
        row.put("weaveStyle", l.getFabric().getWeaveStyle());
        row.put("finishType", l.getFabric().getFinishType());
        row.put("gsm", l.getFabric().getGsm());
        row.put("colourName", l.getFabric().getColourName());
        row.put("quantity", l.getQuantity());
        row.put("rate", l.getRate());
        row.put("lineAmount", l.getLineAmount());
        row.put("fulfilled", l.getFulfilledQuantity());
        row.put("outstanding", l.outstandingQuantity());
        return row;
    }
}

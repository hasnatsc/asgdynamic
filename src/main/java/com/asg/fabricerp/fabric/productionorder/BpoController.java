package com.asg.fabricerp.fabric.productionorder;

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
import java.util.List;
import java.util.Map;

/**
 * Same page/grid split as {@code BookingController}; the one addition is
 * {@code /api/bpo/booking-lines/{bookingId}}, which the "raise BPO" form calls to populate
 * its line picker with what each Booking line still has outstanding.
 */
@Controller
public class BpoController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo",     "documentNo",
        "documentDate",   "documentDate",
        "status",         "status",
        "totalQuantity",  "totalQuantity",
        "subtotalAmount", "subtotalAmount",
        "revisionNo",     "revisionNo"
    ));

    private final BpoService service;

    public BpoController(BpoService service) {
        this.service = service;
    }

    @GetMapping("/bpo")
    @PreAuthorize("hasAnyRole('BPO_VIEW', 'BPO_MAKER', 'PRODUCTION')")
    public String page(Model model) {
        model.addAttribute("title", "Bulk Production Order");
        model.addAttribute("bpo", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        return "fabric/bpo";
    }

    @GetMapping("/api/bpo")
    @ResponseBody
    @PreAuthorize("hasAnyRole('BPO_VIEW', 'BPO_MAKER', 'PRODUCTION')")
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

        return DataTableResponse.from(draw, page, BpoController::toRow);
    }

    @GetMapping("/api/bpo/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('BPO_VIEW', 'BPO_MAKER', 'PRODUCTION')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    /** What the "raise BPO" line picker offers: Booking lines with something left to draw. */
    @GetMapping("/api/bpo/booking-lines/{bookingId}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('BPO_VIEW', 'BPO_MAKER', 'PRODUCTION')")
    public List<Map<String, Object>> openBookingLines(@PathVariable Long bookingId) {
        return service.openBookingLines(bookingId).stream()
            .map(BpoController::toSourceOption)
            .toList();
    }

    @PostMapping("/api/bpo")
    @ResponseBody
    @PreAuthorize("hasRole('BPO_MAKER')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument bpo) {
        return toDetail(service.save(bpo));
    }

    @PostMapping("/api/bpo/{id}/submit")
    @ResponseBody
    @PreAuthorize("hasRole('BPO_MAKER')")
    public Map<String, Object> submit(@PathVariable Long id) {
        return toDetail(service.submit(id));
    }

    @PostMapping("/api/bpo/{id}/revise")
    @ResponseBody
    @PreAuthorize("hasRole('BPO_MAKER')")
    public Map<String, Object> revise(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        return toDetail(service.revise(id, reason));
    }

    @DeleteMapping("/api/bpo/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('BPO_MAKER')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("bookingId", d.getParentDocumentId());
        row.put("documentDate", d.getDocumentDate() == null ? "" : d.getDocumentDate().toString());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    private static Map<String, Object> toDetail(BusinessDocument d) {
        Map<String, Object> detail = new LinkedHashMap<>(toRow(d));
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lines", d.getLines().stream().map(BpoController::toLine).toList());
        return detail;
    }

    private static Map<String, Object> toLine(BusinessDocumentLine l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("lineNo", l.getLineNo());
        row.put("sourceLineId", l.getSourceLineId());
        row.put("costingCode", l.getFabric().getCostingCode());
        row.put("construction", l.getFabric().getConstruction());
        row.put("weaveType", l.getFabric().getWeaveType());
        row.put("finishType", l.getFabric().getFinishType());
        row.put("gsm", l.getFabric().getGsm());
        row.put("colourName", l.getFabric().getColourName());
        row.put("quantity", l.getQuantity());
        row.put("rate", l.getRate());
        row.put("lineAmount", l.getLineAmount());
        return row;
    }

    private static Map<String, Object> toSourceOption(BusinessDocumentLine bookingLine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceLineId", bookingLine.getId());
        row.put("construction", bookingLine.getFabric().getConstruction());
        row.put("colourName", bookingLine.getFabric().getColourName());
        row.put("orderedQuantity", bookingLine.getQuantity());
        row.put("outstandingQuantity", bookingLine.outstandingQuantity());
        return row;
    }
}

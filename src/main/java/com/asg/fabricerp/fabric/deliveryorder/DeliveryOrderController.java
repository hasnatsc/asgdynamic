package com.asg.fabricerp.fabric.deliveryorder;

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

@Controller
public class DeliveryOrderController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo",    "documentNo",
        "documentDate",  "documentDate",
        "status",        "status",
        "totalQuantity", "totalQuantity"
    ));

    private final DeliveryOrderService service;

    public DeliveryOrderController(DeliveryOrderService service) {
        this.service = service;
    }

    @GetMapping("/delivery-order")
    @PreAuthorize("hasAnyRole('DO_VIEW', 'DO_MAKER', 'SALES')")
    public String page(Model model) {
        model.addAttribute("title", "Delivery Order");
        model.addAttribute("dlo", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        return "fabric/delivery-order";
    }

    @GetMapping("/api/delivery-order")
    @ResponseBody
    @PreAuthorize("hasAnyRole('DO_VIEW', 'DO_MAKER', 'SALES')")
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

        return DataTableResponse.from(draw, page, DeliveryOrderController::toRow);
    }

    @GetMapping("/api/delivery-order/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('DO_VIEW', 'DO_MAKER', 'SALES')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    /** What the "raise against Request-for-PI" line picker offers. */
    @GetMapping("/api/delivery-order/schedule-lines/{requestForPiId}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('DO_VIEW', 'DO_MAKER', 'SALES')")
    public List<Map<String, Object>> openScheduleLines(@PathVariable Long requestForPiId) {
        return service.openScheduleLines(requestForPiId).stream()
            .map(DeliveryOrderController::toSourceOption).toList();
    }

    @PostMapping("/api/delivery-order")
    @ResponseBody
    @PreAuthorize("hasRole('DO_MAKER')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument dlo) {
        return toDetail(service.save(dlo));
    }

    @DeleteMapping("/api/delivery-order/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('DO_MAKER')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("scheduleId", d.getParentDocumentId());
        row.put("documentDate", d.getDocumentDate() == null ? "" : d.getDocumentDate().toString());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    private static Map<String, Object> toDetail(BusinessDocument d) {
        Map<String, Object> detail = new LinkedHashMap<>(toRow(d));
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lines", d.getLines().stream().map(DeliveryOrderController::toLine).toList());
        return detail;
    }

    private static Map<String, Object> toLine(BusinessDocumentLine l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("lineNo", l.getLineNo());
        row.put("sourceLineId", l.getSourceLineId());
        row.put("construction", l.getFabric().getConstruction());
        row.put("colourName", l.getFabric().getColourName());
        row.put("quantity", l.getQuantity());
        return row;
    }

    private static Map<String, Object> toSourceOption(BusinessDocumentLine scheduleLine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceLineId", scheduleLine.getId());
        row.put("construction", scheduleLine.getFabric().getConstruction());
        row.put("colourName", scheduleLine.getFabric().getColourName());
        row.put("orderedQuantity", scheduleLine.getQuantity());
        row.put("outstandingQuantity", scheduleLine.outstandingQuantity());
        return row;
    }
}

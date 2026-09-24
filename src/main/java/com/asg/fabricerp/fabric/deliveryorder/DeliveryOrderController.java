package com.asg.fabricerp.fabric.deliveryorder;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
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
    @PreAuthorize("hasAuthority('SCREEN_DO_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Delivery Order");
        model.addAttribute("dlo", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        return "fabric/delivery-order";
    }

    @GetMapping("/api/delivery-order")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DO_VIEW')")
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
    @PreAuthorize("hasAuthority('SCREEN_DO_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    /** What the "raise against Request-for-PI" line picker offers. */
    @GetMapping("/api/delivery-order/schedule-lines/{requestForPiId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DO_VIEW')")
    public List<Map<String, Object>> openScheduleLines(@PathVariable Long requestForPiId) {
        return service.openScheduleLines(requestForPiId).stream()
            .map(DeliveryOrderController::toSourceOption).toList();
    }

    @PostMapping("/api/delivery-order")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DO_CREATE') or hasAuthority('SCREEN_DO_AMEND')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument dlo) {
        AuthorityChecks.require(dlo.getId() == null ? "SCREEN_DO_CREATE" : "SCREEN_DO_AMEND");
        return toDetail(service.save(dlo));
    }

    @DeleteMapping("/api/delivery-order/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DO_DELETE')")
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
        detail.put("lineGroups", d.getLineGroups().stream().map(DeliveryOrderController::toGroup).toList());
        return detail;
    }

    private static Map<String, Object> toGroup(BusinessDocumentLineGroup g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId());
        row.put("groupNo", g.getGroupNo());
        row.put("construction", g.getFabric().getConstruction());
        row.put("groupQuantity", g.groupQuantity());
        row.put("colorLines", g.getColorLines().stream().map(DeliveryOrderController::toColorLine).toList());
        return row;
    }

    private static Map<String, Object> toColorLine(BusinessDocumentColorLine l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("colorLineNo", l.getColorLineNo());
        row.put("sourceColorLineId", l.getSourceColorLineId());
        row.put("colorName", l.getColorName());
        row.put("quantity", l.getQuantity());
        return row;
    }

    private static Map<String, Object> toSourceOption(BusinessDocumentColorLine scheduleColorLine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceColorLineId", scheduleColorLine.getId());
        row.put("construction", scheduleColorLine.getLineGroup().getFabric().getConstruction());
        row.put("colorName", scheduleColorLine.getColorName());
        row.put("orderedQuantity", scheduleColorLine.getQuantity());
        row.put("outstandingQuantity", scheduleColorLine.outstandingQuantity());
        return row;
    }
}

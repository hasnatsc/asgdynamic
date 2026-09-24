package com.asg.fabricerp.fabric.requestforpi;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
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
 * Page/grid split identical to {@code BookingController}/{@code BpoController}. Submit,
 * approve and reject are not repeated here — see {@code /api/documents/{id}/...} in
 * {@code ApprovalController}.
 */
@Controller
public class RequestForPiController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo",     "documentNo",
        "documentDate",   "documentDate",
        "status",         "status",
        "totalQuantity",  "totalQuantity",
        "revisionNo",     "revisionNo"
    ));

    private final RequestForPiService service;

    public RequestForPiController(RequestForPiService service) {
        this.service = service;
    }

    @GetMapping("/requestforpi")
    @PreAuthorize("hasAnyRole('RPI_VIEW', 'RPI_MAKER', 'SALES')")
    public String page(Model model) {
        model.addAttribute("title", "Request For PI");
        model.addAttribute("rpi", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        return "fabric/requestforpi";
    }

    @GetMapping("/api/requestforpi")
    @ResponseBody
    @PreAuthorize("hasAnyRole('RPI_VIEW', 'RPI_MAKER', 'SALES')")
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

        return DataTableResponse.from(draw, page, RequestForPiController::toRow);
    }

    @GetMapping("/api/requestforpi/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('RPI_VIEW', 'RPI_MAKER', 'SALES')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    /** What the "raise against BPO" line picker offers. */
    @GetMapping("/api/requestforpi/bpo-lines/{bpoId}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('RPI_VIEW', 'RPI_MAKER', 'SALES')")
    public List<Map<String, Object>> openBpoLines(@PathVariable Long bpoId) {
        return service.openBpoLines(bpoId).stream().map(RequestForPiController::toSourceOption).toList();
    }

    @PostMapping("/api/requestforpi")
    @ResponseBody
    @PreAuthorize("hasRole('RPI_MAKER')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument rpi) {
        return toDetail(service.save(rpi));
    }

    @PostMapping("/api/requestforpi/{id}/revise")
    @ResponseBody
    @PreAuthorize("hasRole('RPI_MAKER')")
    public Map<String, Object> revise(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        return toDetail(service.revise(id, reason));
    }

    @DeleteMapping("/api/requestforpi/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('RPI_MAKER')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("bpoId", d.getParentDocumentId());
        row.put("documentDate", d.getDocumentDate() == null ? "" : d.getDocumentDate().toString());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    private static Map<String, Object> toDetail(BusinessDocument d) {
        Map<String, Object> detail = new LinkedHashMap<>(toRow(d));
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lineGroups", d.getLineGroups().stream().map(RequestForPiController::toGroup).toList());
        return detail;
    }

    private static Map<String, Object> toGroup(BusinessDocumentLineGroup g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId());
        row.put("groupNo", g.getGroupNo());
        row.put("construction", g.getFabric().getConstruction());
        row.put("groupQuantity", g.groupQuantity());
        row.put("colorLines", g.getColorLines().stream().map(RequestForPiController::toColorLine).toList());
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

    private static Map<String, Object> toSourceOption(BusinessDocumentColorLine bpoColorLine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceColorLineId", bpoColorLine.getId());
        row.put("construction", bpoColorLine.getLineGroup().getFabric().getConstruction());
        row.put("colorName", bpoColorLine.getColorName());
        row.put("orderedQuantity", bpoColorLine.getQuantity());
        row.put("outstandingQuantity", bpoColorLine.outstandingQuantity());
        return row;
    }
}

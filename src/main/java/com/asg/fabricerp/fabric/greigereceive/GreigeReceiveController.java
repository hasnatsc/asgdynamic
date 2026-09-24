package com.asg.fabricerp.fabric.greigereceive;

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

@Controller
public class GreigeReceiveController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo",    "documentNo",
        "documentDate",  "documentDate",
        "status",        "status",
        "totalQuantity", "totalQuantity"
    ));

    private final GreigeReceiveService service;

    public GreigeReceiveController(GreigeReceiveService service) {
        this.service = service;
    }

    @GetMapping("/greige-receive")
    @PreAuthorize("hasAnyRole('GR_VIEW', 'GR_MAKER', 'PRODUCTION')")
    public String page(Model model) {
        model.addAttribute("title", "Greige Fabrics Received");
        model.addAttribute("receipt", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        return "fabric/greige-receive";
    }

    @GetMapping("/api/greige-receive")
    @ResponseBody
    @PreAuthorize("hasAnyRole('GR_VIEW', 'GR_MAKER', 'PRODUCTION')")
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

        return DataTableResponse.from(draw, page, GreigeReceiveController::toRow);
    }

    @GetMapping("/api/greige-receive/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('GR_VIEW', 'GR_MAKER', 'PRODUCTION')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    @GetMapping("/api/greige-receive/bpo-lines/{bpoId}")
    @ResponseBody
    @PreAuthorize("hasAnyRole('GR_VIEW', 'GR_MAKER', 'PRODUCTION')")
    public List<Map<String, Object>> openBpoLines(@PathVariable Long bpoId) {
        return service.openBpoLines(bpoId).stream().map(GreigeReceiveController::toSourceOption).toList();
    }

    @PostMapping("/api/greige-receive")
    @ResponseBody
    @PreAuthorize("hasRole('GR_MAKER')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument receipt) {
        return toDetail(service.save(receipt));
    }

    @DeleteMapping("/api/greige-receive/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('GR_MAKER')")
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
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    private static Map<String, Object> toDetail(BusinessDocument d) {
        Map<String, Object> detail = new LinkedHashMap<>(toRow(d));
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lineGroups", d.getLineGroups().stream().map(GreigeReceiveController::toGroup).toList());
        return detail;
    }

    private static Map<String, Object> toGroup(BusinessDocumentLineGroup g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId());
        row.put("groupNo", g.getGroupNo());
        row.put("construction", g.getFabric().getConstruction());
        row.put("groupQuantity", g.groupQuantity());
        row.put("colorLines", g.getColorLines().stream().map(GreigeReceiveController::toColorLine).toList());
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

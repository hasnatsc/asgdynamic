package com.asg.fabricerp.fabric.productionorder;

import static com.asg.fabricerp.common.AuditableEntity.idOf;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Same page/grid split as {@code BookingController}; the one addition is
 * {@code /api/bpo/booking-lines/{bookingId}}, which the "raise BPO" form calls to populate
 * its line picker with what each Booking colour line still has outstanding.
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
    @PreAuthorize("hasAuthority('SCREEN_BPO_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Bulk Production Order");
        model.addAttribute("bpo", new BusinessDocument());
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        model.addAttribute("content", "fabric/bpo :: content");
        return "layout/main";
    }

    @GetMapping("/api/bpo")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BPO_VIEW')")
    @Transactional(readOnly = true)   // rows name the marketing team, a lazy association
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
    @PreAuthorize("hasAuthority('SCREEN_BPO_VIEW')")
    @Transactional(readOnly = true)
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    /** What the "raise BPO" line picker offers: Booking colour lines with something left to draw. */
    @GetMapping("/api/bpo/booking-lines/{bookingId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BPO_VIEW')")
    public List<Map<String, Object>> openBookingLines(@PathVariable Long bookingId) {
        return service.openBookingLines(bookingId).stream()
            .map(BpoController::toSourceOption)
            .toList();
    }

    @PostMapping("/api/bpo")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BPO_CREATE') or hasAuthority('SCREEN_BPO_AMEND')")
    public Map<String, Object> save(@Valid @RequestBody BusinessDocument bpo) {
        AuthorityChecks.require(bpo.getId() == null ? "SCREEN_BPO_CREATE" : "SCREEN_BPO_AMEND");
        return toDetail(service.save(bpo));
    }

    // Submit/approve/reject are the same action for every document type — see
    // /api/documents/{id}/submit|approve|reject in ApprovalController rather than a
    // per-type route here.

    @PostMapping("/api/bpo/{id}/revise")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BPO_AMEND')")
    public Map<String, Object> revise(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        return toDetail(service.revise(id, reason));
    }

    @DeleteMapping("/api/bpo/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BPO_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("bookingId", idOf(d.getParentDocument()));
        row.put("marketingTeamName", teamName(d));
        row.put("documentDate", d.getDocumentDate() == null ? "" : d.getDocumentDate().toString());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    /**
     * The owning team's name (ADM-7). Read only when loaded: save and revise answer after their own
     * transaction has closed, and a lazy team there would fail the whole response over a label.
     */
    private static String teamName(BusinessDocument d) {
        var team = d.getMarketingTeam();
        return team == null || !org.hibernate.Hibernate.isInitialized(team) ? null : team.getName();
    }

    private static Map<String, Object> toDetail(BusinessDocument d) {
        Map<String, Object> detail = new LinkedHashMap<>(toRow(d));
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lineGroups", d.getLineGroups().stream().map(BpoController::toGroup).toList());
        return detail;
    }

    private static Map<String, Object> toGroup(BusinessDocumentLineGroup g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId());
        row.put("groupNo", g.getGroupNo());
        row.put("costingCode", g.getFabric().getCostingCode());
        row.put("construction", g.getFabric().getConstruction());
        row.put("weaveType", g.getFabric().getWeaveType());
        row.put("finishType", g.getFabric().getFinishType());
        row.put("gsm", g.getFabric().getGsm());
        row.put("groupQuantity", g.groupQuantity());
        row.put("groupAmount", g.groupAmount());
        row.put("colorLines", g.getColorLines().stream().map(BpoController::toColorLine).toList());
        return row;
    }

    private static Map<String, Object> toColorLine(BusinessDocumentColorLine l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("colorLineNo", l.getColorLineNo());
        row.put("sourceColorLineId", idOf(l.getSourceColorLine()));
        row.put("colorCode", l.getColorCode());
        row.put("colorName", l.getColorName());
        row.put("quantity", l.getQuantity());
        row.put("rate", l.getRate());
        row.put("lineAmount", l.getLineAmount());
        return row;
    }

    private static Map<String, Object> toSourceOption(BusinessDocumentColorLine bookingColorLine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceColorLineId", bookingColorLine.getId());
        row.put("construction", bookingColorLine.getLineGroup().getFabric().getConstruction());
        row.put("colorName", bookingColorLine.getColorName());
        row.put("orderedQuantity", bookingColorLine.getQuantity());
        row.put("outstandingQuantity", bookingColorLine.outstandingQuantity());
        return row;
    }
}

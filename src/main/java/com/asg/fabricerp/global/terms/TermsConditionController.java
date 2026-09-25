package com.asg.fabricerp.global.terms;

import com.asg.fabricerp.global.documents.BusinessDocumentTerm;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The global terms & conditions - the legacy {@code /termsAndConditions/index} screen.
 *
 * <pre>
 *   GET    /setup/terms              page
 *   GET    /api/setup/terms          grid rows
 *   POST   /api/setup/terms          create or update
 *   DELETE /api/setup/terms/{id}     soft delete
 *   GET    /api/terms/defaults?type= the clauses a new document starts with
 *   GET    /api/terms/library?type=  every active clause, to add one to a document
 * </pre>
 *
 * The two {@code /api/terms} feeds are open to any signed-in user: whoever may raise a booking
 * needs its standard clauses, without being able to maintain them.
 */
@Controller
public class TermsConditionController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "conditionType", "conditionType",
        "caption",       "caption",
        "isDefault",     "isDefault",
        "sortOrder",     "sortOrder",
        "active",        "active"
    ));

    private final TermsConditionService service;

    public TermsConditionController(TermsConditionService service) {
        this.service = service;
    }

    @GetMapping("/setup/terms")
    @PreAuthorize("hasAuthority('SCREEN_TERMS_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Terms & conditions");
        model.addAttribute("conditionTypes", ConditionType.values());
        model.addAttribute("content", "setup/terms :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/terms")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_TERMS_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) ConditionType type) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<TermsCondition> page = service.search(type, request.searchOrNull(),
            request.toPageable(SORTABLE, "sortOrder"));
        return DataTableResponse.from(draw, page, TermsConditionController::toRow);
    }

    @PostMapping("/api/setup/terms")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_TERMS_CREATE') or hasAuthority('SCREEN_TERMS_AMEND')")
    public Map<String, Object> save(@Valid @RequestBody TermsCondition clause) {
        AuthorityChecks.require(clause.getId() == null ? "SCREEN_TERMS_CREATE" : "SCREEN_TERMS_AMEND");
        return toRow(service.save(clause));
    }

    @DeleteMapping("/api/setup/terms/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_TERMS_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    @GetMapping("/api/terms/defaults")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> defaults(@RequestParam ConditionType type) {
        return service.defaultTermsFor(type).stream().map(TermsConditionController::toTerm).toList();
    }

    @GetMapping("/api/terms/library")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> library(@RequestParam ConditionType type) {
        return service.library(type).stream().map(TermsConditionController::toRow).toList();
    }

    private static Map<String, Object> toTerm(BusinessDocumentTerm t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serialNo", t.getSerialNo());
        row.put("bodyText", t.getBodyText());
        return row;
    }

    private static Map<String, Object> toRow(TermsCondition t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("conditionType", t.getConditionType().name());
        row.put("conditionTypeLabel", t.getConditionType().label());
        row.put("caption", t.getCaption());
        row.put("bodyText", t.getBodyText());
        row.put("isDefault", t.getIsDefault());
        row.put("sortOrder", t.getSortOrder());
        row.put("active", t.getActive());
        return row;
    }
}

package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.RoleRepository;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * The Approvals inbox and the Approval matrices setup - asfl-erp's {@code /api/approvals}.
 *
 * <pre>
 *   GET    /approvals                          the inbox page
 *   GET    /api/approvals/inbox                what is waiting for the signed-in user
 *   GET    /api/approvals/requests?pending=    every request in the unit (grid)
 *   GET    /setup/approval-matrices            the setup page
 *   GET    /api/setup/approval-matrices        matrices (grid)
 *   GET    /api/setup/approval-matrices/{id}   one, with its levels
 *   GET    /api/setup/approval-matrices/options  document types, teams, roles, users for the editor
 *   POST   /api/setup/approval-matrices        define or change
 *   DELETE /api/setup/approval-matrices/{id}   delete (refused once used - deactivate instead)
 * </pre>
 *
 * Deciding is not here: it is {@code POST /api/documents/{id}/approve|return|reject}, the same
 * whether the approver comes from this inbox or from the document's own screen.
 */
@Controller
public class ApprovalsController {

    private static final SortWhitelist MATRIX_SORT = SortWhitelist.of(Map.of(
        "documentType", "documentType",
        "name",         "name",
        "active",       "active"
    ));

    private final ApprovalService approvals;
    private final ApprovalMatrixService matrices;
    private final ApprovalLabels labels;
    private final MarketingTeamRepository teams;
    private final RoleRepository roles;
    private final FabricUserRepository users;
    private final OrgContext context;

    public ApprovalsController(ApprovalService approvals, ApprovalMatrixService matrices, ApprovalLabels labels,
                               MarketingTeamRepository teams, RoleRepository roles, FabricUserRepository users,
                               OrgContext context) {
        this.approvals = approvals;
        this.matrices = matrices;
        this.labels = labels;
        this.teams = teams;
        this.roles = roles;
        this.users = users;
        this.context = context;
    }

    // ------------------------------------------------------------------------------ inbox

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public String inboxPage(Model model) {
        model.addAttribute("title", "Approvals");
        model.addAttribute("content", "approvals/inbox :: content");
        return "layout/main";
    }

    @GetMapping("/api/approvals/inbox")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public List<ApprovalRowView> inbox() {
        return approvals.inbox();
    }

    @GetMapping("/api/approvals/requests")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public DataTableResponse<ApprovalRowView> requests(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(required = false) Boolean pending) {
        int size = Math.max(length, 1);
        Page<ApprovalRowView> page = approvals.report(pending, start / size, size);
        // A request whose document the caller cannot see comes back null and is left out.
        List<ApprovalRowView> rows = page.getContent().stream().filter(Objects::nonNull).toList();
        return new DataTableResponse<>(draw, page.getTotalElements(), page.getTotalElements(), rows);
    }

    // ------------------------------------------------------------------------------ matrices

    @GetMapping("/setup/approval-matrices")
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_VIEW')")
    public String matricesPage(Model model) {
        model.addAttribute("title", "Approval matrices");
        model.addAttribute("documentTypes", ApprovalMatrixService.approvableTypes());
        model.addAttribute("content", "setup/approval-matrices :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/approval-matrices")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_VIEW')")
    @Transactional(readOnly = true)
    public DataTableResponse<Map<String, Object>> matrixGrid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) DocumentType type) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return DataTableResponse.from(draw, matrices.search(type, request.searchOrNull(),
            request.toPageable(MATRIX_SORT, "documentType")), this::matrixRow);
    }

    @GetMapping("/api/setup/approval-matrices/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_VIEW')")
    @Transactional(readOnly = true)
    public Map<String, Object> matrix(@PathVariable Long id) {
        return matrixRow(matrices.get(id));
    }

    @GetMapping("/api/setup/approval-matrices/options")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_VIEW')")
    @Transactional(readOnly = true)
    public Map<String, Object> options() {
        Long orgId = context.requireOrganizationId();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("teams", teams.lookup(orgId).stream()
            .map(t -> Map.of("id", t.getId(), "text", t.getName())).toList());
        options.put("roles", roles.findAll(Sort.by("name")).stream()
            .filter(r -> !Boolean.FALSE.equals(r.getActive()))
            .map(r -> Map.of("id", r.getId(), "text", r.getName())).toList());
        options.put("users", users.search(orgId, null, false, null, null, PageRequest.of(0, 500, Sort.by("fullName")))
            .stream().map(u -> Map.of("id", u.getId(), "text",
                u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName() + " (" + u.getUsername() + ")"))
            .toList());
        return options;
    }

    @PostMapping("/api/setup/approval-matrices")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_CREATE') or hasAuthority('SCREEN_APPROVAL_SETUP_AMEND')")
    @Transactional
    public Map<String, Object> save(@RequestBody ApprovalMatrixService.MatrixRequest request) {
        AuthorityChecks.require(request.id() == null ? "SCREEN_APPROVAL_SETUP_CREATE" : "SCREEN_APPROVAL_SETUP_AMEND");
        return matrixRow(matrices.save(request));
    }

    @DeleteMapping("/api/setup/approval-matrices/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        matrices.delete(id);
        return Map.of("deleted", id);
    }

    private Map<String, Object> matrixRow(ApprovalMatrix m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("documentType", m.getDocumentType().name());
        row.put("documentTypeLabel", ApprovalLabels.screenLabel(m.getDocumentType()));
        row.put("marketingTeamId", m.getMarketingTeamId());
        row.put("scope", m.isTeamWise() ? "Team " + labels.teamName(m.getMarketingTeamId()) : "Business unit");
        row.put("name", m.getName());
        row.put("active", m.getActive());
        row.put("levels", m.getLevels().stream().map(l -> {
            Map<String, Object> level = new LinkedHashMap<>();
            level.put("sequence", l.getSequence());
            level.put("roleId", l.getRoleId());
            level.put("userId", l.getUserId());
            level.put("approver", labels.approver(l.approver(), m.getDocumentType()));
            level.put("minAmount", l.getMinAmount());
            level.put("maxAmount", l.getMaxAmount());
            level.put("band", band(l.getMinAmount(), l.getMaxAmount()));
            return level;
        }).toList());
        return row;
    }

    private static String band(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return "Any amount";
        if (min == null) return "Up to " + max.toPlainString();
        if (max == null) return "From " + min.toPlainString();
        return min.toPlainString() + " – " + max.toPlainString();
    }
}

package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.Role;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.RoleRepository;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 *   GET    /api/approvals/inbox                what is waiting for the signed-in user (paged grid)
 *   GET    /api/approvals/inbox/count          how many - the badge
 *   POST   /api/approvals/decide               one decision on many documents (bulk approve/return/reject)
 *   GET    /api/approvals/requests?pending=    every request in the unit (grid)
 *   GET    /setup/approval-matrices            the setup page
 *   GET    /api/setup/approval-matrices        matrices (grid)
 *   GET    /api/setup/approval-matrices/{id}   one, with its levels
 *   GET    /api/setup/approval-matrices/options  teams for the editor
 *   GET    /api/setup/approval-matrices/roles    role picker feed (LookupPage: ?q, ?page, ?id)
 *   GET    /api/setup/approval-matrices/users    person picker feed (LookupPage: ?q, ?page, ?id)
 *   POST   /api/setup/approval-matrices        define or change
 *   DELETE /api/setup/approval-matrices/{id}   delete (refused once used - deactivate instead)
 * </pre>
 *
 * Deciding is not here: it is {@code POST /api/documents/{id}/approve|return|reject}, the same
 * whether the approver comes from this inbox or from the document's own screen.
 */
@Controller
public class ApprovalsController {

    /** At most this many documents in one bulk decision - each is decided, and checked, on its own. */
    static final int BULK_LIMIT = 200;

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
        model.addAttribute("documentTypes", ApprovalMatrixService.approvableTypes());
        model.addAttribute("content", "approvals/inbox :: content");
        return "layout/main";
    }

    /** The centralised inbox, one page at a time: oldest first, filtered by type and by number/buyer/reference. */
    @GetMapping("/api/approvals/inbox")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public DataTableResponse<ApprovalRowView> inbox(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) DocumentType type) {
        Page<ApprovalRowView> page = approvals.inbox(new ApprovalService.InboxFilter(type, search), pageOf(start, length));
        return new DataTableResponse<>(draw, page.getTotalElements(), page.getTotalElements(), page.getContent());
    }

    @GetMapping("/api/approvals/inbox/count")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public Map<String, Object> inboxCount() {
        return Map.of("count", approvals.inboxCount());
    }

    /** What a bulk decision posts: the documents, the decision, one remark for all (required to return or reject). */
    public record BulkDecision(List<Long> documentIds, ApprovalDecision decision, String remarks) { }

    /**
     * One decision on many documents. Each is decided in its own transaction with every check a
     * single decision gets - level, team, four-eyes - so one that fails is reported and the rest
     * still go through. Deliberately not @Transactional.
     */
    @PostMapping("/api/approvals/decide")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public Map<String, Object> decideAll(@RequestBody BulkDecision request) {
        List<Long> ids = request.documentIds() == null ? List.of()
            : request.documentIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one document");
        }
        if (ids.size() > BULK_LIMIT) {
            throw new IllegalArgumentException("At most %d documents at a time - you chose %d".formatted(BULK_LIMIT, ids.size()));
        }
        if (request.decision() == null) {
            throw new IllegalArgumentException("Choose approve, return or reject");
        }
        if (request.decision().isRefusal() && (request.remarks() == null || request.remarks().isBlank())) {
            throw new IllegalArgumentException("Give the reason - every maker will see it");
        }
        List<Map<String, Object>> done = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                var doc = approvals.decide(id, request.decision(), request.remarks());
                done.add(Map.of("id", doc.getId(), "documentNo", String.valueOf(doc.getDocumentNo()),
                    "status", doc.getStatus().name()));
            } catch (RuntimeException e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("id", id);
                failure.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                failed.add(failure);
            }
        }
        return Map.of("done", done, "failed", failed);
    }

    @GetMapping("/api/approvals/requests")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVALS_VIEW')")
    public DataTableResponse<ApprovalRowView> requests(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) Boolean pending,
            @RequestParam(required = false) DocumentType type) {
        Page<ApprovalRowView> page = approvals.report(pending, type, search, pageOf(start, length));
        return new DataTableResponse<>(draw, page.getTotalElements(), page.getTotalElements(), page.getContent());
    }

    private static Pageable pageOf(int start, int length) {
        int size = Math.min(Math.max(length, 1), 100);
        return PageRequest.of(Math.max(start, 0) / size, size);
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
        // Roles and people are searched and paged on demand - see roleLookup / userLookup.
        return options;
    }

    /** The level editor's role picker, searched and paged like the Booking buyer picker. */
    @GetMapping("/api/setup/approval-matrices/roles")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_VIEW')")
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> roleLookup(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) Long id) {
        if (id != null) {
            return LookupPage.single(roles.findById(id).map(ApprovalsController::roleOption).orElse(null));
        }
        return LookupPage.of(roles.lookup(LookupPage.like(q), LookupPage.pageable(page, size, Sort.by("name"))),
            ApprovalsController::roleOption);
    }

    /** The level editor's person picker: this organization's unlocked users, by name or username. */
    @GetMapping("/api/setup/approval-matrices/users")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_APPROVAL_SETUP_VIEW')")
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> userLookup(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) Long id) {
        Long orgId = context.requireOrganizationId();
        if (id != null) {
            return LookupPage.single(users.findScoped(id, orgId).map(ApprovalsController::userOption).orElse(null));
        }
        return LookupPage.of(users.search(orgId, q == null || q.isBlank() ? null : q.trim(), false, null, null,
                LookupPage.pageable(page, size, Sort.by("fullName"))),
            ApprovalsController::userOption);
    }

    private static LookupPage.Option roleOption(Role r) {
        return new LookupPage.Option(r.getId(), null, r.getName(), r.getDescription());
    }

    private static LookupPage.Option userOption(FabricUser u) {
        String name = u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName();
        return new LookupPage.Option(u.getId(), u.getUsername(), name, null);
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
            // The pickers' labels, so a saved level shows without a lookup round-trip.
            level.put("roleName", l.getRoleId() == null ? null
                : roles.findById(l.getRoleId()).map(Role::getName).orElse("#" + l.getRoleId()));
            level.put("userName", l.getUserId() == null ? null : labels.userName(l.getUserId()));
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

package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.UserAdminService.StatusFilter;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** CRUD for {@link FabricUser} logins — the piece {@code DevUserSeeder} stood in for until now. */
@Controller
public class UserAdminController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "username", "username",
        "fullName", "fullName",
        "businessUnitCode", "businessUnitCode",
        "lastLoginAt", "lastLoginAt"
    ));

    private final UserAdminService service;
    private final RoleRepository roleRepository;
    private final BusinessUnitRepository businessUnits;
    private final WarehouseRepository warehouses;
    private final MarketingTeamRepository marketingTeams;
    private final OrgContext context;

    public UserAdminController(UserAdminService service, RoleRepository roleRepository,
                               BusinessUnitRepository businessUnits, WarehouseRepository warehouses,
                               MarketingTeamRepository marketingTeams, OrgContext context) {
        this.service = service;
        this.roleRepository = roleRepository;
        this.businessUnits = businessUnits;
        this.warehouses = warehouses;
        this.marketingTeams = marketingTeams;
        this.context = context;
    }

    @GetMapping("/setup/users")
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public String page(Model model) {
        Long orgId = context.requireOrganizationId();
        var unitOptions = businessUnits.lookup(orgId).stream()
            .map(b -> option(b.getId(), b.getCode() + " - " + b.getName())).toList();
        var warehouseOptions = warehouses.lookup(orgId).stream()
            .map(w -> {
                Map<String, Object> o = new LinkedHashMap<>(option(w.getId(), w.getCode() + " - " + w.getName()));
                o.put("businessUnitId", w.getBusinessUnitId());
                return o;
            }).toList();

        model.addAttribute("title", "Users");
        model.addAttribute("roles", roleRepository.findAll(Sort.by("name")).stream()
            .map(r -> Map.of("id", r.getId(), "name", r.getName(),
                "description", r.getDescription() == null ? "" : r.getDescription(),
                "active", Boolean.TRUE.equals(r.getActive())))
            .toList());
        model.addAttribute("businessUnits", unitOptions);
        model.addAttribute("warehouses", warehouseOptions);
        // Scope-grant pickers: one list per ScopeDimension, keyed by the enum name the JS posts.
        model.addAttribute("scopeOptions", Map.of(
            ScopeDimension.BUSINESS_UNIT.name(), unitOptions,
            ScopeDimension.WAREHOUSE.name(), warehouseOptions,
            ScopeDimension.MARKETING_TEAM.name(), marketingTeams.lookup(orgId).stream()
                .map(t -> option(t.getId(), t.getName())).toList()));
        model.addAttribute("currentUserId", CurrentUser.id());
        model.addAttribute("minPasswordLength", PasswordPolicy.MIN_LENGTH);
        model.addAttribute("content", "setup/users :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/users")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) StatusFilter status) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<FabricUser> page = service.search(request.searchOrNull(), status,
            request.toPageable(SORTABLE, "username"));
        Set<Long> scoped = service.idsHoldingScopeToday(
            page.getContent().stream().map(FabricUser::getId).toList());
        return DataTableResponse.from(draw, page, u -> toRow(u, scoped));
    }

    @GetMapping("/api/setup/users/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    /** Every scope grant, open and closed — the history is the point of effective dating. */
    @GetMapping("/api/setup/users/{id}/scopes")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public List<Map<String, Object>> scopes(@PathVariable Long id) {
        return service.scopesOf(id).stream().map(UserAdminController::toScopeRow).toList();
    }

    /** Opens a scope grant — ADM-3, ADM-4. Refused on your own account. */
    @PostMapping("/api/setup/users/{id}/scopes")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> grantScope(@PathVariable Long id, @Valid @RequestBody GrantScopeRequest request) {
        return toScopeRow(service.grantScope(id, request.dimension(), request.scopeValueId(),
            request.from(), request.remarks()));
    }

    /** Closes a scope grant from a date. The row survives, so the history stays true. */
    @PostMapping("/api/setup/scopes/{scopeId}/revoke")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> revokeScope(@PathVariable Long scopeId, @Valid @RequestBody RevokeScopeRequest request) {
        service.revokeScope(scopeId, request.from(), request.reason());
        return Map.of("id", scopeId, "revoked", true);
    }

    @PostMapping("/api/setup/users")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_CREATE')")
    public Map<String, Object> create(@Valid @RequestBody CreateUserRequest request) {
        FabricUser saved = service.create(request.username(), request.password(), request.fullName(),
            request.businessUnitId(), request.warehouseId(), request.roleIds(), request.unrestricted());
        return toDetail(saved);
    }

    @PostMapping("/api/setup/users/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        FabricUser saved = service.update(id, request.fullName(), request.businessUnitId(),
            request.warehouseId(), request.roleIds(), request.unrestricted());
        return toDetail(saved);
    }

    @PostMapping("/api/setup/users/{id}/reset-password")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.password());
        return Map.of("id", id, "passwordReset", true);
    }

    @PostMapping("/api/setup/users/{id}/lock")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> lock(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.setLocked(id, true, reason);
        return Map.of("id", id, "accountLocked", true);
    }

    @PostMapping("/api/setup/users/{id}/unlock")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> unlock(@PathVariable Long id) {
        service.setLocked(id, false, null);
        return Map.of("id", id, "accountLocked", false);
    }

    @DeleteMapping("/api/setup/users/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(FabricUser u, Set<Long> scoped) {
        Map<String, Object> row = toRow(u);
        // Restricted with nothing held today: the login refuses them (ADM-3).
        row.put("cannotSignIn", !u.isUnrestricted() && !scoped.contains(u.getId()));
        return row;
    }

    private static Map<String, Object> toRow(FabricUser u) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId());
        row.put("username", u.getUsername());
        row.put("fullName", u.getFullName());
        row.put("businessUnitCode", u.getBusinessUnitCode());
        row.put("accountLocked", u.getAccountLocked());
        row.put("unrestricted", u.isUnrestricted());
        row.put("active", u.getActive());
        row.put("mustChangePassword", u.isMustChangePassword());
        row.put("lastLoginAt", u.getLastLoginAt());
        row.put("roles", u.getRoles().stream().map(Role::getName).sorted().toList());
        return row;
    }

    private static Map<String, Object> toDetail(FabricUser u) {
        Map<String, Object> row = toRow(u);
        row.put("businessUnitId", u.getBusinessUnitId());
        row.put("warehouseId", u.getWarehouseId());
        row.put("roleIds", u.getRoles().stream().map(Role::getId).collect(Collectors.toSet()));
        row.put("lockedAt", u.getLockedAt());
        row.put("lockedReason", u.getLockedReason());
        row.put("failedLoginCount", u.getFailedLoginCount());
        row.put("lastFailedLoginAt", u.getLastFailedLoginAt());
        row.put("passwordChangedAt", u.getPasswordChangedAt());
        return row;
    }

    private static Map<String, Object> toScopeRow(DataScope s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("dimension", s.getDimension());
        row.put("scopeValueId", s.getScopeValueId());
        row.put("grantedFrom", s.getGrantedFrom());
        row.put("revokedFrom", s.getRevokedFrom());
        row.put("revokedReason", s.getRevokedReason());
        row.put("remarks", s.getRemarks());
        row.put("heldToday", s.isHeldOn(LocalDate.now()));
        return row;
    }

    private static Map<String, Object> option(Long id, String label) {
        return Map.of("id", id, "label", label);
    }

    public record CreateUserRequest(
        @NotBlank @Size(max = 80) String username, @NotBlank String password,
        @Size(max = 150) String fullName, @NotNull Long businessUnitId, Long warehouseId,
        Set<Long> roleIds, boolean unrestricted) { }

    public record UpdateUserRequest(
        @Size(max = 150) String fullName, @NotNull Long businessUnitId, Long warehouseId,
        Set<Long> roleIds, boolean unrestricted) { }

    public record GrantScopeRequest(
        @NotNull ScopeDimension dimension, @NotNull Long scopeValueId, LocalDate from,
        @Size(max = 255) String remarks) { }

    public record RevokeScopeRequest(LocalDate from, @NotBlank @Size(max = 255) String reason) { }

    public record ResetPasswordRequest(@NotBlank String password) { }
}

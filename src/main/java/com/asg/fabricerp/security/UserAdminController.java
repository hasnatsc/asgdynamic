package com.asg.fabricerp.security;

import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** CRUD for {@link FabricUser} logins — the piece {@code DevUserSeeder} stood in for until now. */
@Controller
@PreAuthorize("hasRole('SECURITY_ADMIN')")
public class UserAdminController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "username", "username",
        "fullName", "fullName"
    ));

    private final UserAdminService service;
    private final RoleRepository roleRepository;

    public UserAdminController(UserAdminService service, RoleRepository roleRepository) {
        this.service = service;
        this.roleRepository = roleRepository;
    }

    @GetMapping("/setup/users")
    public String page(Model model) {
        model.addAttribute("title", "Users");
        model.addAttribute("roles", roleRepository.findAll(Sort.by("name")));
        model.addAttribute("content", "setup/users :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/users")
    @ResponseBody
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<FabricUser> page = service.search(request.searchOrNull(),
            request.toPageable(SORTABLE, "username"));
        return DataTableResponse.from(draw, page, UserAdminController::toRow);
    }

    @GetMapping("/api/setup/users/{id}")
    @ResponseBody
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    @PostMapping("/api/setup/users")
    @ResponseBody
    public Map<String, Object> create(@Valid @RequestBody CreateUserRequest request) {
        FabricUser saved = service.create(request.username(), request.password(), request.fullName(),
            request.businessUnitId(), request.businessUnitCode(), request.warehouseId(),
            request.roleIds());
        return toDetail(saved);
    }

    @PostMapping("/api/setup/users/{id}")
    @ResponseBody
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        FabricUser saved = service.update(id, request.fullName(), request.businessUnitId(),
            request.businessUnitCode(), request.warehouseId(), request.roleIds());
        return toDetail(saved);
    }

    @PostMapping("/api/setup/users/{id}/reset-password")
    @ResponseBody
    public Map<String, Object> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.password());
        return Map.of("id", id, "passwordReset", true);
    }

    @PostMapping("/api/setup/users/{id}/lock")
    @ResponseBody
    public Map<String, Object> lock(@PathVariable Long id) {
        service.setLocked(id, true);
        return Map.of("id", id, "accountLocked", true);
    }

    @PostMapping("/api/setup/users/{id}/unlock")
    @ResponseBody
    public Map<String, Object> unlock(@PathVariable Long id) {
        service.setLocked(id, false);
        return Map.of("id", id, "accountLocked", false);
    }

    @DeleteMapping("/api/setup/users/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(FabricUser u) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId());
        row.put("username", u.getUsername());
        row.put("fullName", u.getFullName());
        row.put("businessUnitCode", u.getBusinessUnitCode());
        row.put("accountLocked", u.getAccountLocked());
        row.put("active", u.getActive());
        row.put("roles", u.getRoles().stream().map(Role::getName)
            .collect(Collectors.joining(", ")));
        return row;
    }

    private static Map<String, Object> toDetail(FabricUser u) {
        Map<String, Object> row = toRow(u);
        row.put("businessUnitId", u.getBusinessUnitId());
        row.put("warehouseId", u.getWarehouseId());
        row.put("roleIds", u.getRoles().stream().map(Role::getId).collect(Collectors.toSet()));
        return row;
    }

    public record CreateUserRequest(
        @NotBlank String username, @NotBlank String password, String fullName,
        Long businessUnitId, @NotBlank String businessUnitCode, Long warehouseId,
        Set<Long> roleIds) { }

    public record UpdateUserRequest(
        String fullName, Long businessUnitId, @NotBlank String businessUnitCode,
        Long warehouseId, Set<Long> roleIds) { }

    public record ResetPasswordRequest(@NotBlank String password) { }
}

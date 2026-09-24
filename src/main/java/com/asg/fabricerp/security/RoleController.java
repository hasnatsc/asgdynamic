package com.asg.fabricerp.security;

import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** CRUD for {@link Role}: named, reusable bundles of existing {@link Permission}s. */
@Controller
@PreAuthorize("hasRole('SECURITY_ADMIN')")
public class RoleController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "name",   "name",
        "active", "active"
    ));

    private final RoleService service;
    private final PermissionRepository permissionRepository;

    public RoleController(RoleService service, PermissionRepository permissionRepository) {
        this.service = service;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping("/setup/roles")
    public String page(Model model) {
        model.addAttribute("title", "Roles");
        model.addAttribute("permissions", permissionRepository.findAll(
            org.springframework.data.domain.Sort.by("module", "name")));
        model.addAttribute("content", "setup/roles :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/roles")
    @ResponseBody
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<Role> page = service.search(request.searchOrNull(), request.toPageable(SORTABLE, "name"));
        return DataTableResponse.from(draw, page, RoleController::toRow);
    }

    @GetMapping("/api/setup/roles/{id}")
    @ResponseBody
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    @PostMapping("/api/setup/roles")
    @ResponseBody
    public Map<String, Object> save(@Valid @RequestBody RoleRequest request) {
        Role submitted = new Role(request.name(), request.description());
        submitted.setId(request.id());
        submitted.setActive(request.active() == null || request.active());
        Role saved = service.save(submitted, request.permissionIds());
        return toDetail(saved);
    }

    @DeleteMapping("/api/setup/roles/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(Role r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("name", r.getName());
        row.put("description", r.getDescription());
        row.put("active", r.getActive());
        row.put("permissionCount", r.getPermissions().size());
        return row;
    }

    private static Map<String, Object> toDetail(Role r) {
        Map<String, Object> row = toRow(r);
        row.put("permissionIds", r.getPermissions().stream()
            .map(Permission::getId).collect(Collectors.toSet()));
        return row;
    }

    public record RoleRequest(Long id, String name, String description, Boolean active,
                              Set<Long> permissionIds) { }
}

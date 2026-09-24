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

/** CRUD for {@link Role}: named, reusable bundles of per-{@link Screen} {@link Verb} grants. */
@Controller
public class RoleController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "name",   "name",
        "active", "active"
    ));

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping("/setup/roles")
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Roles");
        model.addAttribute("screens", Screen.values());
        model.addAttribute("verbs", Verb.values());
        model.addAttribute("content", "setup/roles :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/roles")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
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
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return toDetail(service.get(id));
    }

    @PostMapping("/api/setup/roles")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_CREATE') or hasAuthority('SCREEN_SECURITY_ADMIN_AMEND')")
    public Map<String, Object> save(@Valid @RequestBody RoleRequest request) {
        Role submitted = new Role(request.name(), request.description());
        submitted.setId(request.id());
        submitted.setActive(request.active() == null || request.active());
        Role saved = service.save(submitted, toGrantMap(request.grants()));
        return toDetail(saved);
    }

    @DeleteMapping("/api/setup/roles/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<Screen, Set<Verb>> toGrantMap(Map<Screen, Set<Verb>> grants) {
        return grants == null ? Map.of() : grants;
    }

    private static Map<String, Object> toRow(Role r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("name", r.getName());
        row.put("description", r.getDescription());
        row.put("active", r.getActive());
        row.put("screenCount", r.getScreenGrants().size());
        return row;
    }

    private static Map<String, Object> toDetail(Role r) {
        Map<String, Object> row = toRow(r);
        Map<String, Set<Verb>> byScreenName = new LinkedHashMap<>();
        r.grantsByScreen().forEach((screen, verbs) -> byScreenName.put(screen.name(), verbs));
        row.put("grants", byScreenName);
        return row;
    }

    public record RoleRequest(Long id, String name, String description, Boolean active,
                              Map<Screen, Set<Verb>> grants) { }
}

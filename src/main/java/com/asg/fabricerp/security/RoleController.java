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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
        // Grouped by menu section, so the matrix reads the way the sidebar does.
        Map<String, List<Screen>> screensBySection = new LinkedHashMap<>();
        for (Screen.Section section : Screen.Section.values()) {
            List<Screen> inSection = Arrays.stream(Screen.values())
                .filter(s -> s.section() == section).toList();
            if (!inSection.isEmpty()) {
                screensBySection.put(section.label(), inSection);
            }
        }
        model.addAttribute("screensBySection", screensBySection);
        model.addAttribute("verbs", Verb.values());
        model.addAttribute("content", "setup/roles :: content");
        return "layout/main";
    }

    /** {@code grants}: {@code with} = roles granting something, {@code empty} = unconfigured shells. */
    @GetMapping("/api/setup/roles")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String grants) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Boolean hasGrants = "with".equals(grants) ? Boolean.TRUE : "empty".equals(grants) ? Boolean.FALSE : null;
        Page<Role> page = service.search(request.searchOrNull(), hasGrants, request.toPageable(SORTABLE, "name"));
        Map<Long, Long> userCounts = service.userCounts(page.getContent().stream().map(Role::getId).toList());
        return DataTableResponse.from(draw, page, r -> {
            Map<String, Object> row = toRow(r);
            row.put("userCount", userCounts.getOrDefault(r.getId(), 0L));
            return row;
        });
    }

    @GetMapping("/api/setup/roles/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> row = toDetail(service.get(id));
        row.put("userCount", service.userCounts(List.of(id)).getOrDefault(id, 0L));
        return row;
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

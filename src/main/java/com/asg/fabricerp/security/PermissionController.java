package com.asg.fabricerp.security;

import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browse the {@link Permission} catalog. Deliberately read-only: {@code Permission.name} must
 * match a {@code @PreAuthorize}/{@code hasRole(...)} string already compiled into a controller
 * for a row to mean anything — creating a new row here would not protect any route, it would
 * just be an unused name (exactly the trap {@code V3__users.sql}'s original comment warned a
 * Role/Permission table would be, absent something to consume it — see {@link FabricUser}'s
 * javadoc). {@link RoleController} is where the catalog actually gets used, by bundling
 * existing rows into an assignable role.
 */
@Controller
@PreAuthorize("hasRole('SECURITY_ADMIN')")
public class PermissionController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "name",   "name",
        "module", "module"
    ));

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping("/setup/permissions")
    public String page(Model model) {
        model.addAttribute("title", "Permissions");
        model.addAttribute("content", "setup/permissions :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/permissions")
    @ResponseBody
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<Permission> page = service.search(request.searchOrNull(),
            request.toPageable(SORTABLE, "module"));

        return DataTableResponse.from(draw, page, PermissionController::toRow);
    }

    private static Map<String, Object> toRow(Permission p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("name", p.getName());
        row.put("module", p.getModule());
        row.put("description", p.getDescription());
        row.put("active", p.getActive());
        return row;
    }
}

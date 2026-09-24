package com.asg.fabricerp.fabric.setup;

import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import com.asg.fabricerp.security.AuthorityChecks;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One controller for all seven fabric reference lists, routed by slug:
 *
 * <pre>
 *   GET    /setup/fabric/weave-type          page
 *   GET    /api/setup/fabric/weave-type      grid rows
 *   POST   /api/setup/fabric/weave-type      create or update
 *   DELETE /api/setup/fabric/weave-type/{id} soft delete
 * </pre>
 *
 * Adding a list means adding one {@link AttributeType} constant — no new controller,
 * service, DTO, template or grid.
 */
@Controller
public class FabricAttributeController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "code",         "code",
        "name",         "name",
        "displayOrder", "displayOrder",
        "active",       "active"
    ));

    private final FabricAttributeService service;

    public FabricAttributeController(FabricAttributeService service) {
        this.service = service;
    }

    @GetMapping("/setup/fabric/{slug}")
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_SETUP_VIEW')")
    public String page(@PathVariable String slug, Model model) {
        AttributeType type = AttributeType.fromSlug(slug);
        model.addAttribute("attributeType", type);
        model.addAttribute("title", type.label());
        model.addAttribute("slug", type.slug());
        model.addAttribute("attribute", new FabricAttribute());
        return "setup/fabric-attribute";
    }

    @GetMapping("/api/setup/fabric/{slug}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_SETUP_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir) {

        AttributeType type = AttributeType.fromSlug(slug);
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<FabricAttribute> page = service.search(
            type, request.searchOrNull(), request.toPageable(SORTABLE, "displayOrder"));

        return DataTableResponse.from(draw, page, FabricAttributeController::toRow);
    }

    @PostMapping("/api/setup/fabric/{slug}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_SETUP_CREATE') or hasAuthority('SCREEN_FABRIC_SETUP_AMEND')")
    public Map<String, Object> save(@PathVariable String slug,
                                    @Valid @RequestBody FabricAttribute attribute) {
        AuthorityChecks.require(attribute.getId() == null
            ? "SCREEN_FABRIC_SETUP_CREATE" : "SCREEN_FABRIC_SETUP_AMEND");
        AttributeType type = AttributeType.fromSlug(slug);
        FabricAttribute saved = service.save(type, attribute);
        return Map.of("id", saved.getId(), "code", saved.getCode(), "name", saved.getName());
    }

    @DeleteMapping("/api/setup/fabric/{slug}/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_SETUP_DELETE')")
    public Map<String, Object> delete(@PathVariable String slug, @PathVariable Long id) {
        AttributeType.fromSlug(slug);   // validates the route
        service.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> toRow(FabricAttribute a) {
        // LinkedHashMap, not Map.of: the grid renders in declaration order and
        // Map.of gives no ordering guarantee.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("code", a.getCode());
        row.put("name", a.getName());
        row.put("description", a.getDescription() == null ? "" : a.getDescription());
        row.put("displayOrder", a.getDisplayOrder());
        row.put("active", a.getActive());
        return row;
    }
}

package com.asg.fabricerp.fabric.quality;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.fabric.quality.FabricQualityService.ConstructionRequest;
import com.asg.fabricerp.fabric.setup.AttributeType;
import com.asg.fabricerp.fabric.setup.FabricAttributeService;
import com.asg.fabricerp.inventory.item.FiberType;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Fabric qualities - the construction master.
 *
 * <pre>
 *   GET    /setup/qualities                    page
 *   GET    /api/setup/qualities                register rows (DataTables contract)
 *   GET    /api/setup/qualities/{id}           one construction, with its yarns and fibres
 *   POST   /api/setup/qualities                create or amend a construction
 *   DELETE /api/setup/qualities/{id}           soft delete
 *   GET    /api/lookup/constructions           picker feed (LookupPage) - ?q, ?page, ?id
 * </pre>
 *
 * The lookup is open to any signed-in user, like every picker feed: whoever raises a
 * booking needs the qualities without being able to maintain them.
 */
@Controller
public class FabricQualityController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of("code", "code", "name", "name"));

    private final FabricQualityService service;
    private final FabricAttributeService attributes;

    public FabricQualityController(FabricQualityService service, FabricAttributeService attributes) {
        this.service = service;
        this.attributes = attributes;
    }

    @GetMapping("/setup/qualities")
    @PreAuthorize("hasAuthority('SCREEN_QUALITY_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Fabric qualities");
        model.addAttribute("weaveTypes", attributes.lookup(AttributeType.WEAVE_TYPE));
        model.addAttribute("weaveStyles", attributes.lookup(AttributeType.WEAVE_STYLE));
        model.addAttribute("finishTypes", attributes.lookup(AttributeType.FINISH_TYPE));
        model.addAttribute("fiberTypes", FiberType.values());
        model.addAttribute("content", "setup/qualities :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/qualities")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_QUALITY_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return DataTableResponse.from(draw,
            service.search(request.searchOrNull(), includeInactive, request.toPageable(SORTABLE, "code")),
            row -> row);
    }

    @GetMapping("/api/setup/qualities/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_QUALITY_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/setup/qualities")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_QUALITY_CREATE') or hasAuthority('SCREEN_QUALITY_AMEND')")
    public Map<String, Object> save(@RequestBody ConstructionRequest request) {
        AuthorityChecks.require(request.id() == null ? "SCREEN_QUALITY_CREATE" : "SCREEN_QUALITY_AMEND");
        return service.save(request);
    }

    @DeleteMapping("/api/setup/qualities/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_QUALITY_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }


    // ================================================================================ pickers

    @GetMapping("/api/lookup/constructions")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public LookupPage<LookupPage.Option> constructions(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size,
                                                       @RequestParam(required = false) Long id) {
        return id != null ? service.option(id) : service.lookup(q, page, size);
    }

}

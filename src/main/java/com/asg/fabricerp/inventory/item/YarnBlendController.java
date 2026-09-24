package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.inventory.item.YarnBlendService.BlendRequest;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Yarn blends and their fiber components. Governed by the {@code ITEM_SETUP} screen grant. */
@Controller
public class YarnBlendController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "code", "code", "name", "name", "active", "active"));

    private final YarnBlendService service;

    public YarnBlendController(YarnBlendService service) {
        this.service = service;
    }

    @GetMapping("/inventory/yarn-blends")
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Yarn blends");
        model.addAttribute("certifications", Certification.values());
        model.addAttribute("content", "inventory/yarn-blends :: content");
        return "layout/main";
    }

    @GetMapping("/api/inventory/yarn-blends")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return DataTableResponse.from(draw,
            service.search(request.searchOrNull(), request.toPageable(SORTABLE, "name")), row -> row);
    }

    @GetMapping("/api/inventory/yarn-blends/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/inventory/yarn-blends")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_CREATE') or hasAuthority('SCREEN_ITEM_SETUP_AMEND')")
    public Map<String, Object> save(@RequestBody BlendRequest request) {
        ItemSetupController.requireWrite(request.id());
        return service.save(request);
    }

    @PostMapping("/api/inventory/yarn-blends/{id}/approve")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_APPROVE')")
    public Map<String, Object> approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @DeleteMapping("/api/inventory/yarn-blends/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }
}

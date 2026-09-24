package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.inventory.item.InventoryItemService.ItemRequest;
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
 * The item master. One screen for every item type - SpindleERP and the legacy system both split
 * it by type (General Item, Fiber Item, Yarn Item, Dyes Chemical, MRO Item, IT &amp; Electrical FA
 * Item, Finished Fabrics) over the same table; here the type picks which section of the editor
 * shows, and the grid filters by it.
 */
@Controller
public class InventoryItemController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "itemCode", "itemCode",
        "name", "name",
        "itemType", "itemType",
        "categoryName", "category.name",
        "active", "active"));

    private final InventoryItemService service;

    public InventoryItemController(InventoryItemService service) {
        this.service = service;
    }

    @GetMapping("/inventory/items")
    @PreAuthorize("hasAuthority('SCREEN_ITEM_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Items");
        model.addAttribute("itemTypes", ItemType.values());
        model.addAttribute("fiberTypes", FiberType.values());
        model.addAttribute("content", "inventory/items :: content");
        return "layout/main";
    }

    @GetMapping("/api/inventory/items")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return DataTableResponse.from(draw,
            service.search(itemType, categoryId, active, request.searchOrNull(), request.toPageable(SORTABLE, "itemCode")),
            row -> row);
    }

    @GetMapping("/api/inventory/items/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/inventory/items")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_CREATE') or hasAuthority('SCREEN_ITEM_AMEND')")
    public Map<String, Object> save(@RequestBody ItemRequest request) {
        AuthorityChecks.require(request.id() == null ? "SCREEN_ITEM_CREATE" : "SCREEN_ITEM_AMEND");
        return service.save(request);
    }

    @PostMapping("/api/inventory/items/{id}/approve")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_APPROVE')")
    public Map<String, Object> approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @DeleteMapping("/api/inventory/items/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }
}

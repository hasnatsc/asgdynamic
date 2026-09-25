package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.inventory.item.ItemCategoryService.CategoryRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The item category tree. Governed by the {@code ITEM_SETUP} screen grant. */
@Controller
public class ItemCategoryController {

    private final ItemCategoryService service;

    public ItemCategoryController(ItemCategoryService service) {
        this.service = service;
    }

    @GetMapping("/inventory/categories")
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Item categories");
        model.addAttribute("itemTypes", ItemType.values());
        model.addAttribute("content", "inventory/categories :: content");
        return "layout/main";
    }

    /** The whole tree, depth-first. Small enough not to page. */
    @GetMapping("/api/inventory/categories")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public List<Map<String, Object>> tree() {
        return service.tree();
    }

    /**
     * Parent choices for the editor's picker, paged: ?q=cot&amp;page=2, and forId when moving an
     * existing category; ?id=7 labels a saved parent.
     */
    @GetMapping("/api/inventory/categories/parents")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public LookupPage<LookupPage.Option> parents(@RequestParam(required = false) Long forId,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) Long id) {
        return id != null ? service.optionFor(id) : service.parentLookup(forId, q, page, size);
    }

    @GetMapping("/api/inventory/categories/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/inventory/categories")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_CREATE') or hasAuthority('SCREEN_ITEM_SETUP_AMEND')")
    public Map<String, Object> save(@RequestBody CategoryRequest request) {
        ItemSetupController.requireWrite(request.id());
        return service.save(request);
    }

    @DeleteMapping("/api/inventory/categories/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_SETUP_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }
}

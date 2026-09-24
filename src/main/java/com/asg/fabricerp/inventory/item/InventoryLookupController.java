package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.inventory.item.Masters.Option;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Dropdown feeds for every item master, for this module's editors and for any document screen
 * that picks an item or unit. Active rows only. Like {@code FabricLookupController}, open to
 * any signed-in user: a Booking clerk needs the unit list without being granted item setup.
 */
@RestController
@RequestMapping("/api/lookup/inventory")
@PreAuthorize("isAuthenticated()")
public class InventoryLookupController {

    private final UnitOfMeasureService uoms;
    private final HsCodeService hsCodes;
    private final ItemBrandService brands;
    private final ItemModelService models;
    private final YarnTypeService yarnTypes;
    private final YarnCountService yarnCounts;
    private final YarnPlyService yarnPlies;
    private final YarnBlendService yarnBlends;
    private final ItemCategoryService categories;
    private final InventoryItemService items;

    public InventoryLookupController(UnitOfMeasureService uoms, HsCodeService hsCodes, ItemBrandService brands,
                                     ItemModelService models, YarnTypeService yarnTypes,
                                     YarnCountService yarnCounts, YarnPlyService yarnPlies,
                                     YarnBlendService yarnBlends, ItemCategoryService categories,
                                     InventoryItemService items) {
        this.uoms = uoms;
        this.hsCodes = hsCodes;
        this.brands = brands;
        this.models = models;
        this.yarnTypes = yarnTypes;
        this.yarnCounts = yarnCounts;
        this.yarnPlies = yarnPlies;
        this.yarnBlends = yarnBlends;
        this.categories = categories;
        this.items = items;
    }

    @GetMapping("/uoms")        public List<Option> uoms()        { return uoms.lookup(); }
    @GetMapping("/hs-codes")    public List<Option> hsCodes()     { return hsCodes.lookup(); }
    @GetMapping("/brands")      public List<Option> brands()      { return brands.lookup(); }
    @GetMapping("/yarn-types")  public List<Option> yarnTypes()   { return yarnTypes.lookup(); }
    @GetMapping("/yarn-counts") public List<Option> yarnCounts()  { return yarnCounts.lookup(); }
    @GetMapping("/yarn-plies")  public List<Option> yarnPlies()   { return yarnPlies.lookup(); }
    @GetMapping("/yarn-blends") public List<Option> yarnBlends()  { return yarnBlends.lookup(); }

    /** e.g. /api/lookup/inventory/models?brandId=3 */
    @GetMapping("/models")
    public List<Option> models(@RequestParam(required = false) Long brandId) {
        return models.lookup(brandId);
    }

    /** Item-level categories only - the ones an item may be filed under. */
    @GetMapping("/categories")
    public List<Option> categories(@RequestParam(required = false) ItemType itemType) {
        return categories.itemCategoryLookup(itemType);
    }

    /** e.g. /api/lookup/inventory/items?itemType=FIBER&amp;q=cot - at most 50 rows. */
    @GetMapping("/items")
    public List<Map<String, Object>> items(@RequestParam(required = false) ItemType itemType,
                                           @RequestParam(required = false) String q) {
        return items.lookup(itemType, q);
    }
}

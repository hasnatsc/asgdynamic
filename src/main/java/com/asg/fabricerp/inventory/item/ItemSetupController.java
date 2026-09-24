package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.inventory.item.HsCodeService.HsCodeRequest;
import com.asg.fabricerp.inventory.item.ItemBrandService.BrandRequest;
import com.asg.fabricerp.inventory.item.ItemModelService.ModelRequest;
import com.asg.fabricerp.inventory.item.MasterPage.Choice;
import com.asg.fabricerp.inventory.item.MasterPage.Column;
import com.asg.fabricerp.inventory.item.MasterPage.Field;
import com.asg.fabricerp.inventory.item.UnitOfMeasureService.UomRequest;
import com.asg.fabricerp.inventory.item.YarnCountService.YarnCountRequest;
import com.asg.fabricerp.inventory.item.YarnPlyService.YarnPlyRequest;
import com.asg.fabricerp.inventory.item.YarnTypeService.YarnTypeRequest;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The seven simple item reference lists: units of measure, HS codes, brands, models, yarn
 * types, yarn counts and yarn plies. All sit behind the one {@code ITEM_SETUP} screen grant, all
 * render through {@code inventory/master}, and each follows the same routes:
 *
 * <pre>
 *   GET    /inventory/{list}                 page
 *   GET    /api/inventory/{list}             grid rows
 *   GET    /api/inventory/{list}/{id}        one row
 *   POST   /api/inventory/{list}             create or update
 *   POST   /api/inventory/{list}/{id}/approve  (approvable lists only)
 *   DELETE /api/inventory/{list}/{id}        soft delete
 * </pre>
 *
 * SpindleERP spreads the same thing over seven controllers, seven services with interfaces,
 * seven DTOs and seven DataTable services.
 */
@Controller
public class ItemSetupController {

    private static final String VIEW    = "hasAuthority('SCREEN_ITEM_SETUP_VIEW')";
    private static final String WRITE   = "hasAuthority('SCREEN_ITEM_SETUP_CREATE') or hasAuthority('SCREEN_ITEM_SETUP_AMEND')";
    private static final String DELETE  = "hasAuthority('SCREEN_ITEM_SETUP_DELETE')";
    private static final String APPROVE = "hasAuthority('SCREEN_ITEM_SETUP_APPROVE')";

    private static final SortWhitelist CODE_NAME = SortWhitelist.of(Map.of(
        "code", "code", "name", "name", "active", "active"));

    private final UnitOfMeasureService uoms;
    private final HsCodeService hsCodes;
    private final ItemBrandService brands;
    private final ItemModelService models;
    private final YarnTypeService yarnTypes;
    private final YarnCountService yarnCounts;
    private final YarnPlyService yarnPlies;

    public ItemSetupController(UnitOfMeasureService uoms, HsCodeService hsCodes, ItemBrandService brands,
                               ItemModelService models, YarnTypeService yarnTypes,
                               YarnCountService yarnCounts, YarnPlyService yarnPlies) {
        this.uoms = uoms;
        this.hsCodes = hsCodes;
        this.brands = brands;
        this.models = models;
        this.yarnTypes = yarnTypes;
        this.yarnCounts = yarnCounts;
        this.yarnPlies = yarnPlies;
    }

    // =============================================================================================
    // Pages
    // =============================================================================================

    private static final MasterPage UOM_PAGE = new MasterPage(
        "Units of measure", "Units items are counted, weighed or measured in. Factors are relative to the category's base unit.",
        "/api/inventory/uoms", "unit", false,
        List.of(Column.mono("code", "Code"), Column.of("name", "Name"), Column.plain("symbol", "Symbol"),
                Column.of("category", "Category"), Column.yesNo("baseUnit", "Base"),
                Column.plain("conversionFactor", "Factor"), Column.active()),
        List.of(Field.text("code", "Code", 20, true), Field.text("name", "Name", 100, true),
                Field.text("symbol", "Symbol", 20, false),
                Field.select("category", "Category", Choice.of(UomCategory.class), true),
                Field.number("conversionFactor", "Conversion factor", "any", true)
                    .hint("Relative to the base unit of the same category: Gram is 0.001 when Kilogram is the base."),
                Field.check("baseUnit", "Base unit of its category")));

    private static final MasterPage HS_PAGE = new MasterPage(
        "HS codes", "Customs tariff headings and the duty rates that apply to them.",
        "/api/inventory/hs-codes", "HS code", false,
        List.of(Column.mono("hsCode", "HS code"), Column.plain("shortDescription", "Short description"),
                Column.of("hsType", "Type"), Column.number("customsDutyPercent", "CD %"),
                Column.number("vatPercent", "VAT %"), Column.active()),
        List.of(Field.text("hsCode", "HS code", 20, true),
                Field.select("hsType", "Type", Choice.of(HsCode.HsType.class), true),
                Field.text("shortDescription", "Short description", 200, false),
                Field.number("customsDutyPercent", "Customs duty %", "0.01", false),
                Field.number("vatPercent", "VAT %", "0.01", false),
                Field.number("supplementaryDutyPercent", "Supplementary duty %", "0.01", false),
                Field.number("aitPercent", "AIT %", "0.01", false),
                Field.check("bondedAllowed", "Bonded import allowed"),
                Field.check("requiresExportPermit", "Requires export permit"),
                Field.check("requiresImportPermit", "Requires import permit"),
                Field.area("description", "Description")));

    private static final MasterPage BRAND_PAGE = new MasterPage(
        "Brands", "Manufacturer brands items and models are recorded against.",
        "/api/inventory/brands", "brand", true,
        List.of(Column.mono("code", "Code"), Column.of("name", "Name"), Column.plain("shortName", "Short"),
                Column.plain("countryOfOrigin", "Origin"), Column.approval(), Column.active()),
        List.of(Field.text("name", "Brand name", 150, true), Field.text("shortName", "Short name", 50, false),
                Field.text("countryOfOrigin", "Country of origin", 100, false), Field.area("description", "Description")));

    private static final MasterPage MODEL_PAGE = new MasterPage(
        "Models", "Models, optionally under a brand.",
        "/api/inventory/models", "model", true,
        List.of(Column.mono("code", "Code"), Column.of("name", "Name"), Column.plain("brandName", "Brand"),
                Column.plain("shortName", "Short"), Column.approval(), Column.active()),
        List.of(Field.lookup("brandId", "Brand", "/api/lookup/inventory/brands", false),
                Field.text("name", "Model name", 150, true), Field.text("shortName", "Short name", 50, false),
                Field.area("description", "Description")));

    private static final MasterPage YARN_TYPE_PAGE = new MasterPage(
        "Yarn types", "How a yarn is spun or processed. The short name goes into generated yarn names.",
        "/api/inventory/yarn-types", "yarn type", true,
        List.of(Column.mono("code", "Code"), Column.of("name", "Name"), Column.plain("shortName", "Short"),
                Column.approval(), Column.active()),
        List.of(Field.text("name", "Name", 100, true),
                Field.text("shortName", "Short name", 30, false).hint("e.g. CD for Carded - used in \"30/1 CD 100% Cotton\"."),
                Field.area("description", "Description")));

    private static final MasterPage YARN_COUNT_PAGE = new MasterPage(
        "Yarn counts", "Yarn counts, as they appear at the start of a yarn name.",
        "/api/inventory/yarn-counts", "yarn count", true,
        List.of(Column.mono("code", "Code"), Column.of("name", "Count"), Column.approval(), Column.active()),
        List.of(Field.text("name", "Count", 100, true), Field.area("description", "Description")));

    private static final MasterPage YARN_PLY_PAGE = new MasterPage(
        "Yarn plies", "Number of strands twisted together.",
        "/api/inventory/yarn-plies", "yarn ply", true,
        List.of(Column.mono("code", "Code"), Column.number("plyNumber", "Ply"), Column.of("name", "Name"),
                Column.approval(), Column.active()),
        List.of(Field.number("plyNumber", "Ply number", "1", true), Field.text("name", "Name", 50, true),
                Field.area("description", "Description")));

    @GetMapping("/inventory/uoms")        @PreAuthorize(VIEW) public String uomPage(Model m)       { return page(m, UOM_PAGE); }
    @GetMapping("/inventory/hs-codes")    @PreAuthorize(VIEW) public String hsPage(Model m)        { return page(m, HS_PAGE); }
    @GetMapping("/inventory/brands")      @PreAuthorize(VIEW) public String brandPage(Model m)     { return page(m, BRAND_PAGE); }
    @GetMapping("/inventory/models")      @PreAuthorize(VIEW) public String modelPage(Model m)     { return page(m, MODEL_PAGE); }
    @GetMapping("/inventory/yarn-types")  @PreAuthorize(VIEW) public String yarnTypePage(Model m)  { return page(m, YARN_TYPE_PAGE); }
    @GetMapping("/inventory/yarn-counts") @PreAuthorize(VIEW) public String yarnCountPage(Model m) { return page(m, YARN_COUNT_PAGE); }
    @GetMapping("/inventory/yarn-plies")  @PreAuthorize(VIEW) public String yarnPlyPage(Model m)   { return page(m, YARN_PLY_PAGE); }

    private static String page(Model model, MasterPage page) {
        model.addAttribute("title", page.title());
        model.addAttribute("page", page);
        model.addAttribute("content", "inventory/master :: content");
        return "layout/main";
    }

    // =============================================================================================
    // Units of measure
    // =============================================================================================

    private static final SortWhitelist UOM_SORT = SortWhitelist.of(Map.of(
        "code", "code", "name", "name", "category", "category", "active", "active"));

    @GetMapping("/api/inventory/uoms") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> uomGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, uoms.search(request.searchOrNull(), request.toPageable(UOM_SORT, "code")));
    }

    @GetMapping("/api/inventory/uoms/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> uomDetail(@PathVariable Long id) { return uoms.detail(id); }

    @PostMapping("/api/inventory/uoms") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> uomSave(@RequestBody UomRequest request) {
        requireWrite(request.id());
        return uoms.save(request);
    }

    @DeleteMapping("/api/inventory/uoms/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> uomDelete(@PathVariable Long id) {
        uoms.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================
    // HS codes
    // =============================================================================================

    private static final SortWhitelist HS_SORT = SortWhitelist.of(Map.of(
        "hsCode", "hsCode", "hsType", "hsType", "customsDutyPercent", "customsDutyPercent",
        "vatPercent", "vatPercent", "active", "active"));

    @GetMapping("/api/inventory/hs-codes") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> hsGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, hsCodes.search(request.searchOrNull(), request.toPageable(HS_SORT, "hsCode")));
    }

    @GetMapping("/api/inventory/hs-codes/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> hsDetail(@PathVariable Long id) { return hsCodes.detail(id); }

    @PostMapping("/api/inventory/hs-codes") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> hsSave(@RequestBody HsCodeRequest request) {
        requireWrite(request.id());
        return hsCodes.save(request);
    }

    @DeleteMapping("/api/inventory/hs-codes/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> hsDelete(@PathVariable Long id) {
        hsCodes.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================
    // Brands
    // =============================================================================================

    @GetMapping("/api/inventory/brands") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> brandGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, brands.search(request.searchOrNull(), request.toPageable(CODE_NAME, "name")));
    }

    @GetMapping("/api/inventory/brands/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> brandDetail(@PathVariable Long id) { return brands.detail(id); }

    @PostMapping("/api/inventory/brands") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> brandSave(@RequestBody BrandRequest request) {
        requireWrite(request.id());
        return brands.save(request);
    }

    @PostMapping("/api/inventory/brands/{id}/approve") @ResponseBody @PreAuthorize(APPROVE)
    public Map<String, Object> brandApprove(@PathVariable Long id) { return brands.approve(id); }

    @DeleteMapping("/api/inventory/brands/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> brandDelete(@PathVariable Long id) {
        brands.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================
    // Models
    // =============================================================================================

    @GetMapping("/api/inventory/models") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> modelGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Long brandId) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, models.search(brandId, request.searchOrNull(), request.toPageable(CODE_NAME, "name")));
    }

    @GetMapping("/api/inventory/models/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> modelDetail(@PathVariable Long id) { return models.detail(id); }

    @PostMapping("/api/inventory/models") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> modelSave(@RequestBody ModelRequest request) {
        requireWrite(request.id());
        return models.save(request);
    }

    @PostMapping("/api/inventory/models/{id}/approve") @ResponseBody @PreAuthorize(APPROVE)
    public Map<String, Object> modelApprove(@PathVariable Long id) { return models.approve(id); }

    @DeleteMapping("/api/inventory/models/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> modelDelete(@PathVariable Long id) {
        models.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================
    // Yarn types
    // =============================================================================================

    @GetMapping("/api/inventory/yarn-types") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> yarnTypeGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, yarnTypes.search(request.searchOrNull(), request.toPageable(CODE_NAME, "name")));
    }

    @GetMapping("/api/inventory/yarn-types/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> yarnTypeDetail(@PathVariable Long id) { return yarnTypes.detail(id); }

    @PostMapping("/api/inventory/yarn-types") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> yarnTypeSave(@RequestBody YarnTypeRequest request) {
        requireWrite(request.id());
        return yarnTypes.save(request);
    }

    @PostMapping("/api/inventory/yarn-types/{id}/approve") @ResponseBody @PreAuthorize(APPROVE)
    public Map<String, Object> yarnTypeApprove(@PathVariable Long id) { return yarnTypes.approve(id); }

    @DeleteMapping("/api/inventory/yarn-types/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> yarnTypeDelete(@PathVariable Long id) {
        yarnTypes.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================
    // Yarn counts
    // =============================================================================================

    @GetMapping("/api/inventory/yarn-counts") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> yarnCountGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, yarnCounts.search(request.searchOrNull(), request.toPageable(CODE_NAME, "name")));
    }

    @GetMapping("/api/inventory/yarn-counts/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> yarnCountDetail(@PathVariable Long id) { return yarnCounts.detail(id); }

    @PostMapping("/api/inventory/yarn-counts") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> yarnCountSave(@RequestBody YarnCountRequest request) {
        requireWrite(request.id());
        return yarnCounts.save(request);
    }

    @PostMapping("/api/inventory/yarn-counts/{id}/approve") @ResponseBody @PreAuthorize(APPROVE)
    public Map<String, Object> yarnCountApprove(@PathVariable Long id) { return yarnCounts.approve(id); }

    @DeleteMapping("/api/inventory/yarn-counts/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> yarnCountDelete(@PathVariable Long id) {
        yarnCounts.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================
    // Yarn plies
    // =============================================================================================

    private static final SortWhitelist PLY_SORT = SortWhitelist.of(Map.of(
        "code", "code", "plyNumber", "plyNumber", "name", "name", "active", "active"));

    @GetMapping("/api/inventory/yarn-plies") @ResponseBody @PreAuthorize(VIEW)
    public DataTableResponse<Map<String, Object>> yarnPlyGrid(
            @RequestParam(defaultValue = "1") int draw, @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn, @RequestParam(required = false) String sortDir) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return grid(draw, yarnPlies.search(request.searchOrNull(), request.toPageable(PLY_SORT, "plyNumber")));
    }

    @GetMapping("/api/inventory/yarn-plies/{id}") @ResponseBody @PreAuthorize(VIEW)
    public Map<String, Object> yarnPlyDetail(@PathVariable Long id) { return yarnPlies.detail(id); }

    @PostMapping("/api/inventory/yarn-plies") @ResponseBody @PreAuthorize(WRITE)
    public Map<String, Object> yarnPlySave(@RequestBody YarnPlyRequest request) {
        requireWrite(request.id());
        return yarnPlies.save(request);
    }

    @PostMapping("/api/inventory/yarn-plies/{id}/approve") @ResponseBody @PreAuthorize(APPROVE)
    public Map<String, Object> yarnPlyApprove(@PathVariable Long id) { return yarnPlies.approve(id); }

    @DeleteMapping("/api/inventory/yarn-plies/{id}") @ResponseBody @PreAuthorize(DELETE)
    public Map<String, Object> yarnPlyDelete(@PathVariable Long id) {
        yarnPlies.delete(id);
        return Map.of("deleted", id);
    }

    // =============================================================================================

    /** One POST serves create and amend; which verb it needs depends on whether an id came in. */
    static void requireWrite(Long id) {
        AuthorityChecks.require(id == null ? "SCREEN_ITEM_SETUP_CREATE" : "SCREEN_ITEM_SETUP_AMEND");
    }

    private static DataTableResponse<Map<String, Object>> grid(int draw, Page<Map<String, Object>> page) {
        return DataTableResponse.from(draw, page, row -> row);
    }
}

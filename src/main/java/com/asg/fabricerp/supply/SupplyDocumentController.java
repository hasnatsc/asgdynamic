package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.PurchaseType;
import com.asg.fabricerp.global.documents.RequisitionType;
import com.asg.fabricerp.inventory.item.ItemType;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * The purchase and store screens - store and purchase requisitions, purchase orders, MRRs and
 * returns, material issues, direct receives, transfers, adjustments and fabric transfers - on one
 * controller, each at its own path and guarded by its own screen's verbs ({@link SupplyAccess}).
 * Submit, approve, return and reject stay on /api/documents/{id}/…
 */
@Controller
public class SupplyDocumentController {

    static final String SLUGS = "store-requisition|purchase-requisition|purchase-order|mrr|purchase-return|material-issue"
        + "|direct-receive|transfer-request|transfer-issue|transfer-receive|stock-adjustment|fabric-transfer-issue"
        + "|fabric-transfer-receive";

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo", "documentNo",
        "documentDate", "documentDate",
        "requiredDate", "requiredDate",
        "status", "status",
        "totalQuantity", "totalQuantity",
        "subtotalAmount", "subtotalAmount"));

    /** What each screen's open documents can be carried on to, with the icon the button shows. */
    private static final Map<SupplyStep, String> ICONS = Map.ofEntries(
        Map.entry(SupplyStep.SPR, "document"), Map.entry(SupplyStep.PO, "credit-card"), Map.entry(SupplyStep.MRR, "receive"),
        Map.entry(SupplyStep.PRT, "refresh"), Map.entry(SupplyStep.MI, "arrow-up-right"), Map.entry(SupplyStep.TI, "transfer"),
        Map.entry(SupplyStep.TRC, "receive"), Map.entry(SupplyStep.FTR, "packing"));

    private final SupplyDocumentService documents;
    private final SupplyPostingService posting;
    private final SupplyViews views;
    private final SupplyQueries queries;
    private final WarehouseRepository warehouses;
    private final OrgContext context;

    public SupplyDocumentController(SupplyDocumentService documents, SupplyPostingService posting, SupplyViews views,
                                    SupplyQueries queries, WarehouseRepository warehouses, OrgContext context) {
        this.documents = documents;
        this.posting = posting;
        this.views = views;
        this.queries = queries;
        this.warehouses = warehouses;
        this.context = context;
    }

    // ------------------------------------------------------------------------------------ page

    @GetMapping("/{slug:" + SLUGS + "}")
    @PreAuthorize("@supplyAccess.can(#slug, 'VIEW')")
    public String page(@PathVariable String slug, Model model) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("step", step.name());
        config.put("slug", slug);
        config.put("label", step.label());
        config.put("plural", step.plural());
        config.put("api", "/api/" + slug);
        config.put("parentLabel", step.hasParent() ? SupplyStep.of(step.parentType()).map(SupplyStep::label).orElse(step.parentType().label()) : null);
        config.put("parentSlug", step.hasParent() ? SupplyStep.of(step.parentType()).map(SupplyStep::slug).orElse(null) : null);
        config.put("posting", step.movesStock());
        config.put("direct", step.allowsDirect());
        config.put("requiresParent", step.requiresParent());
        config.put("fabricLots", step.lines() == SupplyStep.Lines.FABRIC_LOT);
        config.put("priced", step.isPriced());
        config.put("supplier", step.namesSupplier());
        config.put("transfer", step.isTransfer());
        config.put("needsStore", step.needsStore());
        config.put("canCreate", AuthorityChecks.holds(step.authority("CREATE")));
        config.put("canAmend", AuthorityChecks.holds(step.authority("AMEND")));
        config.put("canDelete", AuthorityChecks.holds(step.authority("DELETE")));
        config.put("defaultStoreId", context.warehouseId());
        config.put("purchaseTypes", Arrays.stream(PurchaseType.values()).map(t -> Map.of("value", t.name(), "label", t.label())).toList());
        config.put("requisitionTypes", Arrays.stream(RequisitionType.values()).map(t -> Map.of("value", t.name(), "label", t.label())).toList());
        config.put("next", SupplyStep.childrenOf(step.type()).stream()
            .filter(c -> AuthorityChecks.holds(c.authority("CREATE")))
            .map(c -> Map.of("slug", c.slug(), "label", c.label(), "icon", ICONS.getOrDefault(c, "document"))).toList());
        model.addAttribute("title", step.plural());
        model.addAttribute("step", step);
        model.addAttribute("config", config);
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        model.addAttribute("content", "supply/documents :: content");
        return "layout/main";
    }

    // ------------------------------------------------------------------------------------ read

    @GetMapping("/api/{slug:" + SLUGS + "}")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'VIEW')")
    @Transactional(readOnly = true)
    public DataTableResponse<Map<String, Object>> grid(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) BusinessDocumentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        var page = documents.search(step, status, from, to, request.searchOrNull(), request.toPageable(SORTABLE, "documentDate"));
        return DataTableResponse.from(draw, page, views::gridRow);
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'VIEW')")
    public Map<String, Object> detail(@PathVariable String slug, @PathVariable Long id) {
        return views.detail(SupplyStep.ofSlug(slug), id);
    }

    /** The documents this screen's documents may be raised against: approved and open. */
    @GetMapping("/api/{slug:" + SLUGS + "}/parents")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'VIEW')")
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> parents(@PathVariable String slug,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Long id,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        int s = size == null || size < 1 ? LookupPage.DEFAULT_SIZE : Math.min(size, LookupPage.MAX_SIZE);
        int p = page == null || page < 1 ? 0 : page - 1;
        List<BusinessDocument> found = documents.parents(step, id == null ? q : null, id == null ? p : 0, id == null ? s : 500);
        if (id != null) found = found.stream().filter(d -> d.getId().equals(id)).toList();
        boolean more = id == null && found.size() > s;
        return LookupPage.of(found.stream().limit(s).map(d -> new LookupPage.Option(d.getId(), d.getStatus().label(),
            d.getDocumentNo(), String.join(" · ", Arrays.stream(new String[] {
                d.getParty() == null ? null : d.getParty().getName(),
                d.getWarehouse() == null ? null : d.getWarehouse().getName() + (d.getToWarehouse() == null ? "" : " → " + d.getToWarehouse().getName()),
                d.getDocumentDate() == null ? null : d.getDocumentDate().toString() }).filter(Objects::nonNull).toList()))).toList(), more);
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/open-lines")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'VIEW')")
    public Map<String, Object> openLines(@PathVariable String slug, @RequestParam Long parentId,
                                         @RequestParam(required = false) Long exclude) {
        return documents.openLines(SupplyStep.ofSlug(slug), parentId, exclude);
    }

    // ---------------------------------------------------------------------------- shared lookups

    /** Items for a line picker, with what the chosen store holds of each. */
    @GetMapping("/api/supply/items")
    @ResponseBody
    @PreAuthorize("@supplyAccess.anyScreen()")
    public Map<String, Object> items(@RequestParam(required = false) String q, @RequestParam(required = false) ItemType itemType,
                                     @RequestParam(required = false) Long warehouseId,
                                     @RequestParam(defaultValue = "false") boolean stockOnly,
                                     @RequestParam(defaultValue = "false") boolean stockItems,
                                     @RequestParam(required = false) Long id,
                                     @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        int s = size == null || size < 1 ? LookupPage.DEFAULT_SIZE : Math.min(size, LookupPage.MAX_SIZE);
        return queries.itemOptions(q, itemType, warehouseId, stockOnly, stockItems, id, page == null || page < 1 ? 0 : page - 1, s);
    }

    /** Fabric lots a store holds, free to move. */
    @GetMapping("/api/supply/fabric-lots")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can('fabric-transfer-issue', 'VIEW')")
    public Map<String, Object> fabricLots(@RequestParam Long warehouseId, @RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long id,
                                          @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        int s = size == null || size < 1 ? LookupPage.DEFAULT_SIZE : Math.min(size, LookupPage.MAX_SIZE);
        return queries.fabricLots(warehouseId, q, id, page == null || page < 1 ? 0 : page - 1, s);
    }

    /** Every store of the organization, with its fabric roles. */
    @GetMapping("/api/supply/stores")
    @ResponseBody
    @PreAuthorize("@supplyAccess.anyScreen()")
    public List<Map<String, Object>> stores() {
        return warehouses.lookup(context.requireOrganizationId()).stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("code", w.getCode());
            m.put("name", w.getName());
            m.put("holdsGreige", w.isHoldsGreige());
            m.put("holdsFinished", w.isHoldsFinished());
            return m;
        }).toList();
    }

    // ----------------------------------------------------------------------------------- write

    @PostMapping("/api/{slug:" + SLUGS + "}")
    @ResponseBody
    @PreAuthorize("@supplyAccess.canAny(#slug, 'CREATE', 'AMEND')")
    public Map<String, Object> save(@PathVariable String slug, @RequestBody SupplyDocumentRequest request) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        AuthorityChecks.require(step.authority(request.id() == null ? "CREATE" : "AMEND"));
        return views.detail(step, documents.save(step, request).getId());
    }

    @DeleteMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'DELETE')")
    public Map<String, Object> delete(@PathVariable String slug, @PathVariable Long id) {
        documents.delete(SupplyStep.ofSlug(slug), id);
        return Map.of("deleted", id);
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/post")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'CREATE')")
    public Map<String, Object> post(@PathVariable String slug, @PathVariable Long id) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        return views.detail(step, posting.post(step, id).getId());
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/cancel")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'AMEND')")
    public Map<String, Object> cancel(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String reason) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        return views.detail(step, posting.cancel(step, id, reason).getId());
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/close")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'AMEND')")
    public Map<String, Object> close(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String remarks) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        return views.detail(step, posting.close(step, id, remarks).getId());
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/lines/{lineId:\\d+}/short-close")
    @ResponseBody
    @PreAuthorize("@supplyAccess.can(#slug, 'AMEND')")
    public Map<String, Object> shortClose(@PathVariable String slug, @PathVariable Long lineId, @RequestParam(required = false) String reason) {
        SupplyStep step = SupplyStep.ofSlug(slug);
        return views.detail(step, posting.shortClose(step, lineId, reason).getId());
    }
}

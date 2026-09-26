package com.asg.fabricerp.production;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.ProcessKind;
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
 * The nine production-chain screens - production order, weaving and dyeing work orders, greige
 * receive and issue, finished receive, delivery schedule, delivery order, fabrics delivery - on
 * one controller, each at its own path and guarded by its own screen's verbs
 * ({@link ChainAccess}). Submit, approve, return and reject stay on /api/documents/{id}/…
 */
@Controller
public class ChainDocumentController {

    static final String SLUGS = "bpo|weaving-wo|processing-wo|greige-receive|greige-issue|finished-receive|requestforpi|delivery-order|fabrics-delivery";

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo", "documentNo",
        "documentDate", "documentDate",
        "requiredDate", "requiredDate",
        "status", "status",
        "totalQuantity", "totalQuantity",
        "subtotalAmount", "subtotalAmount"));

    private final ChainDocumentService documents;
    private final ChainPostingService posting;
    private final ChainViews views;
    private final WarehouseRepository warehouses;
    private final OrgContext context;

    public ChainDocumentController(ChainDocumentService documents, ChainPostingService posting, ChainViews views,
                                   WarehouseRepository warehouses, OrgContext context) {
        this.documents = documents;
        this.posting = posting;
        this.views = views;
        this.warehouses = warehouses;
        this.context = context;
    }

    // ------------------------------------------------------------------------------------ page

    @GetMapping("/{slug:" + SLUGS + "}")
    @PreAuthorize("@chainAccess.can(#slug, 'VIEW')")
    public String page(@PathVariable String slug, Model model) {
        ChainStep step = ChainStep.ofSlug(slug);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("step", step.name());
        config.put("slug", slug);
        config.put("label", step.label());
        config.put("plural", step.plural());
        config.put("api", "/api/" + slug);
        config.put("parentLabel", ChainStep.of(step.parentType()).map(ChainStep::label).orElse("Booking"));
        config.put("parentSlug", ChainStep.of(step.parentType()).map(ChainStep::slug).orElse("booking"));
        config.put("posting", step.isPosting());
        config.put("revisable", step.isRevisable());
        config.put("canCreate", AuthorityChecks.holds(step.authority("CREATE")));
        config.put("canAmend", AuthorityChecks.holds(step.authority("AMEND")));
        config.put("canDelete", AuthorityChecks.holds(step.authority("DELETE")));
        config.put("defaultStoreId", context.warehouseId());
        config.put("processKinds", Arrays.stream(ProcessKind.values())
            .map(k -> Map.of("value", k.name(), "label", k.label())).toList());
        model.addAttribute("title", step.plural());
        model.addAttribute("step", step);
        model.addAttribute("config", config);
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        model.addAttribute("content", "production/documents :: content");
        return "layout/main";
    }

    // ------------------------------------------------------------------------------------ read

    @GetMapping("/api/{slug:" + SLUGS + "}")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'VIEW')")
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
        ChainStep step = ChainStep.ofSlug(slug);
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        return DataTableResponse.from(draw, documents.search(step, status, from, to, request.searchOrNull(),
            request.toPageable(SORTABLE, "documentDate")), views::gridRow);
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'VIEW')")
    @Transactional(readOnly = true)
    public Map<String, Object> detail(@PathVariable String slug, @PathVariable Long id) {
        return views.detail(ChainStep.ofSlug(slug), id);
    }

    /** The documents this screen's documents may be raised against: approved and open. */
    @GetMapping("/api/{slug:" + SLUGS + "}/parents")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'VIEW')")
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> parents(@PathVariable String slug,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Long id,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        ChainStep step = ChainStep.ofSlug(slug);
        int s = size == null || size < 1 ? LookupPage.DEFAULT_SIZE : Math.min(size, LookupPage.MAX_SIZE);
        int p = page == null || page < 1 ? 0 : page - 1;
        List<BusinessDocument> found = documents.parents(step, id == null ? q : null, id == null ? p : 0, id == null ? s : 500);
        if (id != null) found = found.stream().filter(d -> d.getId().equals(id)).toList();
        boolean more = id == null && found.size() > s;
        return LookupPage.of(found.stream().limit(s).map(d -> new LookupPage.Option(d.getId(), d.getStatus().label(),
            d.getDocumentNo(), (d.getParty() == null ? "" : d.getParty().getName() + " · ")
                + (d.getDocumentDate() == null ? "" : d.getDocumentDate().toString())
                + (d.getProcessKind() == null ? "" : " · " + d.getProcessKind().label()))).toList(), more);
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/open-lines")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'VIEW')")
    public List<Map<String, Object>> openLines(@PathVariable String slug, @RequestParam Long parentId,
                                               @RequestParam(required = false) Long exclude,
                                               @RequestParam(required = false) ProcessKind kind) {
        return documents.openLines(ChainStep.ofSlug(slug), parentId, exclude, kind);
    }

    /** Stores a step may use: greige stores for greige documents, finished for finished ones. */
    @GetMapping("/api/production/stores")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> stores(@RequestParam(required = false) String stage) {
        return warehouses.lookup(context.requireOrganizationId()).stream()
            .filter(w -> stage == null || stage.isBlank()
                || ("GREIGE".equalsIgnoreCase(stage) ? w.isHoldsGreige() : "FINISHED".equalsIgnoreCase(stage) ? w.isHoldsFinished() : true))
            .map(ChainDocumentController::store).toList();
    }

    static Map<String, Object> store(Warehouse w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("code", w.getCode());
        m.put("name", w.getName());
        m.put("holdsGreige", w.isHoldsGreige());
        m.put("holdsFinished", w.isHoldsFinished());
        return m;
    }

    // ----------------------------------------------------------------------------------- write

    @PostMapping("/api/{slug:" + SLUGS + "}")
    @ResponseBody
    @PreAuthorize("@chainAccess.canAny(#slug, 'CREATE', 'AMEND')")
    public Map<String, Object> save(@PathVariable String slug, @RequestBody ChainDocumentRequest request) {
        ChainStep step = ChainStep.ofSlug(slug);
        AuthorityChecks.require(step.authority(request.id() == null ? "CREATE" : "AMEND"));
        return reload(step, documents.save(step, request));
    }

    @DeleteMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'DELETE')")
    public Map<String, Object> delete(@PathVariable String slug, @PathVariable Long id) {
        documents.delete(ChainStep.ofSlug(slug), id);
        return Map.of("deleted", id);
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/post")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'CREATE')")
    public Map<String, Object> post(@PathVariable String slug, @PathVariable Long id) {
        ChainStep step = ChainStep.ofSlug(slug);
        return reload(step, posting.post(step, id));
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/cancel")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'AMEND')")
    public Map<String, Object> cancel(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String reason) {
        ChainStep step = ChainStep.ofSlug(slug);
        return reload(step, posting.cancel(step, id, reason));
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/close")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'AMEND')")
    public Map<String, Object> close(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String remarks) {
        ChainStep step = ChainStep.ofSlug(slug);
        return reload(step, posting.close(step, id, remarks));
    }

    @PostMapping("/api/processing-wo/{id:\\d+}/close-batch")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PWO_AMEND')")
    public Map<String, Object> closeBatch(@PathVariable Long id, @RequestParam(required = false) String remarks) {
        return reload(ChainStep.PWO, posting.closeBatch(id, remarks));
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/revise")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'AMEND')")
    public Map<String, Object> revise(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String reason) {
        ChainStep step = ChainStep.ofSlug(slug);
        return reload(step, documents.revise(step, id, reason));
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/lines/{lineId:\\d+}/short-close")
    @ResponseBody
    @PreAuthorize("@chainAccess.can(#slug, 'AMEND')")
    public Map<String, Object> shortClose(@PathVariable String slug, @PathVariable Long lineId, @RequestParam(required = false) String reason) {
        ChainStep step = ChainStep.ofSlug(slug);
        return reload(step, posting.shortClose(step, lineId, reason));
    }

    /** An approved Booking's "Create production order": one draft per fabric type. */
    @PostMapping("/api/bpo/from-booking/{bookingId:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_BPO_CREATE')")
    public List<Map<String, Object>> fromBooking(@PathVariable Long bookingId) {
        return documents.createFromBooking(bookingId).stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("documentNo", d.getDocumentNo());
            m.put("totalQuantity", d.getTotalQuantity());
            m.put("fabricType", d.getLineGroups().isEmpty() ? null : d.getLineGroups().get(0).getFabric().getFabricType());
            return m;
        }).toList();
    }

    /** The saved document as the screen shows it, read fresh after its own transaction. */
    private Map<String, Object> reload(ChainStep step, BusinessDocument saved) {
        return views.detail(step, saved.getId());
    }
}

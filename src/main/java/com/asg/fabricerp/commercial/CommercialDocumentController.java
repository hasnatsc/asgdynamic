package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.*;
import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentStatusTotal;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * The commercial screens - export PI, LC and CI; import PI and LC - on one controller, each at its
 * own path and guarded by its own screen's verbs ({@link CommercialAccess}). Submit, approve, return
 * and reject stay on /api/documents/{id}/…
 */
@Controller
public class CommercialDocumentController {

    static final String SLUGS = "export-pi|export-lc|export-ci|import-pi|import-lc";

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo", "documentNo",
        "documentDate", "documentDate",
        "status", "status",
        "totalQuantity", "totalQuantity",
        "subtotalAmount", "subtotalAmount"));

    /** The printable papers of each document; a CI's are the legacy CI's print list. */
    static final Map<CommercialStep, List<Map<String, String>>> PRINTS = Map.of(
        CommercialStep.EPI, List.of(Map.of("code", "PI", "label", "Proforma invoice")),
        CommercialStep.ELC, List.of(Map.of("code", "LC", "label", "LC register sheet")),
        CommercialStep.ECI, List.of(
            Map.of("code", "COMMERCIAL_INVOICE", "label", "Commercial invoice"), Map.of("code", "PACKING_LIST", "label", "Packing list"),
            Map.of("code", "DELIVERY_CHALLAN", "label", "Delivery challan"), Map.of("code", "TRUCK_RECEIPT", "label", "Truck receipt"),
            Map.of("code", "BILL_OF_EXCHANGE", "label", "Bill of exchange"), Map.of("code", "BANK_FORWARDING", "label", "Bank forwarding"),
            Map.of("code", "CERTIFICATE_OF_ORIGIN", "label", "Certificate of origin"),
            Map.of("code", "BENEFICIARY_CERTIFICATE", "label", "Beneficiary certificate"),
            Map.of("code", "AZO_FREE_CERTIFICATE", "label", "Azo-free certificate"),
            Map.of("code", "TWENTY_YARD_CERTIFICATE", "label", "Twenty-yard certificate")),
        CommercialStep.IPI, List.of(Map.of("code", "PI", "label", "Import PI")),
        CommercialStep.ILC, List.of(Map.of("code", "LC", "label", "LC sheet")));

    private final CommercialDocumentService documents;
    private final CommercialPostingService posting;
    private final CommercialRecordsService records;
    private final CommercialViews views;
    private final CostHeadRepository costHeads;
    private final DocumentNameRepository documentNames;
    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public CommercialDocumentController(CommercialDocumentService documents, CommercialPostingService posting,
                                        CommercialRecordsService records, CommercialViews views, CostHeadRepository costHeads,
                                        DocumentNameRepository documentNames, NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.documents = documents;
        this.posting = posting;
        this.records = records;
        this.views = views;
        this.costHeads = costHeads;
        this.documentNames = documentNames;
        this.jdbc = jdbc;
        this.context = context;
    }

    // ------------------------------------------------------------------------------------ page

    @GetMapping("/{slug:" + SLUGS + "}")
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
    public String page(@PathVariable String slug, Model model) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("step", step.name());
        config.put("slug", slug);
        config.put("label", step.label());
        config.put("plural", step.plural());
        config.put("api", "/api/" + slug);
        config.put("parentLabel", CommercialViews.labelOf(step.parentType()));
        config.put("direct", step.allowsDirect());
        config.put("manyParents", step.manyParents());
        config.put("revisable", step.isRevisable());
        config.put("canCreate", AuthorityChecks.holds(step.authority("CREATE")));
        config.put("canAmend", AuthorityChecks.holds(step.authority("AMEND")));
        config.put("canDelete", AuthorityChecks.holds(step.authority("DELETE")));
        config.put("tenures", CommercialTerms.options(Tenure.values()));
        config.put("paymentTerms", CommercialTerms.options(PaymentTerms.values()));
        config.put("incoTerms", CommercialTerms.options(IncoTerms.values()));
        config.put("ciKinds", CommercialTerms.options(CiKind.values()));
        config.put("importDocTypes", CommercialTerms.options(ImportDocType.values()));
        config.put("lcTypes", CommercialTerms.options(LcType.values()));
        config.put("realizationSteps", CommercialTerms.options(RealizationStep.values()));
        config.put("materialTypes", CommercialTerms.MATERIAL_TYPES);
        config.put("ports", CommercialTerms.PORTS);
        config.put("prints", PRINTS.get(step));
        config.put("selfPartyId", documents.selfPartyId());
        List<Map<String, Object>> next = new ArrayList<>();
        switch (step) {
            case EPI -> next.add(Map.of("slug", "export-lc", "label", "Export LC", "icon", "credit-card"));
            case ELC -> next.add(Map.of("slug", "export-ci", "label", "Export CI", "icon", "clipboard-check"));
            case IPI -> {
                next.add(Map.of("slug", "import-lc", "label", "Import LC", "icon", "globe"));
                next.add(Map.of("slug", "purchase-order", "label", "Purchase order", "icon", "credit-card"));
            }
            default -> { }
        }
        config.put("next", next);
        model.addAttribute("title", step.plural());
        model.addAttribute("step", step);
        model.addAttribute("config", config);
        model.addAttribute("statuses", BusinessDocumentStatus.values());
        model.addAttribute("content", "commercial/documents :: content");
        return "layout/main";
    }

    // ------------------------------------------------------------------------------------ read

    @GetMapping("/api/{slug:" + SLUGS + "}")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
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
        CommercialStep step = CommercialStep.ofSlug(slug);
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        var page = documents.search(step, status, from, to, request.searchOrNull(), request.toPageable(SORTABLE, "documentDate"));
        return DataTableResponse.from(draw, page, views::gridRow);
    }

    /** The grid's documents by status, under the same search and dates: the list's tiles and chips. */
    @GetMapping("/api/{slug:" + SLUGS + "}/summary")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
    public List<DocumentStatusTotal> summary(
            @PathVariable String slug,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return documents.statusTotals(CommercialStep.ofSlug(slug), from, to, DataTableRequest.searchOrNull(search));
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
    public Map<String, Object> detail(@PathVariable String slug, @PathVariable Long id) {
        return views.detail(CommercialStep.ofSlug(slug), id);
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/parents")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> parents(@PathVariable String slug, @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Long id, @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        int s = size == null || size < 1 ? LookupPage.DEFAULT_SIZE : Math.min(size, LookupPage.MAX_SIZE);
        int p = page == null || page < 1 ? 0 : page - 1;
        List<BusinessDocument> found = documents.parents(step, id == null ? q : null, id == null ? p : 0, id == null ? s : 500);
        if (id != null) found = found.stream().filter(d -> d.getId().equals(id)).toList();
        boolean more = id == null && found.size() > s;
        return LookupPage.of(found.stream().limit(s).map(d -> new LookupPage.Option(d.getId(), d.getStatus().label(), d.getDocumentNo(),
            String.join(" · ", Arrays.stream(new String[] {d.getParty() == null ? null : d.getParty().getName(),
                d.getCurrencyCode() + " " + d.getSubtotalAmount().stripTrailingZeros().toPlainString(),
                d.getDocumentDate() == null ? null : d.getDocumentDate().toString()}).filter(Objects::nonNull).toList()))).toList(), more);
    }

    @GetMapping("/api/{slug:" + SLUGS + "}/open-lines")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
    public Map<String, Object> openLines(@PathVariable String slug, @RequestParam Long parentId, @RequestParam(required = false) Long exclude) {
        return documents.openLines(CommercialStep.ofSlug(slug), parentId, exclude);
    }

    // ----------------------------------------------------------------------------------- write

    @PostMapping("/api/{slug:" + SLUGS + "}")
    @ResponseBody
    @PreAuthorize("@commercialAccess.canAny(#slug, 'CREATE', 'AMEND')")
    public Map<String, Object> save(@PathVariable String slug, @RequestBody CommercialDocumentRequest request) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        AuthorityChecks.require(step.authority(request.id() == null ? "CREATE" : "AMEND"));
        return views.detail(step, documents.save(step, request).getId());
    }

    @DeleteMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'DELETE')")
    public Map<String, Object> delete(@PathVariable String slug, @PathVariable Long id) {
        documents.delete(CommercialStep.ofSlug(slug), id);
        return Map.of("deleted", id);
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/revise")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'AMEND')")
    public Map<String, Object> revise(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String reason) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        return views.detail(step, documents.revise(step, id, reason).getId());
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/cancel")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'AMEND')")
    public Map<String, Object> cancel(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String reason) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        return views.detail(step, posting.cancel(step, id, reason).getId());
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/close")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'AMEND')")
    public Map<String, Object> close(@PathVariable String slug, @PathVariable Long id, @RequestParam(required = false) String remarks) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        return views.detail(step, posting.close(step, id, remarks).getId());
    }

    // --------------------------------------------------------------------------------- records

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/records")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'AMEND')")
    public Map<String, Object> record(@PathVariable String slug, @PathVariable Long id, @RequestBody CommercialRecordsService.EventRequest request) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        records.record(step, id, request);
        return views.detail(step, id);
    }

    @DeleteMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/records/{eventId:\\d+}")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'AMEND')")
    public Map<String, Object> removeRecord(@PathVariable String slug, @PathVariable Long id, @PathVariable Long eventId) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        records.remove(step, id, eventId);
        return views.detail(step, id);
    }

    @PostMapping("/api/{slug:" + SLUGS + "}/{id:\\d+}/facts")
    @ResponseBody
    @PreAuthorize("@commercialAccess.can(#slug, 'AMEND')")
    public Map<String, Object> facts(@PathVariable String slug, @PathVariable Long id, @RequestBody CommercialRecordsService.FactsRequest request) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        records.updateFacts(step, id, request);
        return views.detail(step, id);
    }

    @PostMapping("/api/import-pi/{id:\\d+}/milestones")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_IPI_AMEND')")
    public Map<String, Object> milestone(@PathVariable Long id, @RequestParam ImportMilestone milestone,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam(required = false) String remarks) {
        records.milestone(id, milestone, date, remarks);
        return views.detail(CommercialStep.IPI, id);
    }

    @DeleteMapping("/api/import-pi/{id:\\d+}/milestones/{eventId:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_IPI_AMEND')")
    public Map<String, Object> undoMilestone(@PathVariable Long id, @PathVariable Long eventId) {
        records.undoMilestone(id, eventId);
        return views.detail(CommercialStep.IPI, id);
    }

    @PostMapping("/api/export-ci/{id:\\d+}/realization")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ECI_AMEND')")
    public Map<String, Object> realize(@PathVariable Long id, @RequestParam RealizationStep step,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) BigDecimal amount, @RequestParam(required = false) String refNo,
                                       @RequestParam(required = false) String remarks) {
        records.realize(id, step, date, amount, refNo, remarks);
        return views.detail(CommercialStep.ECI, id);
    }

    @PostMapping("/api/export-ci/{id:\\d+}/realization/undo")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ECI_AMEND')")
    public Map<String, Object> undoRealization(@PathVariable Long id, @RequestParam(required = false) String reason) {
        records.undoRealization(id, reason);
        return views.detail(CommercialStep.ECI, id);
    }

    // ----------------------------------------------------------------------------------- print

    @GetMapping("/{slug:" + SLUGS + "}/{id:\\d+}/print")
    @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")
    public String print(@PathVariable String slug, @PathVariable Long id, @RequestParam(defaultValue = "PI") String doc, Model model) {
        CommercialStep step = CommercialStep.ofSlug(slug);
        Map<String, String> paper = PRINTS.get(step).stream().filter(p -> p.get("code").equals(doc)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No such paper for a " + step.label() + ": " + doc));
        Map<String, Object> d = views.detail(step, id);
        model.addAttribute("d", d);
        model.addAttribute("paper", paper);
        model.addAttribute("step", step.name());
        model.addAttribute("company", jdbc.queryForMap("""
            SELECT o.name, p.legal_name AS "legalName", p.tin, p.bin, p.vat_reg_no AS "vatRegNo"
            FROM org_organizations o LEFT JOIN pty_parties p ON p.id = o.self_party_id WHERE o.id = :org
            """, new MapSqlParameterSource("org", context.requireOrganizationId())));
        model.addAttribute("today", LocalDate.now());
        return "commercial/print";
    }

    // ---------------------------------------------------------------------------- shared lookups

    /** Bank accounts of the company itself ({@code owner=self}) or of a party - for the bank pickers. */
    @GetMapping("/api/commercial/accounts")
    @ResponseBody
    @PreAuthorize("@commercialAccess.anyScreen()")
    public List<Map<String, Object>> accounts(@RequestParam String owner, @RequestParam(required = false) Long bankId) {
        Long ownerId = "self".equals(owner) ? documents.selfPartyId() : Long.valueOf(owner);
        return jdbc.queryForList("""
            SELECT a.id, a.bank_party_id AS "bankId", b.name AS "bankName", a.account_name AS "accountName",
                   a.account_number AS "accountNumber", a.branch_name AS "branchName", a.swift_code AS "swiftCode",
                   a.currency_code AS currency, a.is_primary AS "primary"
            FROM pty_party_bank_accounts a JOIN pty_parties b ON b.id = a.bank_party_id
            WHERE a.organization_id = :org AND a.party_id = :owner AND (CAST(:bank AS BIGINT) IS NULL OR a.bank_party_id = :bank)
            ORDER BY a.is_primary DESC, b.name, a.account_number
            """, new MapSqlParameterSource("org", context.requireOrganizationId()).addValue("owner", ownerId).addValue("bank", bankId));
    }

    @GetMapping("/api/commercial/cost-heads")
    @ResponseBody
    @PreAuthorize("@commercialAccess.anyScreen()")
    public List<Map<String, Object>> costHeadOptions(@RequestParam(required = false) String kind) {
        return costHeads.list(context.requireOrganizationId()).stream()
            .filter(h -> Boolean.TRUE.equals(h.getActive()) && (kind == null || kind.equalsIgnoreCase(h.getDocKind())))
            .map(h -> Map.<String, Object>of("id", h.getId(), "code", h.getCode(), "name", h.getName(), "docKind", h.getDocKind())).toList();
    }

    @GetMapping("/api/commercial/document-names")
    @ResponseBody
    @PreAuthorize("@commercialAccess.anyScreen()")
    public List<Map<String, Object>> documentNameOptions(@RequestParam(required = false) String kind) {
        return documentNames.list(context.requireOrganizationId()).stream()
            .filter(n -> Boolean.TRUE.equals(n.getActive()) && (kind == null || kind.equalsIgnoreCase(n.getDocKind())))
            .map(n -> Map.<String, Object>of("id", n.getId(), "code", n.getCode(), "name", n.getName(), "docKind", n.getDocKind())).toList();
    }

    /** Approved export LCs an import LC may be opened back-to-back against. */
    @GetMapping("/api/commercial/export-lcs")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ILC_VIEW')")
    public LookupPage<LookupPage.Option> exportLcs(@RequestParam(required = false) String q, @RequestParam(required = false) Long id,
                                                   @RequestParam(required = false) Integer page) {
        String like = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        int p = page == null || page < 1 ? 0 : page - 1;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT d.id, d.document_no, c.lc_no, pt.name AS buyer, d.currency_code, d.subtotal_amount
            FROM gbl_business_documents d
            LEFT JOIN com_document_details c ON c.document_id = d.id
            LEFT JOIN pty_parties pt ON pt.id = d.party_id
            WHERE d.organization_id = :org AND d.document_type = 'EXPORT_LETTER_OF_CREDIT' AND d.deleted = FALSE
              AND d.status IN ('APPROVED', 'PARTIAL', 'COMPLETED')
              AND (CAST(:id AS BIGINT) IS NULL OR d.id = :id)
              AND (lower(d.document_no) LIKE :q OR lower(COALESCE(c.lc_no, '')) LIKE :q OR lower(COALESCE(pt.name, '')) LIKE :q)
            ORDER BY d.document_date DESC, d.id DESC LIMIT 21 OFFSET :offset
            """, new MapSqlParameterSource("org", context.requireOrganizationId()).addValue("q", like).addValue("id", id)
                .addValue("offset", p * 20));
        return LookupPage.of(rows.stream().limit(20).map(r -> new LookupPage.Option(((Number) r.get("id")).longValue(),
            (String) r.get("lc_no"), r.get("document_no") + (r.get("lc_no") == null ? "" : " · LC " + r.get("lc_no")),
            r.get("buyer") + " · " + r.get("currency_code") + " " + ((BigDecimal) r.get("subtotal_amount")).stripTrailingZeros().toPlainString()))
            .toList(), rows.size() > 20);
    }
}

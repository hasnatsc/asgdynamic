package com.asg.fabricerp.accounts;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Accounts screens and their JSON API. Each screen is its own permission (ACC_CHART,
 * ACC_JOURNAL, ACC_REPORTS, ACC_CREDIT, ACC_SETUP) with the usual verbs.
 */
@Controller
public class AccountsController {

    private static final SortWhitelist ENTRY_SORT = SortWhitelist.of(Map.of(
        "entryNo", "entryNo", "postingDate", "postingDate", "eventType", "eventType"));

    private final AccountsSetupService setup;
    private final GeneralLedgerService ledger;
    private final LedgerReportService reports;
    private final CreditService credit;
    private final GlEntryRepository entries;
    private final AccountingPeriodRepository periods;
    private final PartyRepository parties;
    private final OrgContext context;

    public AccountsController(AccountsSetupService setup, GeneralLedgerService ledger, LedgerReportService reports,
                              CreditService credit, GlEntryRepository entries, AccountingPeriodRepository periods,
                              PartyRepository parties, OrgContext context) {
        this.setup = setup;
        this.ledger = ledger;
        this.reports = reports;
        this.credit = credit;
        this.entries = entries;
        this.periods = periods;
        this.parties = parties;
        this.context = context;
    }

    // ================================================================================ pages

    @GetMapping("/accounts/chart")
    @PreAuthorize("hasAuthority('SCREEN_ACC_CHART_VIEW')")
    public String chartPage(Model model) {
        return page(model, "Chart of accounts", "accounts/chart");
    }

    @GetMapping("/accounts/journals")
    @PreAuthorize("hasAuthority('SCREEN_ACC_JOURNAL_VIEW')")
    public String journalsPage(Model model) {
        return page(model, "Journal entries", "accounts/journals");
    }

    @GetMapping("/accounts/reports")
    @PreAuthorize("hasAuthority('SCREEN_ACC_REPORTS_VIEW')")
    public String reportsPage(Model model) {
        model.addAttribute("fiscalStartMonth", setup.fiscalStartMonth());
        return page(model, "Financial reports", "accounts/reports");
    }

    @GetMapping("/accounts/credit")
    @PreAuthorize("hasAuthority('SCREEN_ACC_CREDIT_VIEW')")
    public String creditPage(Model model) {
        return page(model, "Credit control", "accounts/credit");
    }

    @GetMapping("/accounts/setup")
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_VIEW')")
    public String setupPage(Model model) {
        model.addAttribute("fiscalStartMonth", setup.fiscalStartMonth());
        return page(model, "Accounting setup", "accounts/setup");
    }

    private static String page(Model model, String title, String template) {
        model.addAttribute("title", title);
        model.addAttribute("content", template + " :: content");
        return "layout/main";
    }

    // ================================================================================ chart

    @GetMapping("/api/accounts/chart")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('SCREEN_ACC_CHART_VIEW', 'SCREEN_ACC_JOURNAL_VIEW', 'SCREEN_ACC_REPORTS_VIEW', 'SCREEN_ACC_SETUP_VIEW')")
    public List<Map<String, Object>> chart() {
        List<Account> chart = setup.chart();
        Map<String, Account> byCode = chart.stream().collect(Collectors.toMap(Account::getCode, Function.identity()));
        return chart.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("code", a.getCode());
            row.put("name", a.getName());
            row.put("type", a.getAccountType().name());
            row.put("typeLabel", a.getAccountType().label());
            row.put("usage", a.getUsage().name());
            row.put("parentCode", a.getParent() == null ? null : a.getParent().getCode());
            int depth = 0;
            for (Account at = a.getParent(); at != null; at = at.getParent() == null ? null : byCode.get(at.getParent().getCode())) depth++;
            row.put("depth", depth);
            row.put("currency", a.getCurrencyCode());
            row.put("description", a.getDescription());
            row.put("active", a.getActive());
            row.put("postable", a.acceptsPostings());
            return row;
        }).toList();
    }

    @PostMapping("/api/accounts/chart")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CHART_CREATE')")
    public Map<String, Object> openAccount(@RequestBody AccountsSetupService.AccountRequest request) {
        return Map.of("id", setup.openAccount(request).getId());
    }

    @PutMapping("/api/accounts/chart/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CHART_AMEND')")
    public Map<String, Object> updateAccount(@PathVariable Long id, @RequestBody AccountsSetupService.AccountRequest request) {
        return Map.of("id", setup.updateAccount(id, request).getId());
    }

    @PostMapping("/api/accounts/chart/{id}/active")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CHART_AMEND')")
    public Map<String, Object> setActive(@PathVariable Long id, @RequestParam boolean value) {
        return Map.of("id", id, "active", setup.setAccountActive(id, value).getActive());
    }

    @PostMapping("/api/accounts/chart/standard")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CHART_CREATE') and hasAuthority('SCREEN_ACC_SETUP_CREATE')")
    public AccountsSetupService.LoadResult loadStandardChart() {
        return setup.loadStandardChart();
    }

    // ================================================================================ journal

    @GetMapping("/api/accounts/events")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> events() {
        Map<String, String> all = new LinkedHashMap<>(PostingEvent.RULE_EVENTS);
        all.put(PostingEvent.MANUAL_JOURNAL, "Manual journal");
        return all;
    }

    @GetMapping("/api/accounts/entries")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_JOURNAL_VIEW')")
    public DataTableResponse<Map<String, Object>> entries(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String event,
            @RequestParam(required = false) VoucherType voucher,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        String pattern = request.searchOrNull() == null ? "" : "%" + request.searchOrNull().toLowerCase() + "%";
        var page = entries.search(context.requireOrganizationId(),
            from == null ? LocalDate.of(1900, 1, 1) : from, to == null ? LocalDate.of(2999, 12, 31) : to,
            event == null ? "" : event, voucher, pattern, request.toPageable(ENTRY_SORT, "postingDate"));
        return DataTableResponse.from(draw, page, this::entryRow);
    }

    private Map<String, Object> entryRow(GlEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("entryNo", e.getEntryNo());
        row.put("postingDate", e.getPostingDate().toString());
        row.put("eventType", e.getEventType());
        row.put("eventLabel", eventLabel(e.getEventType()));
        row.put("voucherType", e.getVoucherType().name());
        row.put("voucherLabel", e.getVoucherType().label());
        row.put("narration", e.getNarration());
        row.put("amount", e.totalDebits());
        row.put("currency", e.getCurrencyCode());
        row.put("reversed", e.isReversed());
        row.put("isReversal", e.getReversesEntryId() != null);
        return row;
    }

    @GetMapping("/api/accounts/entries/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('SCREEN_ACC_JOURNAL_VIEW', 'SCREEN_ACC_REPORTS_VIEW')")
    public Map<String, Object> entry(@PathVariable Long id) {
        Long orgId = context.requireOrganizationId();
        GlEntry e = entries.findScoped(id, orgId).orElseThrow(() -> new IllegalArgumentException("No entry " + id + "."));
        Map<String, String> accountNames = setup.chart().stream().collect(Collectors.toMap(Account::getCode, Account::getName));
        Map<Long, String> partyNames = partyNames(e.getLines().stream().map(GlEntryLine::getPartyId).filter(Objects::nonNull).toList());
        Map<String, Object> detail = entryRow(e);
        detail.put("fxRate", e.getFxRate());
        detail.put("docTypeCode", e.getDocTypeCode());
        detail.put("documentId", e.getDocumentId());
        detail.put("createdBy", e.getCreatedBy());
        detail.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        detail.put("period", periods.findById(e.getPeriodId()).map(AccountingPeriod::getName).orElse(null));
        detail.put("reversesEntry", e.getReversesEntryId() == null ? null
            : entries.findScoped(e.getReversesEntryId(), orgId).map(GlEntry::getEntryNo).orElse(null));
        detail.put("reversedBy", e.getReversedById() == null ? null
            : entries.findScoped(e.getReversedById(), orgId).map(GlEntry::getEntryNo).orElse(null));
        detail.put("lines", e.getLines().stream().map(l -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("accountCode", l.getAccountCode());
            line.put("accountName", accountNames.get(l.getAccountCode()));
            line.put("side", l.getSide().name());
            line.put("amount", l.getAmount());
            line.put("functionalAmount", l.getFunctionalAmount());
            line.put("party", l.getPartyId() == null ? null : partyNames.get(l.getPartyId()));
            line.put("costCentre", l.getCostCentreCode());
            line.put("narration", l.getNarration());
            return line;
        }).toList());
        return detail;
    }

    public record JournalRequest(VoucherType voucherType, LocalDate postingDate, String narration,
                                 List<GeneralLedgerService.JournalLine> lines) { }

    /** The voucher types a person may pick for a hand-entered journal. */
    @GetMapping("/api/accounts/voucher-types")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> voucherTypes() {
        return java.util.Arrays.stream(VoucherType.values()).map(t -> Map.<String, Object>of(
            "code", t.name(), "label", t.label(), "manual", t.manual(), "series", t.series().name())).toList();
    }

    @PostMapping("/api/accounts/journals")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_JOURNAL_CREATE')")
    public Map<String, Object> postJournal(@RequestBody JournalRequest request) {
        GlEntry entry = ledger.postManualJournal(request.voucherType(),
            request.postingDate() == null ? LocalDate.now() : request.postingDate(), request.narration(), request.lines());
        return Map.of("id", entry.getId(), "entryNo", entry.getEntryNo());
    }

    @PostMapping("/api/accounts/entries/{id}/reversal")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_JOURNAL_APPROVE')")
    public Map<String, Object> reverse(@PathVariable Long id, @RequestParam String reason) {
        GlEntry reversal = ledger.reverse(id, reason);
        return Map.of("id", reversal.getId(), "entryNo", reversal.getEntryNo());
    }

    // ================================================================================ reports

    @GetMapping("/api/accounts/reports/trial-balance")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_REPORTS_VIEW')")
    public LedgerReportService.TrialBalance trialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.trialBalance(from, to);
    }

    @GetMapping("/api/accounts/reports/account-ledger")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_REPORTS_VIEW')")
    public LedgerReportService.Ledger accountLedger(
            @RequestParam String account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.accountLedger(account, from, to);
    }

    @GetMapping("/api/accounts/reports/party-ledger")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_REPORTS_VIEW')")
    public LedgerReportService.Ledger partyLedger(
            @RequestParam Long partyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.partyLedger(partyId, from, to);
    }

    // ================================================================================ setup

    @GetMapping("/api/accounts/periods")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_VIEW')")
    public Map<String, Object> periods(@RequestParam(required = false) Integer year) {
        List<Integer> years = setup.fiscalYears();
        Integer shown = year != null ? year : years.isEmpty() ? null : years.getFirst();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("years", years);
        out.put("year", shown);
        out.put("fiscalStartMonth", setup.fiscalStartMonth());
        out.put("periods", shown == null ? List.of() : setup.periodsOf(shown).stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("code", p.getCode());
            row.put("name", p.getName());
            row.put("periodNo", p.getPeriodNo());
            row.put("startsOn", p.getStartsOn().toString());
            row.put("endsOn", p.getEndsOn().toString());
            row.put("status", p.getPeriodStatus().name());
            row.put("closedOn", p.getClosedOn() == null ? null : p.getClosedOn().toString());
            row.put("closedBy", p.getClosedBy());
            row.put("reopenReason", p.getReopenReason());
            return row;
        }).toList());
        return out;
    }

    @PostMapping("/api/accounts/periods/generate")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_CREATE')")
    public Map<String, Object> generateYear(@RequestParam int year) {
        return Map.of("created", setup.generateFiscalYear(year).size(), "year", year);
    }

    @PostMapping("/api/accounts/periods/{id}/close")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_APPROVE')")
    public Map<String, Object> closePeriod(@PathVariable Long id) {
        return Map.of("id", id, "status", setup.closePeriod(id).getPeriodStatus().name());
    }

    @PostMapping("/api/accounts/periods/{id}/reopen")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_APPROVE')")
    public Map<String, Object> reopenPeriod(@PathVariable Long id, @RequestParam String reason) {
        return Map.of("id", id, "status", setup.reopenPeriod(id, reason).getPeriodStatus().name());
    }

    @GetMapping("/api/accounts/cost-centres")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('SCREEN_ACC_SETUP_VIEW', 'SCREEN_ACC_JOURNAL_VIEW')")
    public List<Map<String, Object>> costCentres() {
        return setup.costCentres().stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("code", c.getCode());
            row.put("name", c.getName());
            row.put("centreType", c.getCentreType().name());
            row.put("direct", c.isDirect());
            row.put("parentCode", c.getParent() == null ? null : c.getParent().getCode());
            row.put("absorbsIntoCode", c.getAbsorbsInto() == null ? null : c.getAbsorbsInto().getCode());
            row.put("sectionCode", c.getSectionCode());
            row.put("remarks", c.getRemarks());
            row.put("active", c.getActive());
            return row;
        }).toList();
    }

    @PostMapping("/api/accounts/cost-centres")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_CREATE')")
    public Map<String, Object> createCostCentre(@RequestBody AccountsSetupService.CostCentreRequest request) {
        return Map.of("id", setup.saveCostCentre(null, request).getId());
    }

    @PutMapping("/api/accounts/cost-centres/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_AMEND')")
    public Map<String, Object> updateCostCentre(@PathVariable Long id, @RequestBody AccountsSetupService.CostCentreRequest request) {
        return Map.of("id", setup.saveCostCentre(id, request).getId());
    }

    @GetMapping("/api/accounts/rules")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_VIEW')")
    public List<Map<String, Object>> rules() {
        LocalDate today = LocalDate.now();
        return setup.postingRules().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("code", r.getCode());
            row.put("name", r.getName());
            row.put("eventType", r.getEventType());
            row.put("eventLabel", eventLabel(r.getEventType()));
            row.put("effectiveFrom", r.getEffectiveFrom().toString());
            row.put("effectiveTo", r.getEffectiveTo() == null ? null : r.getEffectiveTo().toString());
            row.put("current", r.coversDate(today));
            row.put("description", r.getDescription());
            row.put("lines", r.getLines().stream().map(l -> Map.of(
                "side", l.getSide().name(), "accountCode", l.getAccountCode(), "amountKey", l.getAmountKey())).toList());
            return row;
        }).toList();
    }

    @PostMapping("/api/accounts/rules")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_SETUP_CREATE')")
    public Map<String, Object> registerRule(@RequestBody AccountsSetupService.RuleRequest request) {
        return Map.of("id", setup.registerRule(request).getId());
    }

    // ================================================================================ credit

    @GetMapping("/api/accounts/credit")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CREDIT_VIEW')")
    public List<Map<String, Object>> creditLimits() {
        List<CreditLimit> limits = credit.currentLimits();
        Map<Long, String> names = partyNames(limits.stream().map(CreditLimit::getPartyId).toList());
        LocalDate today = LocalDate.now();
        return limits.stream().map(l -> {
            CreditService.Exposure exposure = credit.exposureOf(l.getPartyId(), BigDecimal.ZERO, today);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", l.getId());
            row.put("partyId", l.getPartyId());
            row.put("party", names.get(l.getPartyId()));
            row.put("currency", l.getCurrencyCode());
            row.put("limit", l.getLimitAmount());
            row.put("secured", l.getSecuredAmount());
            row.put("receivable", exposure.receivable());
            row.put("headroom", exposure.headroom());
            row.put("verdict", exposure.verdict().name());
            row.put("onHold", l.isOnHold());
            row.put("holdReason", l.getHoldReason());
            row.put("effectiveFrom", l.getEffectiveFrom().toString());
            row.put("reviewOn", l.getReviewOn() == null ? null : l.getReviewOn().toString());
            row.put("reviewOverdue", l.isOverdueForReview(today));
            row.put("remarks", l.getRemarks());
            return row;
        }).toList();
    }

    @PostMapping("/api/accounts/credit")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CREDIT_CREATE')")
    public Map<String, Object> grantLimit(@RequestBody CreditService.LimitRequest request) {
        return Map.of("id", credit.grant(request).getId());
    }

    @PostMapping("/api/accounts/credit/{id}/hold")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CREDIT_AMEND')")
    public Map<String, Object> hold(@PathVariable Long id, @RequestParam String reason) {
        return Map.of("id", id, "onHold", credit.hold(id, reason).isOnHold());
    }

    @PostMapping("/api/accounts/credit/{id}/release")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CREDIT_AMEND')")
    public Map<String, Object> release(@PathVariable Long id) {
        return Map.of("id", id, "onHold", credit.release(id).isOnHold());
    }

    @GetMapping("/api/accounts/credit/exposure")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ACC_CREDIT_VIEW')")
    public CreditService.Exposure exposure(@RequestParam Long partyId,
                                           @RequestParam(required = false) BigDecimal amount) {
        return credit.exposureOf(partyId, amount, LocalDate.now());
    }

    // ================================================================================

    private Map<Long, String> partyNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Long orgId = context.requireOrganizationId();
        Map<Long, String> names = new HashMap<>();
        for (Party p : parties.findAllById(ids.stream().distinct().toList())) {
            if (orgId.equals(p.getOrganizationId())) names.put(p.getId(), p.getCode() + " · " + p.getName());
        }
        return names;
    }

    private static String eventLabel(String eventType) {
        if (PostingEvent.MANUAL_JOURNAL.equals(eventType)) return "Manual journal";
        return PostingEvent.RULE_EVENTS.getOrDefault(eventType, eventType);
    }

}

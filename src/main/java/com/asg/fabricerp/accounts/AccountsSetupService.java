package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.AccountType;
import com.asg.fabricerp.accounts.AccountFlags.CostCentreType;
import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.global.numbering.FinancialYear;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maintains what the ledger posts against: the chart, the fiscal calendar, cost centres and the
 * posting rules. Every change is scoped to the caller's organization.
 */
@Service
@Transactional(readOnly = true)
public class AccountsSetupService {

    private final AccountRepository accounts;
    private final AccountingPeriodRepository periods;
    private final CostCentreRepository costCentres;
    private final PostingRuleRepository rules;
    private final GlEntryRepository entries;
    private final OrganizationRepository organizations;
    private final OrgContext context;

    public AccountsSetupService(AccountRepository accounts, AccountingPeriodRepository periods,
                                CostCentreRepository costCentres, PostingRuleRepository rules,
                                GlEntryRepository entries, OrganizationRepository organizations, OrgContext context) {
        this.accounts = accounts;
        this.periods = periods;
        this.costCentres = costCentres;
        this.rules = rules;
        this.entries = entries;
        this.organizations = organizations;
        this.context = context;
    }

    private Long org() { return context.requireOrganizationId(); }

    // ============================================================================ chart

    public record AccountRequest(String code, String name, AccountType accountType, String parentCode,
                                 boolean control, String currencyCode, String description) { }

    public List<Account> chart() { return accounts.chart(org()); }

    @Transactional
    public Account openAccount(AccountRequest request) {
        String code = requireCode(request.code(), "Account code");
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Account name is required.");
        if (request.accountType() == null) throw new IllegalArgumentException("Choose the account type.");
        if (accounts.findByCode(org(), code).isPresent()) {
            throw new IllegalArgumentException("Account code " + code + " is already in the chart.");
        }
        Account account = new Account(code, request.name().trim(), request.accountType())
            .inCurrency(request.currencyCode()).describedAs(blankToNull(request.description()));
        account.setOrganizationId(org());
        if (request.parentCode() != null && !request.parentCode().isBlank()) {
            Account parent = accounts.findByCode(org(), request.parentCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("No account " + request.parentCode() + " to place " + code + " under."));
            if (entries.countLinesOn(org(), parent.getCode()) > 0) {
                throw new IllegalStateException("Account " + parent.getCode() + " already has postings, so it cannot "
                    + "become a summary of sub-accounts. Create a new parent instead.");
            }
            account.under(parent);
        }
        account.asControl(request.control());
        return accounts.save(account);
    }

    /** Name, description, currency and control flag change; code, type and place in the tree do not. */
    @Transactional
    public Account updateAccount(Long id, AccountRequest request) {
        Account account = accounts.findScoped(id, org())
            .orElseThrow(() -> new IllegalArgumentException("No account " + id + "."));
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Account name is required.");
        account.rename(request.name().trim(), blankToNull(request.description()));
        account.inCurrency(request.currencyCode());
        if (account.isControl() != request.control() && entries.countLinesOn(org(), account.getCode()) > 0) {
            throw new IllegalStateException("Account " + account.getCode() + " already has postings; changing whether it is "
                + "a control account would split its history.");
        }
        account.asControl(request.control());
        return account;
    }

    @Transactional
    public Account setAccountActive(Long id, boolean active) {
        Account account = accounts.findScoped(id, org())
            .orElseThrow(() -> new IllegalArgumentException("No account " + id + "."));
        account.setActive(active);
        return account;
    }

    // ============================================================================ periods

    /** Creates the twelve periods of a fiscal year, starting in the organization's fiscal month. */
    @Transactional
    public List<AccountingPeriod> generateFiscalYear(int startYear) {
        if (startYear < 2000 || startYear > 2100) throw new IllegalArgumentException("Choose a fiscal year between 2000 and 2100.");
        if (!periods.ofYear(org(), startYear).isEmpty()) {
            throw new IllegalStateException("Fiscal year " + startYear + " already has its periods.");
        }
        int startMonth = fiscalStartMonth();
        FinancialYear fy = new FinancialYear(startYear, startMonth);
        DateTimeFormatter monthName = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<AccountingPeriod> created = new ArrayList<>();
        for (int n = 1; n <= 12; n++) {
            LocalDate starts = fy.start().plusMonths(n - 1L);
            LocalDate ends = starts.plusMonths(1).minusDays(1);
            String code = "FY%d-P%02d".formatted(startYear, n);
            AccountingPeriod period = new AccountingPeriod(code, starts.format(monthName), startYear, n, starts, ends);
            period.setOrganizationId(org());
            created.add(periods.save(period));
        }
        return created;
    }

    public int fiscalStartMonth() {
        return organizations.findById(org()).map(o -> o.getFiscalYearStartMonth()).orElse(7);
    }

    public List<AccountingPeriod> periodsOf(int fiscalYear) { return periods.ofYear(org(), fiscalYear); }

    public List<Integer> fiscalYears() { return periods.years(org()); }

    @Transactional
    public AccountingPeriod closePeriod(Long id) {
        AccountingPeriod period = periods.findScoped(id, org())
            .orElseThrow(() -> new IllegalArgumentException("No period " + id + "."));
        period.close(LocalDate.now(), context.username());
        return period;
    }

    @Transactional
    public AccountingPeriod reopenPeriod(Long id, String reason) {
        AccountingPeriod period = periods.findScoped(id, org())
            .orElseThrow(() -> new IllegalArgumentException("No period " + id + "."));
        period.reopen(reason);
        return period;
    }

    // ============================================================================ cost centres

    public record CostCentreRequest(String code, String name, CostCentreType centreType, String parentCode,
                                    String absorbsIntoCode, String sectionCode, String remarks) { }

    public List<CostCentre> costCentres() { return costCentres.all(org()); }

    @Transactional
    public CostCentre saveCostCentre(Long id, CostCentreRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Cost centre name is required.");
        if (request.centreType() == null) throw new IllegalArgumentException("Choose the cost centre type.");
        CostCentre centre;
        if (id == null) {
            String code = requireCode(request.code(), "Cost centre code");
            if (costCentres.findByCode(org(), code).isPresent()) {
                throw new IllegalArgumentException("Cost centre " + code + " already exists.");
            }
            centre = new CostCentre(code, request.name().trim(), request.centreType());
            centre.setOrganizationId(org());
        } else {
            centre = costCentres.findScoped(id, org()).orElseThrow(() -> new IllegalArgumentException("No cost centre " + id + "."));
        }
        centre.update(request.name().trim(), request.centreType(), blankToNull(request.sectionCode()), blankToNull(request.remarks()));
        centre.under(lookupCentre(request.parentCode()));
        centre.absorbingInto(lookupCentre(request.absorbsIntoCode()));
        return costCentres.save(centre);
    }

    private CostCentre lookupCentre(String code) {
        if (code == null || code.isBlank()) return null;
        return costCentres.findByCode(org(), code.trim())
            .orElseThrow(() -> new IllegalArgumentException("No cost centre " + code + "."));
    }

    // ============================================================================ posting rules

    public record RuleLineRequest(Side side, String accountCode, String amountKey) { }

    public record RuleRequest(String code, String name, String eventType, LocalDate effectiveFrom,
                              String description, List<RuleLineRequest> lines) { }

    public List<PostingRule> postingRules() { return rules.all(org()); }

    /**
     * Registers a rule. If the event already has a live rule, that one is superseded the day before
     * the new one takes effect: rules are replaced, never edited, so old postings stay explainable.
     */
    @Transactional
    public PostingRule registerRule(RuleRequest request) {
        if (!PostingEvent.isKnown(request.eventType())) {
            throw new IllegalArgumentException("Unknown event " + request.eventType() + ".");
        }
        LocalDate from = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
        String code = request.code() == null || request.code().isBlank()
            ? request.eventType() + "-" + from.toString().replace("-", "")
            : request.code().trim().toUpperCase();
        if (rules.findByCode(org(), code).isPresent()) throw new IllegalArgumentException("Rule code " + code + " is taken.");

        PostingRule rule = new PostingRule(code, blankToNull(request.name()) == null ? request.eventType() : request.name().trim(),
            request.eventType(), from);
        rule.setOrganizationId(org());
        rule.describe(rule.getName(), blankToNull(request.description()));
        for (RuleLineRequest line : request.lines() == null ? List.<RuleLineRequest>of() : request.lines()) {
            if (line.side() == null || line.accountCode() == null || line.accountCode().isBlank()) {
                throw new IllegalArgumentException("Every rule line needs a side and an account.");
            }
            Account account = accounts.findByCode(org(), line.accountCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("Account " + line.accountCode() + " is not in the chart."));
            if (account.isSummary()) {
                throw new IllegalArgumentException("Account " + account.getCode() + " has sub-accounts; a rule must post to a leaf account.");
            }
            rule.addLine(line.side(), account.getCode(), line.amountKey());
        }
        rule.requireSelfBalancing();

        for (PostingRule current : rules.forEvent(org(), request.eventType())) {
            if (current.getEffectiveTo() == null) {
                current.supersededFrom(from);
                rules.saveAndFlush(current);   // free the one-live-rule index before the insert
            }
        }
        return rules.save(rule);
    }

    // ============================================================================ standard chart

    private record Std(String code, String name, AccountType type, String parent, boolean control) { }

    /**
     * A typical composite textile mill chart - spinning to finishing, export sales - with matching
     * posting rules. Loaded only on request, into an organization's chart, and only the accounts and
     * rules it does not already have; the accountant reviews and edits from there.
     */
    private static final List<Std> STANDARD_CHART = List.of(
        new Std("1000", "Assets", AccountType.ASSET, null, false),
        new Std("1100", "Current assets", AccountType.ASSET, "1000", false),
        new Std("1101", "Cash in hand", AccountType.ASSET, "1100", false),
        new Std("1102", "Bank accounts", AccountType.ASSET, "1100", false),
        new Std("1110", "Trade receivables", AccountType.ASSET, "1100", true),
        new Std("1120", "Advances, deposits and prepayments", AccountType.ASSET, "1100", false),
        new Std("1130", "VAT current account", AccountType.ASSET, "1100", false),
        new Std("1140", "Inventory - yarn", AccountType.ASSET, "1100", false),
        new Std("1141", "Inventory - dyes and chemicals", AccountType.ASSET, "1100", false),
        new Std("1142", "Inventory - greige fabric", AccountType.ASSET, "1100", false),
        new Std("1143", "Work in process", AccountType.ASSET, "1100", false),
        new Std("1144", "Inventory - finished fabric", AccountType.ASSET, "1100", false),
        new Std("1145", "Stores and spares", AccountType.ASSET, "1100", false),
        new Std("1200", "Non-current assets", AccountType.ASSET, "1000", false),
        new Std("1201", "Land and buildings", AccountType.ASSET, "1200", false),
        new Std("1202", "Plant and machinery", AccountType.ASSET, "1200", false),
        new Std("1209", "Accumulated depreciation", AccountType.ASSET, "1200", false),
        new Std("2000", "Liabilities", AccountType.LIABILITY, null, false),
        new Std("2100", "Current liabilities", AccountType.LIABILITY, "2000", false),
        new Std("2101", "Trade payables", AccountType.LIABILITY, "2100", true),
        new Std("2102", "Goods received not billed", AccountType.LIABILITY, "2100", false),
        new Std("2103", "VAT output payable", AccountType.LIABILITY, "2100", false),
        new Std("2104", "Accrued expenses", AccountType.LIABILITY, "2100", false),
        new Std("2105", "Short-term bank loans", AccountType.LIABILITY, "2100", false),
        new Std("2200", "Non-current liabilities", AccountType.LIABILITY, "2000", false),
        new Std("2201", "Long-term loans", AccountType.LIABILITY, "2200", false),
        new Std("3000", "Equity", AccountType.EQUITY, null, false),
        new Std("3101", "Share capital", AccountType.EQUITY, "3000", false),
        new Std("3102", "Retained earnings", AccountType.EQUITY, "3000", false),
        new Std("4000", "Income", AccountType.INCOME, null, false),
        new Std("4101", "Sales - export fabric", AccountType.INCOME, "4000", false),
        new Std("4102", "Sales - local fabric", AccountType.INCOME, "4000", false),
        new Std("4201", "Exchange gain / loss", AccountType.INCOME, "4000", false),
        new Std("4202", "Other income", AccountType.INCOME, "4000", false),
        new Std("5000", "Expenses", AccountType.EXPENSE, null, false),
        new Std("5100", "Cost of sales", AccountType.EXPENSE, "5000", false),
        new Std("5101", "Cost of fabric sold", AccountType.EXPENSE, "5100", false),
        new Std("5102", "Abnormal loss and re-dye", AccountType.EXPENSE, "5100", false),
        new Std("5200", "Production overheads", AccountType.EXPENSE, "5000", false),
        new Std("5201", "Utilities - gas, power, water", AccountType.EXPENSE, "5200", false),
        new Std("5202", "Factory wages", AccountType.EXPENSE, "5200", false),
        new Std("5203", "Depreciation", AccountType.EXPENSE, "5200", false),
        new Std("5300", "Operating expenses", AccountType.EXPENSE, "5000", false),
        new Std("5301", "Salaries and administration", AccountType.EXPENSE, "5300", false),
        new Std("5302", "Bank charges", AccountType.EXPENSE, "5300", false),
        new Std("5303", "Freight and delivery", AccountType.EXPENSE, "5300", false));

    public record LoadResult(int accountsAdded, int rulesAdded) { }

    @Transactional
    public LoadResult loadStandardChart() {
        int accountsAdded = 0;
        for (Std std : STANDARD_CHART) {
            if (accounts.findByCode(org(), std.code()).isPresent()) continue;
            openAccount(new AccountRequest(std.code(), std.name(), std.type(), std.parent(), std.control(), null, null));
            accountsAdded++;
        }
        accounts.flush();

        int rulesAdded = 0;
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        String[][] standardRules = {
            // event, debit, credit
            {PostingEvent.GRN, "1140", "2102"},
            {PostingEvent.SUPPLIER_BILL, "2102", "2101"},
            {PostingEvent.SUPPLIER_PAYMENT, "2101", "1102"},
            {PostingEvent.YARN_ISSUE_TO_WEAVING, "1143", "1140"},
            {PostingEvent.GREIGE_RECEIVED_FROM_WEAVING, "1142", "1143"},
            {PostingEvent.GREIGE_ISSUE_TO_BATCH, "1143", "1142"},
            {PostingEvent.CHEMICAL_ISSUE_TO_BATCH, "1143", "1141"},
            {PostingEvent.UTILITY_CONSUMED, "1143", "2104"},
            {PostingEvent.FINISHED_ROLL_RECEIVED, "1144", "1143"},
            {PostingEvent.ABNORMAL_LOSS, "5102", "1143"},
            {PostingEvent.DELIVERY, "5101", "1144"},
            {PostingEvent.RECEIPT, "1102", "1110"},
            {PostingEvent.DEPRECIATION, "5203", "1209"}};
        for (String[] r : standardRules) {
            if (!rules.forEvent(org(), r[0]).isEmpty()) continue;
            registerRule(new RuleRequest(null, PostingEvent.RULE_EVENTS.get(r[0]), r[0], from, "Standard rule",
                List.of(new RuleLineRequest(Side.DEBIT, r[1], "amount"), new RuleLineRequest(Side.CREDIT, r[2], "amount"))));
            rulesAdded++;
        }
        if (rules.forEvent(org(), PostingEvent.INVOICE).isEmpty()) {
            registerRule(new RuleRequest(null, PostingEvent.RULE_EVENTS.get(PostingEvent.INVOICE), PostingEvent.INVOICE, from,
                "Standard rule: receivable for net and VAT; sales and VAT output split",
                List.of(new RuleLineRequest(Side.DEBIT, "1110", "net"), new RuleLineRequest(Side.DEBIT, "1110", "vat"),
                        new RuleLineRequest(Side.CREDIT, "4101", "net"), new RuleLineRequest(Side.CREDIT, "2103", "vat"))));
            rulesAdded++;
        }
        return new LoadResult(accountsAdded, rulesAdded);
    }

    // ============================================================================

    private static String requireCode(String code, String what) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException(what + " is required.");
        String trimmed = code.trim().toUpperCase();
        if (!trimmed.matches("[A-Z0-9][A-Z0-9._-]{0,39}")) {
            throw new IllegalArgumentException(what + " may use letters, digits, dot, dash and underscore only.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

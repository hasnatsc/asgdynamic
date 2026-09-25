package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.AccountUsage;
import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * V19's ASFL chart and posting rules on real PostgreSQL: the tree lands as intended, and a month
 * of mill activity posts through the ASFL rules - numbered per voucher type - to a balanced trial
 * balance. Opt-in like {@link AccountsDatabaseIT}, on a throwaway database.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class AsflChartDatabaseIT {

    private static final String V19 = "db/migration/V19__asfl_chart_voucher_types_posting_rules.sql";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private GeneralLedgerService ledger;
    @Autowired private LedgerReportService reports;
    @Autowired private AccountsSetupService setup;
    @Autowired private AccountRepository accounts;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private OrgContext context;

    private long orgId;
    private long customerId;
    private long supplierId;
    private LocalDate day;

    @BeforeEach
    void setUp() throws IOException {
        jdbc.execute("""
            TRUNCATE acc_gl_entry_lines, acc_gl_entries, acc_credit_limits, acc_posting_rule_lines,
                     acc_posting_rules, acc_periods, acc_cost_centres, acc_accounts RESTART IDENTITY CASCADE
            """);
        jdbc.update("DELETE FROM gbl_issued_numbers");
        jdbc.update("DELETE FROM gbl_number_counters");
        reapplyChartAndRules();

        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        Long unitId = jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = 'AF'", Long.class, orgId);
        customerId = party("IT-BUYER", "IT Buyer Ltd");
        supplierId = party("IT-YARN", "IT Yarn Mills");
        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(unitId);
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.username()).thenReturn("it");
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());

        day = LocalDate.now();
        int startMonth = setup.fiscalStartMonth();
        int fyStart = day.getMonthValue() >= startMonth ? day.getYear() : day.getYear() - 1;
        setup.generateFiscalYear(fyStart);
    }

    /** Runs V19's chart and rule parts again - the migration itself ran once, before the truncate. */
    private void reapplyChartAndRules() throws IOException {
        String script = new ClassPathResource(V19).getContentAsString(StandardCharsets.UTF_8);
        String inserts = script.substring(script.indexOf("-- 2. Chart of accounts"));
        tx.executeWithoutResult(status -> ScriptUtils.executeSqlScript(
            DataSourceUtils.getConnection(dataSource), new org.springframework.core.io.ByteArrayResource(inserts.getBytes(StandardCharsets.UTF_8))));
    }

    private long party(String code, String name) {
        jdbc.update("""
            INSERT INTO pty_parties (organization_id, code, name, active, deleted, version, created_by, created_at)
            SELECT ?, ?, ?, TRUE, FALSE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM pty_parties WHERE organization_id = ? AND code = ?)
            """, orgId(), code, name, orgId(), code);
        return jdbc.queryForObject("SELECT id FROM pty_parties WHERE organization_id = ? AND code = ?", Long.class, orgId(), code);
    }

    private long orgId() {
        return jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
    }

    @Test
    void theChartLandsAsATreeWithControlAccounts() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acc_accounts WHERE organization_id = ?", Integer.class, orgId)).isEqualTo(408);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acc_accounts WHERE organization_id = ? AND usage_type = 'SUMMARY'", Integer.class, orgId)).isEqualTo(75);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acc_accounts WHERE organization_id = ? AND usage_type = 'CONTROL'", Integer.class, orgId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acc_accounts WHERE organization_id = ? AND parent_id IS NULL", Integer.class, orgId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acc_posting_rules WHERE organization_id = ?", Integer.class, orgId)).isEqualTo(20);

        // Corrections of the legacy nesting, and the control flag.
        assertThat(parentOf("12020201")).isEqualTo("12020200");     // LC receivable, not under PPE
        assertThat(parentOf("20200000")).isEqualTo("20000000");
        assertThat(parentOf("55010101")).isEqualTo("55010100");
        assertThat(parentOf("55000000")).isEqualTo("50000000");     // financial expenses, not under income
        assertThat(parentOf("53011601")).isEqualTo("53011600");
        assertThat(parentOf("53011600")).isEqualTo("53000000");
        assertThat(accounts.findByCode(orgId, "12020201").orElseThrow().getUsage()).isEqualTo(AccountUsage.CONTROL);
    }

    private String parentOf(String code) {
        return jdbc.queryForObject("""
            SELECT p.code FROM acc_accounts a JOIN acc_accounts p ON p.id = a.parent_id
            WHERE a.organization_id = ? AND a.code = ?""", String.class, orgId, code);
    }

    @Test
    void aMonthOfActivityPostsThroughTheAsflRulesToABalancedTrialBalance() {
        List<GlEntry> posted = List.of(
            ledger.post(PostingCommand.of("GR", 1L, PostingEvent.GREIGE_RECEIVED_FROM_WEAVING, day, new BigDecimal("500000"), null, "GR-1")),
            ledger.post(PostingCommand.of("FR", 2L, PostingEvent.FINISHED_ROLL_RECEIVED, day, new BigDecimal("420000"), null, "FR-1")),
            ledger.post(PostingCommand.of("FD", 3L, PostingEvent.DELIVERY, day, new BigDecimal("300000"), null, "FD-1")),
            ledger.post(PostingCommand.of("CI", 4L, PostingEvent.INVOICE, day, new BigDecimal("380000"), customerId, "CI-1")),
            ledger.post(PostingCommand.of("RC", 5L, PostingEvent.RECEIPT, day, new BigDecimal("200000"), customerId, "Receipt")),
            ledger.post(PostingCommand.of("SB", 6L, PostingEvent.SUPPLIER_BILL, day, new BigDecimal("150000"), supplierId, "Bill")),
            ledger.postManualJournal(VoucherType.PAYMENT, day, "Factory rent", List.of(
                new GeneralLedgerService.JournalLine("52000231", Side.DEBIT, new BigDecimal("80000"), null, null),
                new GeneralLedgerService.JournalLine("12040401", Side.CREDIT, new BigDecimal("80000"), null, null))),
            ledger.postManualJournal(VoucherType.JOURNAL, day, "Opening capital", List.of(
                new GeneralLedgerService.JournalLine("12040401", Side.DEBIT, new BigDecimal("1000000"), null, null),
                new GeneralLedgerService.JournalLine("30100101", Side.CREDIT, new BigDecimal("1000000"), null, null))));

        // Each voucher in the series of its type.
        assertThat(posted).extracting(e -> e.getEntryNo().substring(0, e.getEntryNo().indexOf('-')))
            .containsExactly("PDV", "PDV", "SV", "SV", "RV", "PUV", "PV", "JV");

        LedgerReportService.TrialBalance tb = reports.trialBalance(day.withDayOfMonth(1), day);
        assertThat(tb.balanced()).isTrue();
        Map<String, BigDecimal> closing = new java.util.HashMap<>();
        tb.rows().forEach(r -> closing.put(r.code(), r.closing()));
        assertThat(closing.get("12010112")).isEqualByComparingTo("500000");      // greige in stock
        assertThat(closing.get("12020101")).isEqualByComparingTo("180000");      // export receivable
        assertThat(closing.get("12040401")).isEqualByComparingTo("1120000");     // bank: 1,000,000 + 200,000 - 80,000
        assertThat(closing.get("12000000")).isEqualByComparingTo(                // current assets roll up their children
            closing.get("12010000").add(closing.get("12020100")).add(closing.get("12040000")));
        assertThat(reports.partyLedger(supplierId, day.withDayOfMonth(1), day).closing()).isEqualByComparingTo("-150000");
    }
}

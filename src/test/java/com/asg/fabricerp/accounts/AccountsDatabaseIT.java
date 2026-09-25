package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Accounts against real PostgreSQL: Flyway builds the schema (V18 included), Hibernate validates the
 * entities, and a month of mill activity posts through the standard rules to a balanced trial
 * balance - with the append-only trigger watching.
 *
 * <p>Opt-in, because it wipes the accounts tables. Point {@code FABRICERP_IT_DB_URL} at a
 * <b>throwaway</b> database, never the one the app uses:
 * <pre>
 *   createdb fabricerp_accounts_it
 *   FABRICERP_IT_DB_URL=jdbc:postgresql://localhost:5432/fabricerp_accounts_it mvn -Dtest=AccountsDatabaseIT test
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class AccountsDatabaseIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private AccountsSetupService setup;
    @Autowired private GeneralLedgerService ledger;
    @Autowired private LedgerReportService reports;
    @Autowired private CreditService credit;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private OrgContext context;

    private long orgId;
    private long customerId;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        // TRUNCATE, not DELETE: the ledger's append-only trigger fires on row deletes, as it should.
        jdbc.execute("""
            TRUNCATE acc_gl_entry_lines, acc_gl_entries, acc_credit_limits, acc_posting_rule_lines,
                     acc_posting_rules, acc_periods, acc_cost_centres, acc_accounts RESTART IDENTITY CASCADE
            """);
        jdbc.update("DELETE FROM gbl_issued_numbers");
        jdbc.update("DELETE FROM gbl_number_counters");
        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        jdbc.update("UPDATE org_organizations SET fiscal_year_start_month = 7 WHERE id = ?", orgId);
        Long unitId = jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = 'AF'", Long.class, orgId);
        jdbc.update("""
            INSERT INTO pty_parties (organization_id, code, name, active, deleted, version, created_by, created_at)
            SELECT ?, 'IT-CUS', 'IT Garments Ltd', TRUE, FALSE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM pty_parties WHERE organization_id = ? AND code = 'IT-CUS')
            """, orgId, orgId);
        customerId = jdbc.queryForObject("SELECT id FROM pty_parties WHERE organization_id = ? AND code = 'IT-CUS'", Long.class, orgId);

        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(unitId);
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.username()).thenReturn("it");
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());

        setup.loadStandardChart();
        LocalDate today = LocalDate.now();
        int fyStart = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
        setup.generateFiscalYear(fyStart);
        day = today;
    }

    @Test
    void aMonthOfActivityPostsToABalancedTrialBalance() {
        // Greige received from weaving, then delivered at cost; invoiced with VAT, part-paid.
        ledger.post(PostingCommand.of("GR", 1L, PostingEvent.GREIGE_RECEIVED_FROM_WEAVING, day, new BigDecimal("50000"), null, "GR-1"));
        ledger.post(PostingCommand.of("FD", 2L, PostingEvent.DELIVERY, day, new BigDecimal("30000"), null, "FD-1"));
        ledger.post(new PostingCommand("CI", 3L, PostingEvent.INVOICE, day, "BDT", BigDecimal.ONE,
            Map.of("net", new BigDecimal("40000"), "vat", new BigDecimal("6000")), customerId, null, "CI-1"));
        ledger.post(PostingCommand.of("RC", 4L, PostingEvent.RECEIPT, day, new BigDecimal("16000"), customerId, "Receipt"));
        ledger.postManualJournal(day, "Opening capital", List.of(
            new GeneralLedgerService.JournalLine("1102", Side.DEBIT, new BigDecimal("100000"), null, null),
            new GeneralLedgerService.JournalLine("3101", Side.CREDIT, new BigDecimal("100000"), null, null)));

        LedgerReportService.TrialBalance tb = reports.trialBalance(day.withDayOfMonth(1), day);
        assertThat(tb.balanced()).isTrue();
        assertThat(tb.totalDebit()).isEqualByComparingTo(tb.totalCredit());

        // The customer owes 46,000 invoiced less 16,000 received - from the ledger, not a stored balance.
        assertThat(credit.receivable(customerId, day)).isEqualByComparingTo("30000");
        LedgerReportService.Ledger customer = reports.partyLedger(customerId, day.withDayOfMonth(1), day);
        assertThat(customer.lines()).hasSize(3);   // net, vat, receipt
        assertThat(customer.closing()).isEqualByComparingTo("30000");

        // Summary rows total their children: current assets includes WIP, stock, bank and receivables.
        LedgerReportService.TrialBalanceRow wip = row(tb, "1143");
        assertThat(wip.closing()).isEqualByComparingTo("-50000");   // WIP credited by the greige receipt

        // Credit: a 50,000 limit leaves 20,000 of headroom against 30,000 owed.
        credit.grant(new CreditService.LimitRequest(customerId, new BigDecimal("50000"), "BDT", day, null, null, null));
        assertThat(credit.exposureOf(customerId, BigDecimal.ZERO, day).headroom()).isEqualByComparingTo("20000");
        assertThat(credit.exposureOf(customerId, new BigDecimal("25000"), day).verdict()).isEqualTo(CreditService.Verdict.OVER_LIMIT);
    }

    @Test
    void aReversalNetsToZeroAndTheLedgerRefusesEdits() {
        GlEntry delivery = ledger.post(PostingCommand.of("FD", 2L, PostingEvent.DELIVERY, day, new BigDecimal("1200"), null, "FD-2"));
        GlEntry reversal = ledger.reverse(delivery.getId(), "Wrong cost");

        assertThat(reversal.getReversesEntryId()).isEqualTo(delivery.getId());
        assertThat(jdbc.queryForObject("SELECT reversed_by_id FROM acc_gl_entries WHERE id = ?", Long.class, delivery.getId()))
            .isEqualTo(reversal.getId());
        LedgerReportService.Ledger cogs = reports.accountLedger("5101", day.withDayOfMonth(1), day);
        assertThat(cogs.closing()).isEqualByComparingTo("0");
        assertThat(cogs.lines()).hasSize(2);

        assertThatThrownBy(() -> jdbc.update("UPDATE acc_gl_entries SET narration = 'edited' WHERE id = ?", delivery.getId()))
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM acc_gl_entry_lines WHERE entry_id = ?", delivery.getId()))
            .hasMessageContaining("append-only");
    }

    @Test
    void closingAPeriodStopsPostingsIntoIt() {
        AccountingPeriod current = setup.periodsOf(setup.fiscalYears().getFirst()).stream()
            .filter(p -> p.covers(day)).findFirst().orElseThrow();
        setup.closePeriod(current.getId());
        assertThatThrownBy(() -> ledger.post(PostingCommand.of("FD", 9L, PostingEvent.DELIVERY, day, BigDecimal.TEN, null, "x")))
            .isInstanceOf(ClosedPeriodException.class);
        setup.reopenPeriod(current.getId(), "Late correction");
        assertThat(ledger.post(PostingCommand.of("FD", 9L, PostingEvent.DELIVERY, day, BigDecimal.TEN, null, "x")).getId()).isNotNull();
    }

    private static LedgerReportService.TrialBalanceRow row(LedgerReportService.TrialBalance tb, String code) {
        return tb.rows().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }
}

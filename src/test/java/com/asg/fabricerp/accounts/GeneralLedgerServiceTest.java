package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.AccountType;
import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Posting: the order of checks, and every way a posting is refused before anything is saved. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneralLedgerServiceTest {

    private static final Long ORG = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);

    @Mock private PostingRuleRepository rules;
    @Mock private AccountRepository accounts;
    @Mock private AccountingPeriodRepository periods;
    @Mock private GlEntryRepository entries;
    @Mock private BusinessNumberService numbers;
    @Mock private OrgContext context;

    private GeneralLedgerService ledger;
    private AccountingPeriod september;

    @BeforeEach
    void setUp() {
        ledger = new GeneralLedgerService(rules, accounts, periods, entries, numbers, context);
        when(context.requireOrganizationId()).thenReturn(ORG);
        when(context.businessUnitId()).thenReturn(10L);
        // Each voucher series numbers with its own prefix, so the entry number shows which series was used.
        when(numbers.next(any(), any())).thenAnswer(i -> ((BusinessSeries) i.getArgument(0)).defaultPrefix() + "-AF-000001");
        when(entries.save(any(GlEntry.class))).thenAnswer(i -> i.getArgument(0));

        september = new AccountingPeriod("FY2026-P03", "Sep 2026", 2026, 3, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        september.setId(33L);
        when(periods.findCovering(ORG, DAY)).thenReturn(Optional.of(september));

        account("1144", "Finished fabric", AccountType.ASSET, false);
        account("5101", "Cost of fabric sold", AccountType.EXPENSE, false);
        account("1110", "Trade receivables", AccountType.ASSET, true);
        account("4101", "Sales - export", AccountType.INCOME, false);
    }

    private void account(String code, String name, AccountType type, boolean control) {
        Account account = new Account(code, name, type).asControl(control);
        when(accounts.findByCode(ORG, code)).thenReturn(Optional.of(account));
    }

    private void rule(String event, String debit, String credit) {
        PostingRule rule = new PostingRule(event, event, event, LocalDate.of(2026, 1, 1)).debitAndCredit(debit, credit);
        when(rules.forEvent(ORG, event)).thenReturn(List.of(rule));
    }

    @Test
    void anEventPostsThroughItsRule() {
        rule(PostingEvent.DELIVERY, "5101", "1144");

        GlEntry entry = ledger.post(PostingCommand.of("FD", 5L, PostingEvent.DELIVERY, DAY, new BigDecimal("25000"), null, "FD-0005"));

        assertThat(entry.getEntryNo()).isEqualTo("SV-AF-000001");   // a delivery is a sales voucher
        assertThat(entry.getVoucherType()).isEqualTo(VoucherType.SALES);
        assertThat(entry.getPeriodId()).isEqualTo(33L);
        assertThat(entry.getOrganizationId()).isEqualTo(ORG);
        assertThat(entry.getLines()).extracting(GlEntryLine::getAccountCode, GlEntryLine::getSide)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("5101", Side.DEBIT), org.assertj.core.groups.Tuple.tuple("1144", Side.CREDIT));
        assertThat(entry.totalDebits()).isEqualByComparingTo("25000");
    }

    @Test
    void aManualVoucherIsNumberedInTheSeriesItsTypeNames() {
        List<GeneralLedgerService.JournalLine> lines = List.of(
            new GeneralLedgerService.JournalLine("5101", Side.DEBIT, BigDecimal.TEN, null, null),
            new GeneralLedgerService.JournalLine("1144", Side.CREDIT, BigDecimal.TEN, null, null));
        assertThat(ledger.postManualJournal(DAY, "x", lines).getEntryNo()).startsWith("JV-");
        assertThat(ledger.postManualJournal(VoucherType.PAYMENT, DAY, "x", lines).getEntryNo()).startsWith("PV-");
        assertThat(ledger.postManualJournal(VoucherType.CONTRA, DAY, "x", lines).getVoucherType()).isEqualTo(VoucherType.CONTRA);
        // Sales, purchase and production vouchers come from their documents only.
        assertThatThrownBy(() -> ledger.postManualJournal(VoucherType.SALES, DAY, "x", lines))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noRuleMeansNoPosting_loudly() {
        when(rules.forEvent(ORG, PostingEvent.DELIVERY)).thenReturn(List.of());
        assertThatThrownBy(() -> ledger.post(PostingCommand.of("FD", 5L, PostingEvent.DELIVERY, DAY, BigDecimal.TEN, null, "x")))
            .isInstanceOf(NoPostingRuleException.class).hasMessageContaining("DELIVERY");
        verify(entries, never()).save(any());
    }

    @Test
    void aClosedPeriodTakesNothing() {
        rule(PostingEvent.DELIVERY, "5101", "1144");
        september.close(LocalDate.of(2026, 10, 3), "accountant");
        assertThatThrownBy(() -> ledger.post(PostingCommand.of("FD", 5L, PostingEvent.DELIVERY, DAY, BigDecimal.TEN, null, "x")))
            .isInstanceOf(ClosedPeriodException.class).hasMessageContaining("closed");
        verify(entries, never()).save(any());
    }

    @Test
    void aDateNoPeriodCoversIsRefused() {
        LocalDate later = LocalDate.of(2027, 2, 1);
        when(periods.findCovering(ORG, later)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ledger.postManualJournal(later, "x", List.of(
                new GeneralLedgerService.JournalLine("5101", Side.DEBIT, BigDecimal.TEN, null, null),
                new GeneralLedgerService.JournalLine("1144", Side.CREDIT, BigDecimal.TEN, null, null))))
            .isInstanceOf(ClosedPeriodException.class).hasMessageContaining("generate the fiscal year");
    }

    @Test
    void aControlAccountLineMustNameTheParty() {
        PostingRule invoice = new PostingRule("INV", "Invoice", PostingEvent.INVOICE, LocalDate.of(2026, 1, 1));
        invoice.addLine(Side.DEBIT, "1110", "amount");
        invoice.addLine(Side.CREDIT, "4101", "amount");
        when(rules.forEvent(ORG, PostingEvent.INVOICE)).thenReturn(List.of(invoice));

        assertThatThrownBy(() -> ledger.post(PostingCommand.of("CI", 9L, PostingEvent.INVOICE, DAY, BigDecimal.TEN, null, "x")))
            .isInstanceOf(ControlAccountException.class).hasMessageContaining("customer or supplier");

        GlEntry entry = ledger.post(PostingCommand.of("CI", 9L, PostingEvent.INVOICE, DAY, BigDecimal.TEN, 77L, "x"));
        assertThat(entry.getLines().get(0).getPartyId()).isEqualTo(77L);
        assertThat(entry.getLines().get(1).getPartyId()).isNull();   // only control lines carry the party
    }

    @Test
    void aManualJournalCannotTouchAControlAccount() {
        assertThatThrownBy(() -> ledger.postManualJournal(DAY, "write-off", List.of(
                new GeneralLedgerService.JournalLine("5101", Side.DEBIT, BigDecimal.TEN, null, null),
                new GeneralLedgerService.JournalLine("1110", Side.CREDIT, BigDecimal.TEN, null, null))))
            .isInstanceOf(ControlAccountException.class).hasMessageContaining("no manual");
    }

    @Test
    void anUnbalancedJournalIsNeverSaved() {
        assertThatThrownBy(() -> ledger.postManualJournal(DAY, "x", List.of(
                new GeneralLedgerService.JournalLine("5101", Side.DEBIT, new BigDecimal("10"), null, null),
                new GeneralLedgerService.JournalLine("1144", Side.CREDIT, new BigDecimal("9"), null, null))))
            .isInstanceOf(UnbalancedEntryException.class);
        verify(entries, never()).save(any());
    }

    @Test
    void aSummaryAccountTakesNoEntries() {
        Account parent = new Account("1100", "Current assets", AccountType.ASSET);
        new Account("1101", "Cash", AccountType.ASSET).under(parent);
        when(accounts.findByCode(ORG, "1100")).thenReturn(Optional.of(parent));
        assertThatThrownBy(() -> ledger.postManualJournal(DAY, "x", List.of(
                new GeneralLedgerService.JournalLine("1100", Side.DEBIT, BigDecimal.TEN, null, null),
                new GeneralLedgerService.JournalLine("1144", Side.CREDIT, BigDecimal.TEN, null, null))))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("sub-accounts");
    }

    @Test
    void aReversalMirrorsTheLinesAndMarksTheOriginal() {
        GlEntry original = new GlEntry(10L, "VCH-AF-000001", "MANUAL", null, PostingEvent.MANUAL_JOURNAL, DAY, 33L, "BDT", BigDecimal.ONE, "accrual");
        original.setOrganizationId(ORG);
        original.setId(500L);
        original.addLine("5101", Side.DEBIT, new BigDecimal("40"), null, null, null);
        original.addLine("1144", Side.CREDIT, new BigDecimal("40"), null, null, null);
        when(entries.findScoped(500L, ORG)).thenReturn(Optional.of(original));
        when(periods.findCovering(eq(ORG), eq(LocalDate.now()))).thenReturn(Optional.of(
            new AccountingPeriod("NOW", "Now", 2026, 1, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
        when(entries.save(any(GlEntry.class))).thenAnswer(i -> { GlEntry e = i.getArgument(0); e.setId(501L); return e; });

        GlEntry reversal = ledger.reverse(500L, "Posted twice");

        assertThat(reversal.getReversesEntryId()).isEqualTo(500L);
        assertThat(reversal.getLines()).extracting(GlEntryLine::getSide).containsExactly(Side.CREDIT, Side.DEBIT);
        assertThat(original.getReversedById()).isEqualTo(501L);
        assertThatThrownBy(() -> ledger.reverse(500L, "again")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already been reversed");
        assertThatThrownBy(() -> ledger.reverse(500L, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}

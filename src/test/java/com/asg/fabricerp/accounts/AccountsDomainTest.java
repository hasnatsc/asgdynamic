package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.AccountType;
import com.asg.fabricerp.accounts.AccountFlags.AccountUsage;
import com.asg.fabricerp.accounts.AccountFlags.CostCentreType;
import com.asg.fabricerp.accounts.AccountFlags.PeriodStatus;
import com.asg.fabricerp.accounts.AccountFlags.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The ledger's invariants, on the entities alone. */
class AccountsDomainTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);

    private static GlEntry entry(String currency, BigDecimal fx) {
        return new GlEntry(1L, "VCH-1", "MANUAL", null, "MANUAL_JOURNAL", DAY, 1L, currency, fx, "test");
    }

    // ---------------------------------------------------------------------------- balance

    @Test
    void aBalancedEntryPasses() {
        GlEntry e = entry("BDT", BigDecimal.ONE);
        e.addLine("5101", Side.DEBIT, new BigDecimal("1000.00"), null, null, null);
        e.addLine("1144", Side.CREDIT, new BigDecimal("1000.00"), null, null, null);
        assertThatCode(e::requireBalanced).doesNotThrowAnyException();
        assertThat(e.totalDebits()).isEqualByComparingTo("1000");
    }

    @Test
    void anUnbalancedEntryIsRefused() {
        GlEntry e = entry("BDT", BigDecimal.ONE);
        e.addLine("5101", Side.DEBIT, new BigDecimal("1000.00"), null, null, null);
        e.addLine("1144", Side.CREDIT, new BigDecimal("999.99"), null, null, null);
        assertThatThrownBy(e::requireBalanced).isInstanceOf(UnbalancedEntryException.class)
            .hasMessageContaining("does not balance");
    }

    @Test
    void anEntryNeedsTwoLines() {
        GlEntry e = entry("BDT", BigDecimal.ONE);
        e.addLine("5101", Side.DEBIT, new BigDecimal("10"), null, null, null);
        assertThatThrownBy(e::requireBalanced).isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void aForeignCurrencyEntryMustAlsoBalanceInBdt() {
        // At 1.005, each 1.00 line converts to 1.01 (3.03 in all) while one 3.00 line converts to 3.02.
        GlEntry e = entry("USD", new BigDecimal("1.005000"));
        e.addLine("1110", Side.DEBIT, new BigDecimal("1.00"), 7L, null, null);
        e.addLine("1110", Side.DEBIT, new BigDecimal("1.00"), 7L, null, null);
        e.addLine("1110", Side.DEBIT, new BigDecimal("1.00"), 7L, null, null);
        e.addLine("4101", Side.CREDIT, new BigDecimal("3.00"), null, null, null);
        assertThat(e.totalDebits()).isEqualByComparingTo(e.totalCredits());   // balanced in USD...
        assertThatThrownBy(e::requireBalanced).isInstanceOf(UnbalancedEntryException.class)
            .hasMessageContaining("BDT");                                      // ...but not in the books
    }

    @Test
    void aLineCarriesAPositiveAmount() {
        GlEntry e = entry("BDT", BigDecimal.ONE);
        assertThatThrownBy(() -> e.addLine("5101", Side.DEBIT, new BigDecimal("-5"), null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------------- chart

    @Test
    void placingAnAccountUnderAParentMakesTheParentASummary() {
        Account parent = new Account("1100", "Current assets", AccountType.ASSET);
        Account child = new Account("1101", "Cash", AccountType.ASSET).under(parent);
        assertThat(parent.getUsage()).isEqualTo(AccountUsage.SUMMARY);
        assertThat(child.getParent()).isSameAs(parent);
    }

    @Test
    void aParentMustBeOfTheSameType() {
        Account income = new Account("4000", "Income", AccountType.INCOME);
        assertThatThrownBy(() -> new Account("1101", "Cash", AccountType.ASSET).under(income))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot sit under");
    }

    @Test
    void aParentPickerOptionSaysWhereTheAccountSits() {
        Account assets = new Account("1000", "Assets", AccountType.ASSET);
        Account current = new Account("1100", "Current assets", AccountType.ASSET).under(assets);
        Account cash = new Account("1101", "Cash", AccountType.ASSET).under(current);

        assertThat(AccountsSetupService.option(assets).sub()).isEqualTo("Asset · top level");
        assertThat(AccountsSetupService.option(cash))
            .extracting(o -> o.code(), o -> o.text(), o -> o.sub())
            .containsExactly("1101", "Cash", "Asset in Assets › Current assets");
    }

    @Test
    void theTreeCannotLoop() {
        Account a = new Account("1", "A", AccountType.ASSET);
        Account b = new Account("2", "B", AccountType.ASSET).under(a);
        assertThatThrownBy(() -> a.under(b)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("loop");
    }

    @Test
    void aSummaryCannotBeAControlAccount() {
        Account parent = new Account("1100", "Current assets", AccountType.ASSET);
        new Account("1101", "Cash", AccountType.ASSET).under(parent);
        assertThatThrownBy(() -> parent.asControl(true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aControlAccountCannotBecomeAParent() {
        Account receivables = new Account("1110", "Trade receivables", AccountType.ASSET).asControl(true);
        assertThatThrownBy(() -> new Account("1111", "Export receivables", AccountType.ASSET).under(receivables))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------------- periods

    @Test
    void aClosedPeriodRefusesPostingsAndReopensOnlyWithAReason() {
        AccountingPeriod p = new AccountingPeriod("FY2026-P03", "Sep 2026", 2026, 3,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(p.covers(DAY)).isTrue();
        assertThat(p.acceptsPostings()).isTrue();
        p.close(LocalDate.of(2026, 10, 5), "accountant");
        assertThat(p.acceptsPostings()).isFalse();
        assertThatThrownBy(() -> p.reopen(" ")).isInstanceOf(IllegalArgumentException.class);
        p.reopen("Late supplier bill");
        assertThat(p.getPeriodStatus()).isEqualTo(PeriodStatus.TEMPORARILY_OPEN);
        assertThat(p.acceptsPostings()).isTrue();
    }

    // ---------------------------------------------------------------------------- rules

    @Test
    void aRuleMustBalancePerAmountKey() {
        PostingRule invoice = new PostingRule("INV", "Invoice", PostingEvent.INVOICE, DAY);
        invoice.addLine(Side.DEBIT, "1110", "net");
        invoice.addLine(Side.DEBIT, "1110", "vat");
        invoice.addLine(Side.CREDIT, "4101", "net");
        invoice.addLine(Side.CREDIT, "2103", "vat");
        assertThatCode(invoice::requireSelfBalancing).doesNotThrowAnyException();
        assertThatCode(() -> invoice.requireBalanced(Map.of("net", new BigDecimal("100"), "vat", new BigDecimal("15"))))
            .doesNotThrowAnyException();

        PostingRule lopsided = new PostingRule("BAD", "Bad", PostingEvent.INVOICE, DAY);
        lopsided.addLine(Side.DEBIT, "1110", "net");
        lopsided.addLine(Side.CREDIT, "4101", "net");
        lopsided.addLine(Side.CREDIT, "2103", "vat");
        assertThatThrownBy(lopsided::requireSelfBalancing).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vat");
    }

    @Test
    void aRuleAskingForAnAmountThePostingLacksFailsInsteadOfPostingZero() {
        PostingRule invoice = new PostingRule("INV", "Invoice", PostingEvent.INVOICE, DAY);
        invoice.addLine(Side.DEBIT, "1110", "net");
        invoice.addLine(Side.CREDIT, "4101", "net");
        assertThatThrownBy(() -> invoice.requireBalanced(Map.of("amount", BigDecimal.TEN)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs amount 'net'");
    }

    @Test
    void aSupersededRuleStopsTheDayBeforeItsSuccessor() {
        PostingRule rule = new PostingRule("R1", "Delivery", PostingEvent.DELIVERY, LocalDate.of(2026, 1, 1));
        rule.supersededFrom(LocalDate.of(2026, 7, 1));
        assertThat(rule.coversDate(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(rule.coversDate(LocalDate.of(2026, 7, 1))).isFalse();
    }

    // ---------------------------------------------------------------------------- credit, centres

    @Test
    void securedCoverIsDeductedBeforeTheLimitApplies() {
        CreditLimit limit = new CreditLimit(7L, "BDT", new BigDecimal("1000000"), DAY);
        limit.securedBy(new BigDecimal("300000"));
        // Owes 1.2M, of which 300k is LC-backed: 100k headroom remains.
        assertThat(limit.headroomAgainst(new BigDecimal("1200000"))).isEqualByComparingTo("100000");
        assertThatThrownBy(() -> limit.placeOnHold("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unabsorbedCostGoesOnlyToAnOverheadCentre() {
        CostCentre shed = new CostCentre("WV-S2", "Weaving shed 2", CostCentreType.PRODUCTION);
        CostCentre admin = new CostCentre("ADM", "Administration", CostCentreType.ADMINISTRATIVE);
        assertThatThrownBy(() -> shed.absorbingInto(admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> shed.absorbingInto(new CostCentre("OH", "Factory overhead", CostCentreType.OVERHEAD)))
            .doesNotThrowAnyException();
    }
}

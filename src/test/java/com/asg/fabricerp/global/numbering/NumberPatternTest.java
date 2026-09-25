package com.asg.fabricerp.global.numbering;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumberPatternTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 25);

    private static String render(String pattern, String prefix, long seq, int width, int fyStartMonth) {
        return NumberPattern.render(pattern, new NumberPattern.Values(prefix, "af",
            FinancialYear.containing(DAY, fyStartMonth), DAY, seq, width));
    }

    @Test
    void theDefaultIsPrefixYearSequence() {
        assertThat(render(NumberPattern.DEFAULT, "EMP", 1, 6, 1)).isEqualTo("EMP-2026-000001");
        assertThat(render(NumberPattern.DEFAULT, "VCH", 42, 6, 1)).isEqualTo("VCH-2026-000042");
    }

    @Test
    void everyTokenRenders() {
        assertThat(render("{PREFIX}/{BRANCH}/{YY}{SEQ}", "SO", 7, 4, 1)).isEqualTo("SO/AF/260007");
        assertThat(render("{PREFIX}{BRANCH}{SEQ}", "BPO", 1, 6, 1)).isEqualTo("BPOAF000001");   // the legacy layout
        assertThat(render("{PREFIX}-{YYYY}-{SEQ}", "SO", 1, 3, 1)).isEqualTo("SO-2026-001");
        // V20: customers continue the legacy CAF000001..CAF000130 series.
        assertThat(render("{PREFIX}{SEQ}", "CAF", 131, 6, 1)).isEqualTo("CAF000131");
    }

    @Test
    void theFinancialYearIsTheOneTheDateFallsIn() {
        // July-start year: 25 Sep 2026 is in FY 2026; 30 Jun 2026 closes FY 2025.
        assertThat(render("{PREFIX}-{FY}-{SEQ}", "SO", 1, 6, 7)).isEqualTo("SO-2026-000001");
        assertThat(NumberPattern.render("{PREFIX}-{FY}-{SEQ}", new NumberPattern.Values("SO", null,
            FinancialYear.containing(LocalDate.of(2026, 6, 30), 7), LocalDate.of(2026, 6, 30), 1, 6)))
            .isEqualTo("SO-2025-000001");
    }

    @Test
    void theSequenceGrowsPastItsWidthRatherThanTruncating() {
        assertThat(render(NumberPattern.DEFAULT, "SO", 1_234_567, 6, 1)).isEqualTo("SO-2026-1234567");
    }

    @Test
    void aBranchTokenWithNoBranchFailsLoudly() {
        assertThatThrownBy(() -> NumberPattern.render("{PREFIX}-{BRANCH}-{SEQ}",
                new NumberPattern.Values("SO", null, FinancialYear.containing(DAY, 1), DAY, 1, 6)))
            .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------------ validation

    @Test
    void theDefaultsAreValid() {
        assertThatCode(() -> NumberPattern.validate("SO", NumberPattern.DEFAULT, 6,
            ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION)).doesNotThrowAnyException();
        assertThatCode(() -> NumberPattern.validate("SO", "{PREFIX}-{BRANCH}-{FY}-{SEQ}", 6,
            ResetPolicy.FINANCIAL_YEAR, CounterScope.BRANCH)).doesNotThrowAnyException();
        assertThatCode(() -> NumberPattern.validate("SO", "{PREFIX}{SEQ}", 6,
            ResetPolicy.NEVER, CounterScope.ORGANIZATION)).doesNotThrowAnyException();
    }

    @Test
    void aPerBranchCounterMustPutTheBranchInTheNumber() {
        assertThatThrownBy(() -> NumberPattern.validate("SO", NumberPattern.DEFAULT, 6,
                ResetPolicy.FINANCIAL_YEAR, CounterScope.BRANCH))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{BRANCH}");
    }

    @Test
    void aYearlyResetMustPutThatYearInTheNumber() {
        assertThatThrownBy(() -> NumberPattern.validate("SO", "{PREFIX}-{SEQ}", 6,
                ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{FY}");
        assertThatThrownBy(() -> NumberPattern.validate("SO", "{PREFIX}-{FY}-{SEQ}", 6,
                ResetPolicy.CALENDAR_YEAR, CounterScope.ORGANIZATION))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("{YYYY}");
    }

    @Test
    void prefixAndSequenceAreRequired_andUnknownTokensRefused() {
        assertThatThrownBy(() -> NumberPattern.validate("SO", "INV-{FY}-{SEQ}", 6, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .hasMessageContaining("{PREFIX}");
        assertThatThrownBy(() -> NumberPattern.validate("SO", "{PREFIX}-{FY}", 6, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .hasMessageContaining("{SEQ}");
        assertThatThrownBy(() -> NumberPattern.validate("SO", "{PREFIX}-{MONTH}-{SEQ}", 6, ResetPolicy.NEVER, CounterScope.ORGANIZATION))
            .hasMessageContaining("may contain only");
        assertThatThrownBy(() -> NumberPattern.validate("SO", "{PREFIX} {SEQ}", 6, ResetPolicy.NEVER, CounterScope.ORGANIZATION))
            .hasMessageContaining("may contain only");
    }

    @Test
    void prefixAndWidthAreBounded() {
        assertThatThrownBy(() -> NumberPattern.validate("so", NumberPattern.DEFAULT, 6, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .hasMessageContaining("Prefix");
        assertThatThrownBy(() -> NumberPattern.validate("9SO", NumberPattern.DEFAULT, 6, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .hasMessageContaining("Prefix");
        assertThatThrownBy(() -> NumberPattern.validate("SO", NumberPattern.DEFAULT, 2, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .hasMessageContaining("width");
        assertThatThrownBy(() -> NumberPattern.validate("SO", NumberPattern.DEFAULT, 13, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION))
            .hasMessageContaining("width");
    }
}

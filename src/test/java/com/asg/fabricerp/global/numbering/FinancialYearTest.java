package com.asg.fabricerp.global.numbering;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialYearTest {

    @Test
    void aJanuaryStartIsTheCalendarYear() {
        FinancialYear fy = FinancialYear.containing(LocalDate.of(2026, 12, 31), 1);
        assertThat(fy.label()).isEqualTo("2026");
        assertThat(fy.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void aJulyStartYearIsNamedForTheYearItStartsIn() {
        assertThat(FinancialYear.containing(LocalDate.of(2026, 6, 30), 7).label()).isEqualTo("2025");
        assertThat(FinancialYear.containing(LocalDate.of(2026, 7, 1), 7).label()).isEqualTo("2026");
        assertThat(FinancialYear.containing(LocalDate.of(2027, 6, 30), 7).label()).isEqualTo("2026");

        FinancialYear fy = FinancialYear.containing(LocalDate.of(2026, 9, 25), 7);
        assertThat(fy.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2027, 6, 30));
    }

    @Test
    void eachResetPolicyKeysItsOwnPeriod() {
        LocalDate lastDayOfFy2025 = LocalDate.of(2026, 6, 30);
        LocalDate firstDayOfFy2026 = LocalDate.of(2026, 7, 1);

        assertThat(ResetPolicy.FINANCIAL_YEAR.periodKey(lastDayOfFy2025, 7)).isEqualTo("FY2025");
        assertThat(ResetPolicy.FINANCIAL_YEAR.periodKey(firstDayOfFy2026, 7)).isEqualTo("FY2026");
        assertThat(ResetPolicy.CALENDAR_YEAR.periodKey(lastDayOfFy2025, 7)).isEqualTo("CY2026");
        assertThat(ResetPolicy.NEVER.periodKey(firstDayOfFy2026, 7)).isEmpty();
    }

    @Test
    void theStartMonthIsAMonth() {
        assertThatThrownBy(() -> FinancialYear.containing(LocalDate.of(2026, 1, 1), 13))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.asg.fabricerp.global.numbering;

import java.time.LocalDate;

/**
 * The financial year a date falls in, for an organization whose year starts on the first of
 * {@code startMonth}. Named by the calendar year it <em>starts</em> in: with a July start,
 * 2026-09-25 and 2027-06-30 are both FY 2026, and 2026-06-30 is FY 2025. With the default
 * January start the financial year is the calendar year.
 */
public record FinancialYear(int startYear, int startMonth) {

    public FinancialYear {
        if (startMonth < 1 || startMonth > 12) {
            throw new IllegalArgumentException("Financial year start month must be 1-12, got " + startMonth);
        }
    }

    public static FinancialYear containing(LocalDate date, int startMonth) {
        int year = date.getMonthValue() >= startMonth ? date.getYear() : date.getYear() - 1;
        return new FinancialYear(year, startMonth);
    }

    public LocalDate start() { return LocalDate.of(startYear, startMonth, 1); }

    public LocalDate end()   { return start().plusYears(1).minusDays(1); }

    /** What {@code {FY}} renders as. */
    public String label()    { return Integer.toString(startYear); }
}

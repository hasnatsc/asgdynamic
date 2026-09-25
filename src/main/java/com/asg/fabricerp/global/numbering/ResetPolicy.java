package com.asg.fabricerp.global.numbering;

import java.time.LocalDate;

/**
 * When a series starts again from 1. Each period is its own counter row, so a reset is never an
 * update that could race: the first number of FY 2027 simply finds no FY2027 row yet.
 *
 * <p>A reset is only safe if the number itself says which period it belongs to - otherwise
 * FY 2027's first number would repeat FY 2026's. {@link NumberPattern#validate} therefore
 * requires the matching token, and so does a {@code CHECK} on {@code gbl_numbering_schemes}.
 */
public enum ResetPolicy {

    NEVER,
    FINANCIAL_YEAR,
    CALENDAR_YEAR;

    /** The counter's period key: {@code ""}, {@code FY2026} or {@code CY2026}. */
    public String periodKey(LocalDate date, int fiscalYearStartMonth) {
        return switch (this) {
            case NEVER -> "";
            case FINANCIAL_YEAR -> "FY" + FinancialYear.containing(date, fiscalYearStartMonth).label();
            case CALENDAR_YEAR -> "CY" + date.getYear();
        };
    }
}

package com.asg.fabricerp.global.numbering;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The layout of a number: literal separators and tokens.
 *
 * <pre>
 *   {PREFIX}  the series prefix                 BK, CUS, VCH
 *   {BRANCH}  the business unit's code          AF
 *   {FY}      financial year (its start year)   2026
 *   {YYYY}    calendar year of the date         2026
 *   {YY}      two-digit calendar year           26
 *   {SEQ}     the counter, zero-padded          000001
 * </pre>
 *
 * The default is {@code {PREFIX}-{FY}-{SEQ}}: {@code BK-2026-000001}. The rules in
 * {@link #validate} are the ones that make a configuration unable to repeat a number; the same
 * rules are {@code CHECK}s on {@code gbl_numbering_schemes}, so a row written by hand cannot skip
 * them either.
 */
public final class NumberPattern {

    public static final String DEFAULT = "{PREFIX}-{FY}-{SEQ}";
    public static final int MIN_WIDTH = 3;
    public static final int MAX_WIDTH = 12;

    private static final Pattern WELL_FORMED = Pattern.compile("^([A-Z0-9/_.-]|\\{(PREFIX|BRANCH|FY|YYYY|YY|SEQ)})+$");
    private static final Pattern PREFIX = Pattern.compile("^[A-Z][A-Z0-9]{0,11}$");
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Z]+)}");

    /** Everything a pattern can draw on for one number. {@code branchCode} may be null if the pattern has no {@code {BRANCH}}. */
    public record Values(String prefix, String branchCode, FinancialYear financialYear, LocalDate date,
                         long sequence, int width) { }

    private NumberPattern() { }

    /** @throws IllegalArgumentException with a message fit for the setup screen */
    public static void validate(String prefix, String pattern, int width, ResetPolicy reset, CounterScope scope) {
        if (prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                "Prefix must be 1-12 capital letters or digits, starting with a letter (e.g. SO, CUS, VCH).");
        }
        if (pattern == null || !WELL_FORMED.matcher(pattern).matches()) {
            throw new IllegalArgumentException(
                "Pattern may contain only {PREFIX}, {BRANCH}, {FY}, {YYYY}, {YY}, {SEQ} and the separators - / _ . "
              + "or capital letters and digits.");
        }
        if (!pattern.contains("{PREFIX}") || !pattern.contains("{SEQ}")) {
            throw new IllegalArgumentException("Pattern must contain {PREFIX} and {SEQ}.");
        }
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw new IllegalArgumentException("Sequence width must be %d-%d digits.".formatted(MIN_WIDTH, MAX_WIDTH));
        }
        if (scope == CounterScope.BRANCH && !pattern.contains("{BRANCH}")) {
            throw new IllegalArgumentException(
                "A per-branch counter needs {BRANCH} in the pattern, or two branches would issue the same number.");
        }
        if (reset == ResetPolicy.FINANCIAL_YEAR && !pattern.contains("{FY}")) {
            throw new IllegalArgumentException(
                "A counter that restarts each financial year needs {FY} in the pattern, or each year would repeat the last.");
        }
        if (reset == ResetPolicy.CALENDAR_YEAR && !pattern.contains("{YYYY}") && !pattern.contains("{YY}")) {
            throw new IllegalArgumentException(
                "A counter that restarts each calendar year needs {YYYY} or {YY} in the pattern.");
        }
    }

    public static boolean usesBranch(String pattern) {
        return pattern.contains("{BRANCH}");
    }

    public static String render(String pattern, Values v) {
        Matcher m = TOKEN.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = switch (m.group(1)) {
                case "PREFIX" -> v.prefix();
                case "BRANCH" -> {
                    if (v.branchCode() == null || v.branchCode().isBlank()) {
                        throw new IllegalStateException("No business unit code to put in {BRANCH}");
                    }
                    yield v.branchCode().toUpperCase();
                }
                case "FY" -> v.financialYear().label();
                case "YYYY" -> Integer.toString(v.date().getYear());
                case "YY" -> "%02d".formatted(v.date().getYear() % 100);
                // Grows past the width rather than truncating: 1000000 at width 6 is still unique.
                case "SEQ" -> String.format("%0" + v.width() + "d", v.sequence());
                default -> throw new IllegalStateException("Unknown token {" + m.group(1) + "}");
            };
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}

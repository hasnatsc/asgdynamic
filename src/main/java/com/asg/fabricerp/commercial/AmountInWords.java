package com.asg.fabricerp.commercial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An amount written out, as a PI, a bill of exchange and a CI state it: "US DOLLAR TWO THOUSAND
 * NINE HUNDRED EIGHTY AND CENTS FIFTY ONLY". Taka is written in crore and lakh, as a Bangladeshi
 * bank reads it; every other currency in millions and thousands.
 */
public final class AmountInWords {

    private static final String[] ONES = {"", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN",
        "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
    private static final String[] TENS = {"", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY"};

    private static final Map<String, String[]> CURRENCY = Map.of(
        "USD", new String[] {"US DOLLAR", "CENTS"},
        "EUR", new String[] {"EURO", "CENTS"},
        "GBP", new String[] {"POUND STERLING", "PENCE"},
        "AUD", new String[] {"AUSTRALIAN DOLLAR", "CENTS"},
        "BDT", new String[] {"TAKA", "PAISA"});

    private AmountInWords() { }

    public static String of(BigDecimal amount, String currency) {
        BigDecimal a = (amount == null ? BigDecimal.ZERO : amount.abs()).setScale(2, RoundingMode.HALF_UP);
        long whole = a.longValue();
        int fraction = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String code = currency == null ? "BDT" : currency.toUpperCase();
        String[] names = CURRENCY.getOrDefault(code, new String[] {code, "CENTS"});
        String words = whole == 0 ? "ZERO" : "BDT".equals(code) ? indian(whole) : international(whole);
        return names[0] + " " + words + (fraction > 0 ? " AND " + names[1] + " " + belowThousand(fraction) : "") + " ONLY";
    }

    static String international(long n) {
        List<String> parts = new ArrayList<>();
        long[] scales = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};
        String[] names = {"TRILLION", "BILLION", "MILLION", "THOUSAND"};
        for (int i = 0; i < scales.length; i++) {
            if (n >= scales[i]) {
                parts.add(belowThousand((int) (n / scales[i])) + " " + names[i]);
                n %= scales[i];
            }
        }
        if (n > 0) parts.add(belowThousand((int) n));
        return String.join(" ", parts);
    }

    /** Crore (10,000,000), lakh (100,000), thousand, hundred. */
    static String indian(long n) {
        List<String> parts = new ArrayList<>();
        if (n >= 10_000_000L) {
            parts.add((n / 10_000_000L >= 1000 ? indian(n / 10_000_000L) : belowThousand((int) (n / 10_000_000L))) + " CRORE");
            n %= 10_000_000L;
        }
        if (n >= 100_000) { parts.add(belowThousand((int) (n / 100_000)) + " LAKH"); n %= 100_000; }
        if (n >= 1_000) { parts.add(belowThousand((int) (n / 1_000)) + " THOUSAND"); n %= 1_000; }
        if (n > 0) parts.add(belowThousand((int) n));
        return String.join(" ", parts);
    }

    static String belowThousand(int n) {
        List<String> parts = new ArrayList<>();
        if (n >= 100) { parts.add(ONES[n / 100] + " HUNDRED"); n %= 100; }
        if (n >= 20) { parts.add(TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "")); }
        else if (n > 0) parts.add(ONES[n]);
        return String.join(" ", parts);
    }
}

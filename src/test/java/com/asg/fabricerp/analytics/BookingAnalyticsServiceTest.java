package com.asg.fabricerp.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The service's arithmetic: folding a chart's tail, margins and shares, and a safe CSV. */
class BookingAnalyticsServiceTest {

    private static Map<String, Object> row(String label, String value, String qty, String sales, String cost) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("label", label);
        r.put("bookings", BigDecimal.ONE);
        r.put("confirmed", BigDecimal.ONE);
        r.put("confirmedQty", new BigDecimal(qty));
        r.put("confirmedValue", new BigDecimal(value));
        r.put("marginSales", sales == null ? null : new BigDecimal(sales));
        r.put("marginCost", cost == null ? null : new BigDecimal(cost));
        return r;
    }

    @Test
    void theTailIsFoldedIntoOtherWithRatiosRecomputedFromSums() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(row("B" + i, "100", "10", "100", "90"));
        rows.add(row("B5", "300", "10", "300", "150"));
        List<Map<String, Object>> top = BookingAnalyticsService.top(rows, 3, true);
        assertEquals(4, top.size());
        Map<String, Object> other = top.get(3);
        assertEquals("Other (3)", other.get("label"));
        assertEquals(0, new BigDecimal("500").compareTo((BigDecimal) other.get("confirmedValue")));
        // (500 - 330) / 500 = 34.0 %, not the average of 10 %, 10 % and 50 %
        assertEquals(new BigDecimal("34.0"), other.get("marginPct"));
    }

    @Test
    void oneExtraRowIsShownRatherThanFoldedIntoAnOtherOfOne() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(row("B" + i, "1", "1", null, null));
        assertEquals(4, BookingAnalyticsService.top(rows, 3, true).size());
        assertNull(BookingAnalyticsService.top(rows, 3, true).get(3).get("other"));
    }

    @Test
    void marginAndPercentAreEmptyRatherThanZeroWithoutABase() {
        assertNull(BookingAnalyticsService.marginPct(null, null));
        assertNull(BookingAnalyticsService.marginPct(BigDecimal.ZERO, BigDecimal.ZERO));
        assertNull(BookingAnalyticsService.percent(BigDecimal.ONE, BigDecimal.ZERO));
        assertEquals(new BigDecimal("12.5"), BookingAnalyticsService.marginPct(new BigDecimal("80"), new BigDecimal("70")));
    }

    @Test
    void numbersLoseTrailingZerosButNeverTurnIntoENotation() {
        assertEquals("62545153.2", BookingAnalyticsService.num(new BigDecimal("62545153.200000")).toPlainString());
        assertEquals("1500", BookingAnalyticsService.num(new BigDecimal("1500.000")).toString());
        assertNull(BookingAnalyticsService.numOrNull(null));
    }

    @Test
    void csvQuotesSeparatorsAndDisarmsFormulas() {
        assertEquals("\"Smith, Jones\",\"say \"\"hi\"\"\",'=HYPERLINK(1),1500\r\n",
            BookingAnalyticsService.csvLine(List.of("Smith, Jones", "say \"hi\"", "=HYPERLINK(1)", new BigDecimal("1500"))));
    }
}

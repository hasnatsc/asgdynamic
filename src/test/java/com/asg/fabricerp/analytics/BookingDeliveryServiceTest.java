package com.asg.fabricerp.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Following one booking down the chain - pure arithmetic over the Production board's lines. */
class BookingDeliveryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void aBookingWithoutProductionOrdersIsNotOrdered_andItsApprovalComesFirst() {
        assertThat(follow(booking("CONFIRMED", "APPROVED", "1000", "2026-10-30"), List.of()).get("stage")).isEqualTo("Not ordered");
        assertThat(follow(booking("PENDING", "SUBMITTED", "1000", "2026-10-30"), List.of()).get("stage")).isEqualTo("Awaiting approval");
        assertThat(follow(booking("DRAFT", "DRAFT", "1000", "2026-10-30"), List.of()).get("stage")).isEqualTo("Draft");
    }

    @Test
    void aBookingSumsItsProductionOrdersLines_andTakesTheFurthestStage() {
        Map<String, Object> b = follow(booking("CONFIRMED", "COMPLETED", "1500", "2026-10-30"), List.of(
            line(11L, "BPO-1", "PROCESSING", true, "FINISHED", Map.of("quantity", "1000", "greigeReceived", "1100", "dyeing", "1000",
                "finishedA", "900", "finishedB", "20", "balance", "1000", "ready", "900")),
            line(12L, "BPO-2", "APPROVED", true, "FINISHED", Map.of("quantity", "400", "balance", "400"))));

        assertThat((BigDecimal) b.get("booked")).isEqualByComparingTo("1500");
        assertThat((BigDecimal) b.get("ordered")).isEqualByComparingTo("1400");
        assertThat((BigDecimal) b.get("stillToOrder")).isEqualByComparingTo("100");
        assertThat((BigDecimal) b.get("finished")).isEqualByComparingTo("920");
        assertThat((BigDecimal) b.get("produced")).isEqualByComparingTo("920");
        assertThat((BigDecimal) b.get("balance")).isEqualByComparingTo("1500");
        assertThat(b.get("stage")).isEqualTo("Finishing");
        assertThat(b.get("open")).isEqualTo(true);
        assertThat(b.get("overdue")).isEqualTo(false);
        assertThat((List<?>) b.get("orders")).hasSize(2);
    }

    @Test
    void deliveredMeansAllItBooked_notJustAllItsOrdersCarry() {
        Map<String, Object> part = follow(booking("CONFIRMED", "PARTIAL", "1000", "2026-10-30"), List.of(
            line(11L, "BPO-1", "COMPLETED", false, "GREIGE", Map.of("quantity", "600", "greigeReceived", "600", "scheduled", "600",
                "delivered", "600", "balance", "0"))));
        Map<String, Object> all = follow(booking("CONFIRMED", "COMPLETED", "600", "2026-10-30"), List.of(
            line(11L, "BPO-1", "COMPLETED", false, "GREIGE", Map.of("quantity", "600", "delivered", "600", "balance", "0"))));

        assertThat(part.get("stage")).isEqualTo("Delivering");
        assertThat(all.get("stage")).isEqualTo("Delivered");
        assertThat(all.get("open")).isEqualTo(false);
    }

    @Test
    void anOpenBookingPastItsDateIsOverdue_oneDueSoonAndNotReadyIsAtRisk_aClosedOneIsNeither() {
        Map<String, Object> overdue = follow(booking("CONFIRMED", "COMPLETED", "1000", "2026-09-20"), List.of(
            line(11L, "BPO-1", "PROCESSING", false, "GREIGE", Map.of("quantity", "1000", "delivered", "300", "balance", "700"))));
        Map<String, Object> atRisk = follow(booking("CONFIRMED", "COMPLETED", "1000", "2026-10-02"), List.of(
            line(11L, "BPO-1", "PROCESSING", false, "GREIGE", Map.of("quantity", "1000", "ready", "200", "balance", "1000"))));
        Map<String, Object> closed = follow(booking("CONFIRMED", "CLOSED", "1000", "2026-09-20"), List.of());

        assertThat(overdue.get("overdue")).isEqualTo(true);
        assertThat(overdue.get("daysLeft")).isEqualTo(-8L);
        assertThat(atRisk.get("overdue")).isEqualTo(false);
        assertThat(atRisk.get("atRisk")).isEqualTo(true);
        assertThat(closed.get("overdue")).isEqualTo(false);

        List<Map<String, Object>> alerts = BookingDeliveryService.alerts(List.of(atRisk, overdue), TODAY);
        assertThat(alerts).extracting(a -> a.get("kind")).containsExactly("OVERDUE", "AT_RISK");
        assertThat((String) alerts.get(0).get("detail")).isEqualTo("700 Yds still to deliver, 8 day(s) late");
    }

    @Test
    void aConfirmedBookingLeftUnorderedForDaysIsAnAlert() {
        Map<String, Object> b = booking("CONFIRMED", "APPROVED", "1000", "2026-11-30");
        b.put("documentDate", LocalDate.of(2026, 9, 20));
        List<Map<String, Object>> alerts = BookingDeliveryService.alerts(List.of(follow(b, List.of())), TODAY);
        assertThat(alerts).singleElement().satisfies(a -> {
            assertThat(a.get("kind")).isEqualTo("NOT_ORDERED");
            assertThat((String) a.get("detail")).startsWith("1,000 of 1,000 still to put on production orders");
        });
    }

    @Test
    void monthsAreOldestFirst() {
        Map<String, Object> sep = follow(booking("CONFIRMED", "APPROVED", "100", "2026-10-30"), List.of());
        Map<String, Object> aug = follow(booking("CONFIRMED", "APPROVED", "300", "2026-10-30"), List.of());
        aug.put("documentDate", LocalDate.of(2026, 8, 3));
        List<Map<String, Object>> months = BookingDeliveryService.byMonth(List.of(sep, aug));
        assertThat(months).extracting(m -> m.get("month")).containsExactly("2026-08", "2026-09");
        assertThat((BigDecimal) months.get(0).get("booked")).isEqualByComparingTo("300");
    }

    private static Map<String, Object> follow(Map<String, Object> booking, List<Map<String, Object>> lines) {
        return BookingDeliveryService.follow(booking, lines, TODAY);
    }

    private static Map<String, Object> booking(String group, String status, String qty, String required) {
        Map<String, Object> b = new HashMap<>();
        b.put("id", 1L);
        b.put("documentNo", "BK-1");
        b.put("documentDate", LocalDate.of(2026, 9, 1));
        b.put("requiredDate", LocalDate.parse(required));
        b.put("statusGroup", group);
        b.put("status", status);
        b.put("quantity", new BigDecimal(qty));
        b.put("buyer", "IT Buyer");
        return b;
    }

    private static Map<String, Object> line(Long bpoId, String no, String status, boolean dyed, String deliverStage, Map<String, String> m) {
        Map<String, Object> l = new HashMap<>();
        l.put("bpoId", bpoId);
        l.put("bpoNo", no);
        l.put("bookingId", 1L);
        l.put("status", status);
        l.put("documentDate", LocalDate.of(2026, 9, 2));
        l.put("requiredDate", LocalDate.of(2026, 10, 30));
        l.put("fabricType", "Solid Dyed");
        l.put("colorName", "Navy");
        l.put("needsProcessing", dyed);
        l.put("deliverStage", deliverStage);
        l.put("routeCode", "PIECE_DYED");
        l.put("shortClosed", false);
        l.put("late", false);
        l.put("uom", "Yds");
        m.forEach((k, v) -> l.put(k, new BigDecimal(v)));
        return l;
    }
}

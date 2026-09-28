package com.asg.fabricerp.production;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** The dashboard's roll-ups over board lines - pure arithmetic, no database. */
class ProductionDashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void anOrderSumsItsLines_andSaysHowFarItHasGot() {
        List<Map<String, Object>> lines = List.of(
            line(1L, "BPO-1", "PROCESSING", "2026-10-30", "Solid Dyed", "Navy", true, "FINISHED",
                Map.of("quantity", "1000", "greigeRequired", "1100", "weaving", "1100", "greigeReceived", "1100",
                    "dyeing", "1000", "greigeIssued", "1000", "finishedA", "600", "finishedB", "50", "balance", "1000")),
            line(1L, "BPO-1", "PROCESSING", "2026-10-30", "Solid Dyed", "Red", true, "FINISHED",
                Map.of("quantity", "500", "greigeRequired", "550", "weaving", "550", "greigeReceived", "300", "balance", "500")));

        Map<String, Object> order = ProductionDashboardService.orders(lines, TODAY).get(0);

        assertThat((BigDecimal) order.get("quantity")).isEqualByComparingTo("1500");
        assertThat((BigDecimal) order.get("finished")).isEqualByComparingTo("650");
        // Produced at the stage it delivers from: finished cloth for a dyed route.
        assertThat((BigDecimal) order.get("produced")).isEqualByComparingTo("650");
        assertThat((BigDecimal) order.get("dyedQuantity")).isEqualByComparingTo("1500");
        assertThat(order.get("stage")).isEqualTo("Finishing");
        assertThat(order.get("colours")).isEqualTo(List.of("Navy", "Red"));
        assertThat(order.get("overdue")).isEqualTo(false);
        assertThat(order.get("daysLeft")).isEqualTo(32L);
    }

    @Test
    void anOpenOrderPastItsDateWithABalanceIsOverdue_aCompletedOneIsNot() {
        Map<String, Object> open = ProductionDashboardService.orders(List.of(line(2L, "BPO-2", "APPROVED", "2026-09-20", "Greige", "Ecru",
            false, "GREIGE", Map.of("quantity", "100", "balance", "40", "delivered", "60"))), TODAY).get(0);
        Map<String, Object> done = ProductionDashboardService.orders(List.of(line(3L, "BPO-3", "COMPLETED", "2026-09-20", "Greige", "Ecru",
            false, "GREIGE", Map.of("quantity", "100", "balance", "40", "delivered", "60"))), TODAY).get(0);

        assertThat(open.get("overdue")).isEqualTo(true);
        assertThat(open.get("daysLeft")).isEqualTo(-8L);
        assertThat(open.get("stage")).isEqualTo("Delivering");
        assertThat(done.get("overdue")).isEqualTo(false);
    }

    @Test
    void pendingCountsWhatIsStillToDoAtEachStage_onOpenLinesOnly() {
        List<Map<String, Object>> lines = List.of(
            line(1L, "BPO-1", "PROCESSING", "2026-10-30", "Solid Dyed", "Navy", true, "FINISHED",
                Map.of("quantity", "1000", "greigeRequired", "1100", "weaving", "800", "greigeReceived", "500",
                    "dyeing", "400", "finishedA", "300", "scheduled", "200", "balance", "1000")),
            line(9L, "BPO-9", "COMPLETED", "2026-08-01", "Solid Dyed", "Navy", true, "FINISHED",
                Map.of("quantity", "999", "greigeRequired", "999", "balance", "999")));

        Map<String, BigDecimal> pending = new HashMap<>();
        ProductionDashboardService.pending(lines).forEach(p -> pending.put((String) p.get("key"), (BigDecimal) p.get("value")));

        assertThat(pending.get("weave")).isEqualByComparingTo("300");
        assertThat(pending.get("receive")).isEqualByComparingTo("300");
        assertThat(pending.get("dye")).isEqualByComparingTo("600");
        assertThat(pending.get("finish")).isEqualByComparingTo("100");
        assertThat(pending.get("schedule")).isEqualByComparingTo("800");
        assertThat(pending.get("deliver")).isEqualByComparingTo("1000");
    }

    @Test
    void fabricTypeAndColourRowsAreLargestFirst() {
        List<Map<String, Object>> lines = List.of(
            line(1L, "BPO-1", "APPROVED", null, "Yarn Dyed", "Check", false, "GREIGE", Map.of("quantity", "100")),
            line(2L, "BPO-2", "APPROVED", null, "Solid Dyed", "Navy", true, "FINISHED", Map.of("quantity", "700")),
            line(3L, "BPO-3", "APPROVED", null, "Solid Dyed", "Red", true, "FINISHED", Map.of("quantity", "200")));

        List<Map<String, Object>> byType = ProductionDashboardService.group(lines, l -> (String) l.get("fabricType"));
        List<Map<String, Object>> byColour = ProductionDashboardService.groupColour(lines);

        assertThat(byType).extracting(r -> r.get("key")).containsExactly("Solid Dyed", "Yarn Dyed");
        assertThat(byType.get(0).get("orders")).isEqualTo(2L);
        assertThat(byColour).extracting(r -> r.get("colour")).containsExactly("Navy", "Red", "Check");
        assertThat(byColour.get(0).get("fabricType")).isEqualTo("Solid Dyed");
    }

    @Test
    void percentagesNeedSomethingToBeAPercentageOf() {
        assertThat(ProductionDashboardService.pct(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("33.3");
        assertThat(ProductionDashboardService.pct(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }

    private static Map<String, Object> line(Long bpoId, String no, String status, String required, String fabricType, String colour,
                                            boolean dyed, String deliverStage, Map<String, String> measures) {
        Map<String, Object> l = new HashMap<>();
        l.put("bpoId", bpoId);
        l.put("bpoNo", no);
        l.put("status", status);
        l.put("documentDate", LocalDate.of(2026, 9, 1));
        l.put("requiredDate", required == null ? null : LocalDate.parse(required));
        l.put("buyer", "IT Buyer");
        l.put("fabricType", fabricType);
        l.put("colorName", colour);
        l.put("needsProcessing", dyed);
        l.put("deliverStage", deliverStage);
        l.put("routeCode", "PIECE_DYED");
        l.put("shortClosed", false);
        l.put("late", false);
        l.put("uom", "Yds");
        measures.forEach((k, v) -> l.put(k, new BigDecimal(v)));
        return l;
    }
}

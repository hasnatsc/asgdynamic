package com.asg.fabricerp.production;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.party.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The production chain against real PostgreSQL: Flyway builds the schema (V27-V29 included),
 * Hibernate validates the entities, and orders run from Booking to the buyer's gate through the
 * real services - draw streams, stock ledger, reservations, progress and revisions.
 *
 * <p>Opt-in, because it writes documents and stock. Point {@code FABRICERP_IT_DB_URL} at a
 * <b>throwaway</b> database, never the one the app uses:
 * <pre>
 *   createdb fabricerp_chain_it
 *   FABRICERP_IT_DB_URL=jdbc:postgresql://localhost:5432/fabricerp_chain_it mvn -Dtest=ProductionChainDatabaseIT test
 * </pre>
 * Approval is signed directly (status + the approval listener), since who may sign is the
 * approval engine's own concern, tested in ApprovalServiceTest.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class ProductionChainDatabaseIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private ChainDocumentService documents;
    @Autowired private ChainPostingService posting;
    @Autowired private ChainApprovalListener approvals;
    @Autowired private ChainViews views;
    @Autowired private DeliveryTypeService deliveryTypes;
    @Autowired private ProductionDashboardService dashboard;
    @Autowired private ProductionBoardService boards;
    @Autowired private FabricStockQueries stockQueries;
    @Autowired private BusinessDocumentRepository repository;
    @Autowired private BusinessUnitRepository units;
    @Autowired private PartyRepository parties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private OrgContext context;

    private long orgId;
    private long unitId;
    private long buyerId;
    private long greigeStore;     // SAF002 Weaving Store: greige only
    private long processingStore; // SAF001 Processing Store: greige and finished
    private static int seq;

    @BeforeEach
    void setUp() {
        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        unitId = jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = 'AF'", Long.class, orgId);
        greigeStore = jdbc.queryForObject("SELECT id FROM org_warehouses WHERE code = 'SAF002'", Long.class);
        processingStore = jdbc.queryForObject("SELECT id FROM org_warehouses WHERE code = 'SAF001'", Long.class);
        jdbc.update("""
            INSERT INTO pty_parties (organization_id, code, name, active, deleted, version, created_by, created_at)
            SELECT ?, 'IT-BUYER', 'IT Buyer Ltd', TRUE, FALSE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM pty_parties WHERE organization_id = ? AND code = 'IT-BUYER')
            """, orgId, orgId);
        buyerId = jdbc.queryForObject("SELECT id FROM pty_parties WHERE organization_id = ? AND code = 'IT-BUYER'", Long.class, orgId);
        jdbc.update("""
            INSERT INTO pty_party_roles (organization_id, party_id, role_type, qualifier, granted_on, is_current, version)
            VALUES (?, ?, 'CUSTOMER', 'MARKETING', CURRENT_DATE, TRUE, 0) ON CONFLICT DO NOTHING
            """, orgId, buyerId);

        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(unitId);
        when(context.requireBusinessUnitId()).thenReturn(unitId);
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.requireBusinessUnitCode()).thenReturn("AF");
        when(context.username()).thenReturn("it");
        when(context.warehouseId()).thenReturn(greigeStore);
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());
        when(context.requireRowScope()).thenReturn(RowScope.unrestrictedScope());
    }

    // ------------------------------------------------------------------------------- scenarios

    @Test
    void aPieceDyedOrderRunsFromBookingToTheBuyersGate() {
        BusinessDocument booking = approvedBooking("Solid Dyed", "Navy", "1000", "Red", "500");

        // Create from booking: one order, route copied, greige worked out, the Booking drawn.
        List<BusinessDocument> orders = documents.createFromBooking(booking.getId());
        assertThat(orders).hasSize(1);
        BusinessDocument bpo = load(orders.get(0).getId());
        BusinessDocumentLineGroup g = bpo.getLineGroups().get(0);
        assertThat(g.getRoute().getRouteCode()).isEqualTo(RouteCode.PIECE_DYED);
        assertThat(g.getRoute().getGreigeKey()).isEqualTo(GreigeKey.CONSTRUCTION);
        assertThat(g.getRoute().greigeFor(g.groupQuantity())).isEqualByComparingTo("1650");
        assertThat(fulfilled(booking)).containsExactly(bd("1000"), bd("500"));
        approve(bpo);

        // Weaving is per fabric line: one greige for both colours, capped at 1,500 + 10 %.
        Long bpoGroup = g.getId();
        assertThatThrownBy(() -> documents.save(ChainStep.WWO, request(null, line("GROUP", bpoGroup, "1700"))))
            .hasMessageContaining("1650 allowed");
        BusinessDocument wwo = documents.save(ChainStep.WWO, request(null, line("GROUP", bpoGroup, "1650")));
        approve(wwo);
        assertThat(load(bpo.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PROCESSING);

        // Greige receive into a greige store, posted.
        BusinessDocument gr = documents.save(ChainStep.GR, store(request(null,
            line("COLOUR", lineOf(wwo, 0), "1600", 40)), greigeStore));
        posting.post(ChainStep.GR, gr.getId());
        long greigeLot = jdbc.queryForObject("SELECT fabric_lot_id FROM gbl_business_document_color_lines WHERE id = ?",
            Long.class, lineOf(load(gr.getId()), 0));
        assertThat(balance(greigeStore, greigeLot)).isEqualByComparingTo("1600");

        // Dyeing and the delivery schedule draw their own streams of the same order lines.
        BusinessDocument pwo = documents.save(ChainStep.PWO, request(null,
            line("COLOUR", lineOf(bpo, 0), "1000"), line("COLOUR", lineOf(bpo, 1), "500")));
        approve(pwo);
        BusinessDocument rpi = documents.save(ChainStep.RPI, request(null,
            line("COLOUR", lineOf(bpo, 0), "1000"), line("COLOUR", lineOf(bpo, 1), "500")));
        approve(rpi);

        // Greige issue: out of the greige store to the batch.
        BusinessDocument gi = documents.save(ChainStep.GI, store(request(null,
            lotLine(lineOf(pwo, 0), "1100", greigeLot), lotLine(lineOf(pwo, 1), "500", greigeLot)), greigeStore));
        posting.post(ChainStep.GI, gi.getId());
        assertThat(balance(greigeStore, greigeLot)).isEqualByComparingTo("0");
        // The greige receive can no longer be cancelled: its greige has gone to dyeing.
        assertThatThrownBy(() -> posting.cancel(ChainStep.GR, gr.getId(), "keyed twice"))
            .hasMessageContaining("cannot be cancelled");

        // Finished receive by dye lot, shade and grade - capped at what was issued.
        BusinessDocument ffr = documents.save(ChainStep.FFR, store(request(null,
            finished(lineOf(pwo, 0), "980", "D1", "S1", "A"), finished(lineOf(pwo, 1), "490", "D2", "S1", "A")), processingStore));
        posting.post(ChainStep.FFR, ffr.getId());
        BusinessDocument ffrB = documents.save(ChainStep.FFR, store(request(null,
            finished(lineOf(pwo, 0), "60", "D1", "S1", "B")), processingStore));
        posting.post(ChainStep.FFR, ffrB.getId());
        assertThatThrownBy(() -> documents.save(ChainStep.FFR, store(request(null,
            finished(lineOf(pwo, 0), "100", "D1", "S1", "A")), processingStore)))
            .hasMessageContaining("only 60 is left");

        // Delivery order: picks the A lots; approval reserves them.
        Map<Long, Long> finishedLots = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            long bpoLine = lineOf(bpo, i);
            finishedLots.put(bpoLine, jdbc.queryForObject(
                "SELECT id FROM inv_fabric_lots WHERE stage = 'FINISHED' AND color_line_id = ? AND grade = 'A'", Long.class, bpoLine));
        }
        BusinessDocument order = documents.save(ChainStep.DO, store(request(null,
            lotLine(lineOf(rpi, 0), "980", finishedLots.get(lineOf(bpo, 0))),
            lotLine(lineOf(rpi, 1), "490", finishedLots.get(lineOf(bpo, 1)))), processingStore));
        approve(order);
        assertThat(reserved(processingStore, finishedLots.get(lineOf(bpo, 0)))).isEqualByComparingTo("980");

        // A second delivery order cannot promise the same metres.
        BusinessDocument greedy = documents.save(ChainStep.DO, store(request(null,
            lotLine(lineOf(rpi, 0), "20", finishedLots.get(lineOf(bpo, 0)))), processingStore));
        assertThatThrownBy(() -> approve(greedy)).hasMessageContaining("free in the store");

        // Fabrics delivery: out of stock against the reservation; everything moves forward.
        BusinessDocument fd = documents.save(ChainStep.FD, request(null,
            line("COLOUR", lineOf(order, 0), "980", 25), line("COLOUR", lineOf(order, 1), "490", 12)));
        posting.post(ChainStep.FD, fd.getId());
        assertThat(balance(processingStore, finishedLots.get(lineOf(bpo, 0)))).isEqualByComparingTo("0");
        assertThat(reserved(processingStore, finishedLots.get(lineOf(bpo, 0)))).isEqualByComparingTo("0");
        assertThat(load(order.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        assertThat(load(rpi.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PARTIAL);
        assertThat(load(bpo.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        assertThat(load(booking.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);

        // The ledger agrees with the balances, lot by lot.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM inv_fabric_balances b
            WHERE b.quantity <> (SELECT COALESCE(SUM(m.quantity), 0) FROM inv_fabric_moves m
                                 WHERE m.lot_id = b.lot_id AND m.warehouse_id = b.warehouse_id)
            """, Integer.class)).isZero();

        // Cancelling the delivery reverses it exactly and puts the fabric back on hold for its order.
        posting.cancel(ChainStep.FD, fd.getId(), "Wrong vehicle");
        assertThat(balance(processingStore, finishedLots.get(lineOf(bpo, 0)))).isEqualByComparingTo("980");
        assertThat(reserved(processingStore, finishedLots.get(lineOf(bpo, 0)))).isEqualByComparingTo("980");
        assertThat(load(order.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        assertThat(load(bpo.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PROCESSING);
        assertThat(load(booking.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);

        // The screens read it: detail, both boards, stock.
        Map<String, Object> detail = views.detail(ChainStep.BPO, bpo.getId());
        assertThat(detail.get("children")).asList().isNotEmpty();
        assertThat(boards.productionBoard(new ProductionBoardService.BoardFilter(null, buyerId, null, null, null, true), 0, 50).rows())
            .isNotEmpty();
        assertThat(boards.readyToDeliver(buyerId, null, false, 0, 50).rows()).isNotEmpty();
        assertThat(stockQueries.balances(null, null, null, false, 0, 50).get("rows")).asList().isNotEmpty();
    }

    @Test
    void aProductionOrderShowsItsBooking_keepsItsRequirements_andTakesTheBookingsGarmentsOrTheOneEntered() {
        long garmentsA = garmentFactory("IT-GMT-A", "IT Garments A");
        long garmentsB = garmentFactory("IT-GMT-B", "IT Garments B");
        var ticked = new ChainDocumentRequest.Requirements(true, false, true, false, true, false, true);

        // A booking naming no garments: the order takes the one entered, and its requirements.
        BusinessDocument open = approvedBooking("Solid Dyed", "Navy", "1000");
        BusinessDocument bpo = documents.save(ChainStep.BPO, bpoRequest(null, garmentsA, "Plot 7, Gazipur", ticked,
            line("COLOUR", lineOf(open, 0), "1000")));
        BusinessDocument saved = load(bpo.getId());
        assertThat(saved.getGarments().getId()).isEqualTo(garmentsA);
        assertThat(saved.getGarmentsAddress()).isEqualTo("Plot 7, Gazipur");
        assertThat(saved.isInHouseTestReport()).isTrue();
        assertThat(saved.isInspectionReport()).isFalse();
        assertThat(saved.isDyeLotRequired()).isTrue();
        assertThat(saved.isBlanket()).isTrue();
        assertThat(saved.isPackingList()).isTrue();
        assertThat(saved.isHeadCutting()).isFalse();

        // Its detail carries the booking's master data and the ticks.
        Map<String, Object> detail = views.detail(ChainStep.BPO, bpo.getId());
        @SuppressWarnings("unchecked") Map<String, Object> booking = (Map<String, Object>) detail.get("booking");
        assertThat(booking.get("documentNo")).isEqualTo(open.getDocumentNo());
        assertThat(booking.get("buyer")).isEqualTo("IT Buyer Ltd");
        assertThat(booking).containsKeys("bookingType", "orderType", "brand", "marketingPerson", "currency", "priceInMeter");
        @SuppressWarnings("unchecked") Map<String, Object> req = (Map<String, Object>) detail.get("requirements");
        assertThat(req).containsEntry("inHouseTestReport", true).containsEntry("testFabrics", false);

        // Editing without requirements leaves them; clearing the garments is allowed when the booking has none.
        documents.save(ChainStep.BPO, bpoRequest(bpo.getId(), null, null, null, line("COLOUR", lineOf(open, 0), "1000")));
        saved = load(bpo.getId());
        assertThat(saved.getGarments()).isNull();
        assertThat(saved.isInHouseTestReport()).isTrue();

        // A booking naming its garments decides it: another one sent is ignored.
        BusinessDocument named = approvedBooking("Solid Dyed", "Red", "500");
        jdbc.update("UPDATE gbl_business_documents SET garments_id = ?, garments_address = 'Booking address' WHERE id = ?", garmentsA, named.getId());
        BusinessDocument other = documents.save(ChainStep.BPO, bpoRequest(null, garmentsB, null, null,
            line("COLOUR", lineOf(named, 0), "500")));
        saved = load(other.getId());
        assertThat(saved.getGarments().getId()).isEqualTo(garmentsA);
        assertThat(saved.getGarmentsAddress()).isEqualTo("Booking address");
        assertThat(views.bookingMaster(named.getId())).containsEntry("garmentsId", garmentsA);
    }

    @Test
    void aProductionOrderKeepsItsPreDeliverySchedule_throughEditsAndRevision() {
        long pps = jdbc.queryForObject("SELECT id FROM fab_delivery_types WHERE organization_id = ? AND code = 'PPS'", Long.class, orgId);
        long full = jdbc.queryForObject("SELECT id FROM fab_delivery_types WHERE organization_id = ? AND code = 'FULL'", Long.class, orgId);
        BusinessDocument booking = approvedBooking("Solid Dyed", "CAMO AOP", "18650");
        long colour = lineOf(booking, 0);
        List<ChainDocumentRequest.PreDelivery> plan = List.of(
            new ChainDocumentRequest.PreDelivery(pps, LocalDate.of(2025, 11, 25), colour, new BigDecimal("20"), 2),
            new ChainDocumentRequest.PreDelivery(full, LocalDate.of(2025, 11, 30), colour, new BigDecimal("18630"), 1));

        BusinessDocument bpo = documents.save(ChainStep.BPO, scheduled(null, plan, line("COLOUR", colour, "18650")));
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows =
            (List<Map<String, Object>>) views.detail(ChainStep.BPO, bpo.getId()).get("preDeliveries");
        assertThat(rows).extracting(r -> r.get("deliveryTypeName")).containsExactly("PP Submission", "Full Delivery");
        assertThat(rows).extracting(r -> r.get("colorName")).containsOnly("CAMO AOP");
        assertThat(rows).extracting(r -> r.get("serialNo")).containsExactly(2, 1);
        assertThat((BigDecimal) rows.get(1).get("quantity")).isEqualByComparingTo("18630");

        // Editing the lines without the schedule leaves it; a colour from another booking is refused.
        documents.save(ChainStep.BPO, request(bpo.getId(), line("COLOUR", colour, "18650")));
        assertThat(count(bpo)).isEqualTo(2);
        long foreign = lineOf(approvedBooking("Solid Dyed", "Navy", "100"), 0);
        assertThatThrownBy(() -> documents.save(ChainStep.BPO, scheduled(bpo.getId(),
                List.of(new ChainDocumentRequest.PreDelivery(pps, LocalDate.of(2025, 11, 25), foreign, BigDecimal.TEN, 1)),
                line("COLOUR", colour, "18650"))))
            .hasMessageContaining("Pre-delivery row 1: choose one of the order's colours");

        // A revision starts with the schedule.
        approve(bpo);
        BusinessDocument revision = documents.revise(ChainStep.BPO, bpo.getId(), "IT revision");
        assertThat(count(revision)).isEqualTo(2);
        // The draft revision does not count until approved: the order is counted once, as its original.
        List<Map<String, Object>> current = dashboard.linesForBookings(List.of(booking.getId()));
        assertThat(current).extracting(l -> l.get("bpoId")).containsOnly(bpo.getId());
        assertThat(ProductionDashboardService.sum(current).get("quantity")).isEqualByComparingTo("18650");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theDashboardReadsTheChainsOwnFigures_andDrillsIntoItsDocuments() {
        BusinessDocument booking = approvedBooking("Solid Dyed", "Navy", "1000", "Red", "500");
        BusinessDocument bpo = load(documents.createFromBooking(booking.getId()).get(0).getId());
        approve(bpo);
        BusinessDocument wwo = documents.save(ChainStep.WWO, request(null, line("GROUP", bpo.getLineGroups().get(0).getId(), "1650")));
        approve(wwo);
        BusinessDocument gr = documents.save(ChainStep.GR, store(request(null, line("COLOUR", lineOf(wwo, 0), "1600", 40)), greigeStore));
        posting.post(ChainStep.GR, gr.getId());

        var one = new ProductionDashboardService.Filter(null, null, null, null, null, bpo.getId(), null);
        Map<String, Object> d = dashboard.dashboard(one, null);

        List<Map<String, Object>> orders = (List<Map<String, Object>>) d.get("orders");
        assertThat(orders).hasSize(1);
        Map<String, Object> order = orders.get(0);
        assertThat((BigDecimal) order.get("quantity")).isEqualByComparingTo("1500");
        assertThat((BigDecimal) order.get("greigeReceived")).isEqualByComparingTo("1600");
        assertThat(order.get("stage")).isEqualTo("Greige in store");
        assertThat(order.get("colours")).isEqualTo(List.of("Navy", "Red"));

        Map<String, Object> kpis = (Map<String, Object>) d.get("kpis");
        assertThat(((Map<String, Object>) kpis.get("bookings")).get("count")).isEqualTo(1L);
        assertThat(((Map<String, Object>) kpis.get("weaving")).get("documents")).isEqualTo(1L);
        assertThat((BigDecimal) ((Map<String, Object>) kpis.get("greigeStock")).get("quantity")).isEqualByComparingTo("1600");

        Map<String, Object> grNode = ((List<Map<String, Object>>) d.get("pipeline")).stream()
            .filter(n -> "GR".equals(n.get("key"))).findFirst().orElseThrow();
        assertThat(grNode.get("documents")).isEqualTo(1L);
        assertThat((BigDecimal) grNode.get("quantity")).isEqualByComparingTo("1600");

        List<Map<String, Object>> byType = (List<Map<String, Object>>) d.get("byFabricType");
        assertThat(byType).singleElement().satisfies(r -> {
            assertThat(r.get("key")).isEqualTo("Solid Dyed");
            assertThat((BigDecimal) r.get("quantity")).isEqualByComparingTo("1500");
        });
        assertThat((List<Map<String, Object>>) d.get("workOrders")).extracting(w -> w.get("id")).contains(wwo.getId());

        // Every figure opens its documents.
        assertThat(dashboard.documents(one, ChainStep.GR, "")).singleElement()
            .satisfies(doc -> assertThat(doc.get("documentNo")).isEqualTo(load(gr.getId()).getDocumentNo()));
        assertThat(dashboard.bookingDocuments(one)).extracting(b -> b.get("id")).containsExactly(booking.getId());

        // Booking analytics follows a booking to its orders' lines through the same query.
        List<Map<String, Object>> bookingLines = dashboard.linesForBookings(List.of(booking.getId()));
        assertThat(bookingLines).hasSize(2).allSatisfy(l -> assertThat(l.get("bookingId")).isEqualTo(booking.getId()));
        assertThat(ProductionDashboardService.sum(bookingLines).get("greigeReceived")).isEqualByComparingTo("1600");
        assertThat(dashboard.linesForBookings(List.of())).isEmpty();

        // Past its required date with a balance: overdue, and an alert that opens the order.
        jdbc.update("UPDATE gbl_business_documents SET required_date = CURRENT_DATE - 5 WHERE id = ?", bpo.getId());
        Map<String, Object> late = dashboard.dashboard(one, null);
        assertThat(((List<Map<String, Object>>) late.get("orders")).get(0).get("overdue")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) late.get("alerts")).anySatisfy(a -> {
            assertThat(a.get("kind")).isEqualTo("OVERDUE");
            assertThat(a.get("severity")).isEqualTo("critical");
            assertThat(a.get("documentId")).isEqualTo(bpo.getId());
            assertThat(a.get("slug")).isEqualTo("bpo");
            assertThat((String) a.get("detail")).contains("5 day(s) late");
        });
        assertThat(((Map<String, Object>) ((Map<String, Object>) late.get("kpis")).get("overdue")).get("orders")).isEqualTo(1L);

        // A colour filter narrows the order to its lines.
        Map<String, Object> red = dashboard.dashboard(new ProductionDashboardService.Filter(null, null, null, "red", null, bpo.getId(), null), null);
        assertThat((BigDecimal) ((List<Map<String, Object>>) red.get("orders")).get(0).get("quantity")).isEqualByComparingTo("500");
    }

    @Test
    void deliveryTypesHaveAUniqueCode_andOneInUseIsRetiredNotDeleted() {
        String code = "IT" + (System.currentTimeMillis() % 1_000_000_000L);
        DeliveryType type = deliveryTypes.save(null, new DeliveryTypeService.Request(code.toLowerCase(), "IT Shipment Sample", 9, true));
        assertThat(type.getCode()).isEqualTo(code);
        assertThatThrownBy(() -> deliveryTypes.save(null, new DeliveryTypeService.Request(code, "Again", 1, true)))
            .hasMessageContaining("already used");
        assertThatThrownBy(() -> deliveryTypes.save(null, new DeliveryTypeService.Request("bad code!", "X", 1, true)))
            .hasMessageContaining("up to 20 letters");

        BusinessDocument booking = approvedBooking("Solid Dyed", "Olive", "300");
        long colour = lineOf(booking, 0);
        documents.save(ChainStep.BPO, scheduled(null, List.of(new ChainDocumentRequest.PreDelivery(type.getId(),
            LocalDate.of(2025, 12, 1), colour, new BigDecimal("300"), 1)), line("COLOUR", colour, "300")));
        assertThat(deliveryTypes.delete(type.getId())).isEqualTo("retired");

        DeliveryType unused = deliveryTypes.save(null, new DeliveryTypeService.Request(code + "X", "IT Unused", 10, true));
        assertThat(deliveryTypes.delete(unused.getId())).isEqualTo("deleted");
    }

    private int count(BusinessDocument doc) {
        return jdbc.queryForObject("SELECT count(*) FROM fab_bpo_pre_deliveries WHERE document_id = ?", Integer.class, doc.getId());
    }

    private static ChainDocumentRequest scheduled(Long id, List<ChainDocumentRequest.PreDelivery> plan, ChainDocumentRequest.Line... lines) {
        return new ChainDocumentRequest(id, null, null, null, null, null, null, null, null, null, null, null,
            List.of(lines), List.of(), null, plan);
    }

    @Test
    void weavingDyeingAndSchedulingEachSeeTheirOwnBalanceOfOneLine() {
        BusinessDocument booking = approvedBooking("Yarn Dyed", "Check", "1000");
        BusinessDocument bpo = load(documents.createFromBooking(booking.getId()).get(0).getId());
        approve(bpo);
        long line = lineOf(bpo, 0);
        // Yarn-dyed is woven per colour, 8 % over; all three take their full share of the same line.
        documents.save(ChainStep.WWO, request(null, line("COLOUR", line, "1080")));
        documents.save(ChainStep.PWO, request(null, line("COLOUR", line, "1000")));
        documents.save(ChainStep.RPI, request(null, line("COLOUR", line, "1030")));
        assertThatThrownBy(() -> documents.save(ChainStep.PWO, request(null, line("COLOUR", line, "1"))))
            .hasMessageContaining("only 0 is left");
        // ...and a rework draws apart from the order's own dyeing.
        documents.save(ChainStep.PWO, new ChainDocumentRequest(null, null, null, null, null, ProcessKind.REWORK, null, null, null,
            null, null, null, List.of(line("COLOUR", line, "50")), List.of()));
    }

    @Test
    void aShortClosedWorkOrderLineGivesItsBalanceBackForATopUp() {
        BusinessDocument booking = approvedBooking("Greige Yarn Dyed", "Stripe", "1000");
        BusinessDocument bpo = load(documents.createFromBooking(booking.getId()).get(0).getId());
        approve(bpo);
        long line = lineOf(bpo, 0);
        BusinessDocument wwo = documents.save(ChainStep.WWO, request(null, line("COLOUR", line, "1020")));
        approve(wwo);
        BusinessDocument gr = documents.save(ChainStep.GR, store(request(null, line("COLOUR", lineOf(wwo, 0), "600")), greigeStore));
        posting.post(ChainStep.GR, gr.getId());
        assertThat(load(wwo.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PARTIAL);

        posting.shortClose(ChainStep.WWO, lineOf(wwo, 0), "Loom breakdown, re-planned");
        assertThat(load(wwo.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        // 420 of the line's greige is free again for a top-up weaving order.
        documents.save(ChainStep.WWO, request(null, line("COLOUR", line, "420")));
        // A received work order cannot be cancelled - only short-closed.
        assertThatThrownBy(() -> posting.cancel(ChainStep.WWO, wwo.getId(), "no")).hasMessageContaining("completed");
    }

    @Test
    void anApprovedRevisionTakesOverWhatWasRaisedAgainstItsPredecessor() {
        BusinessDocument booking = approvedBooking("Greige Indigo Denim", "Indigo", "1000");
        BusinessDocument bpo = load(documents.createFromBooking(booking.getId()).get(0).getId());
        approve(bpo);
        long oldLine = lineOf(bpo, 0);
        BusinessDocument wwo = documents.save(ChainStep.WWO, request(null, line("COLOUR", oldLine, "1000")));
        approve(wwo);

        // A revision that cuts the line below what weaving has drawn is refused at approval.
        BusinessDocument cut = documents.revise(ChainStep.BPO, bpo.getId(), "Buyer cut the order");
        tx.executeWithoutResult(s -> {
            BusinessDocument r = load(cut.getId());
            r.getLineGroups().get(0).getColorLines().get(0).setQuantity(new BigDecimal("900"));
            r.recalculateTotals();
            repository.save(r);
        });
        assertThatThrownBy(() -> approve(cut)).hasMessageContaining("below the 1000 already on weaving work orders");

        // Raising it is fine: the revision takes over, the weaving order follows, the old version is superseded.
        tx.executeWithoutResult(s -> {
            BusinessDocument r = load(cut.getId());
            r.getLineGroups().get(0).getColorLines().get(0).setQuantity(new BigDecimal("1000"));
            r.recalculateTotals();
            repository.save(r);
        });
        approve(cut);
        long newLine = lineOf(load(cut.getId()), 0);
        assertThat(jdbc.queryForObject("SELECT source_color_line_id FROM gbl_business_document_color_lines WHERE id = ?",
            Long.class, lineOf(wwo, 0))).isEqualTo(newLine);
        assertThat(load(bpo.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.CANCELLED);
        assertThat(load(cut.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PROCESSING);
        assertThat(jdbc.queryForObject("""
            SELECT drawn_quantity FROM gbl_line_draws WHERE source_kind = 'COLOUR' AND source_id = ? AND stream = 'WEAVING_WORK_ORDER'
            """, BigDecimal.class, newLine)).isEqualByComparingTo("1000");
        // The Booking is drawn once, by the revision.
        assertThat(fulfilled(booking)).containsExactly(bd("1000"));
        // ...and the boards count the order once, as its revision.
        assertThat(dashboard.linesForBookings(List.of(booking.getId()))).extracting(l -> l.get("bpoId")).containsOnly(cut.getId());
    }

    @Test
    void closingADyeingBatchMeasuresItsLoss() {
        BusinessDocument booking = approvedBooking("Solid Dyed Print", "Floral", "500");
        BusinessDocument bpo = load(documents.createFromBooking(booking.getId()).get(0).getId());
        approve(bpo);
        BusinessDocument wwo = documents.save(ChainStep.WWO, request(null, line("GROUP", bpo.getLineGroups().get(0).getId(), "550")));
        approve(wwo);
        BusinessDocument gr = documents.save(ChainStep.GR, store(request(null, line("COLOUR", lineOf(wwo, 0), "550")), processingStore));
        posting.post(ChainStep.GR, gr.getId());
        long lot = jdbc.queryForObject("SELECT fabric_lot_id FROM gbl_business_document_color_lines WHERE id = ?", Long.class,
            lineOf(load(gr.getId()), 0));
        BusinessDocument pwo = documents.save(ChainStep.PWO, request(null, line("COLOUR", lineOf(bpo, 0), "500")));
        approve(pwo);
        assertThat(load(pwo.getId()).getProcessKind()).isEqualTo(ProcessKind.PRINT);
        BusinessDocument gi = documents.save(ChainStep.GI, store(request(null, lotLine(lineOf(pwo, 0), "550", lot)), processingStore));
        posting.post(ChainStep.GI, gi.getId());
        BusinessDocument ffr = documents.save(ChainStep.FFR, store(request(null, finished(lineOf(pwo, 0), "470", "P1", "S1", "A")), processingStore));
        posting.post(ChainStep.FFR, ffr.getId());

        posting.closeBatch(pwo.getId(), null);
        BusinessDocument closed = load(pwo.getId());
        assertThat(closed.isBatchClosed()).isTrue();
        assertThat(closed.getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT remarks FROM apr_document_history WHERE document_id = ? AND action = 'CLOSED'",
            String.class, pwo.getId())).contains("80 process loss");
        // 30 finished short: the order's dyeing stream has room for a top-up again.
        documents.save(ChainStep.PWO, request(null, line("COLOUR", lineOf(bpo, 0), "30")));
    }

    @Test
    void aStoreDocumentStartsInTheUsersOwnStore_whenThatStoreCanTakeIt() {
        BusinessDocument booking = approvedBooking("Greige Yarn Dyed", "Plaid", "300");
        BusinessDocument bpo = load(documents.createFromBooking(booking.getId()).get(0).getId());
        approve(bpo);
        BusinessDocument wwo = documents.save(ChainStep.WWO, request(null, line("COLOUR", lineOf(bpo, 0), "300")));
        approve(wwo);
        // No store chosen: the user's own (greige) store takes the greige, and it posts.
        BusinessDocument gr = documents.save(ChainStep.GR, request(null, line("COLOUR", lineOf(wwo, 0), "250")));
        assertThat(jdbc.queryForObject("SELECT warehouse_id FROM gbl_business_documents WHERE id = ?", Long.class, gr.getId()))
            .isEqualTo(greigeStore);
        posting.post(ChainStep.GR, gr.getId());
        // A draft saved before the default existed is posted into it as well.
        BusinessDocument gr2 = documents.save(ChainStep.GR, store(request(null, line("COLOUR", lineOf(wwo, 0), "5")), greigeStore));
        jdbc.update("UPDATE gbl_business_documents SET warehouse_id = NULL WHERE id = ?", gr2.getId());
        posting.post(ChainStep.GR, gr2.getId());
        assertThat(jdbc.queryForObject("SELECT warehouse_id FROM gbl_business_documents WHERE id = ?", Long.class, gr2.getId()))
            .isEqualTo(greigeStore);
    }

    // --------------------------------------------------------------------------------- helpers

    private BusinessDocument approvedBooking(String fabricType, String... colourQty) {
        return tx.execute(s -> {
            BusinessDocument b = new BusinessDocument();
            b.setOrganizationId(orgId);
            b.setDocumentType(DocumentType.BOOKING);
            b.setBusinessUnit(units.getReferenceById(unitId));
            b.setDocumentNo("IT-BK-" + System.nanoTime() + "-" + (++seq));
            b.setDocumentDate(LocalDate.now());
            b.setRequiredDate(LocalDate.now().plusDays(30));
            b.setParty(parties.getReferenceById(buyerId));
            b.setCurrencyCode("USD");
            BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
            g.getFabric().setFabricType(fabricType);
            g.getFabric().setConstruction("40x40/133x72");
            for (int i = 0; i < colourQty.length; i += 2) {
                BusinessDocumentColorLine l = new BusinessDocumentColorLine();
                l.setColorName(colourQty[i]);
                l.setQuantity(new BigDecimal(colourQty[i + 1]));
                l.setRate(new BigDecimal("2.5"));
                g.addColorLine(l);
            }
            b.addLineGroup(g);
            g.setOrganizationId(orgId);
            g.getColorLines().forEach(l -> l.setOrganizationId(orgId));
            b.recalculateTotals();
            b.transitionTo(BusinessDocumentStatus.SUBMITTED);
            b.transitionTo(BusinessDocumentStatus.APPROVED);
            return repository.save(b);
        });
    }

    /** Signs a document's last level: what ApprovalService.decide does once the matrix is satisfied. */
    private void approve(BusinessDocument doc) {
        tx.executeWithoutResult(s -> {
            BusinessDocument d = load(doc.getId());
            d.transitionTo(BusinessDocumentStatus.SUBMITTED);
            d.transitionTo(BusinessDocumentStatus.APPROVED);
            approvals.onApproved(d);
            repository.save(d);
        });
    }

    private BusinessDocument load(Long id) {
        return tx.execute(s -> {
            BusinessDocument d = repository.findScopedWithLines(id, orgId).orElseThrow();
            d.getLineGroups().forEach(g -> g.getColorLines().size());
            return d;
        });
    }

    private long lineOf(BusinessDocument doc, int index) {
        return jdbc.queryForObject("""
            SELECT l.id FROM gbl_business_document_color_lines l
            JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
            WHERE g.document_id = ? ORDER BY g.group_no, l.color_line_no OFFSET ? LIMIT 1
            """, Long.class, doc.getId(), index);
    }

    private List<BigDecimal> fulfilled(BusinessDocument doc) {
        return jdbc.queryForList("""
            SELECT l.fulfilled_quantity FROM gbl_business_document_color_lines l
            JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
            WHERE g.document_id = ? ORDER BY g.group_no, l.color_line_no
            """, BigDecimal.class, doc.getId()).stream().map(BigDecimal::stripTrailingZeros).toList();
    }

    private BigDecimal balance(long store, long lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM inv_fabric_balances WHERE warehouse_id = ? AND lot_id = ?",
            BigDecimal.class, store, lot);
    }

    private BigDecimal reserved(long store, long lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(reserved_quantity), 0) FROM inv_fabric_balances WHERE warehouse_id = ? AND lot_id = ?",
            BigDecimal.class, store, lot);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).stripTrailingZeros();
    }

    private static ChainDocumentRequest.Line line(String kind, Long id, String qty) {
        return line(kind, id, qty, null);
    }

    private static ChainDocumentRequest.Line line(String kind, Long id, String qty, Integer rolls) {
        return new ChainDocumentRequest.Line(kind, id, new BigDecimal(qty), null, rolls, null, null, null, null, null, null);
    }

    private static ChainDocumentRequest.Line lotLine(Long id, String qty, Long lot) {
        return new ChainDocumentRequest.Line("COLOUR", id, new BigDecimal(qty), lot, null, null, null, null, null, null, null);
    }

    private static ChainDocumentRequest.Line finished(Long id, String qty, String dyeLot, String shade, String grade) {
        return new ChainDocumentRequest.Line("COLOUR", id, new BigDecimal(qty), null, 10, dyeLot, shade, grade, null, null, null);
    }

    private static ChainDocumentRequest request(Long id, ChainDocumentRequest.Line... lines) {
        return new ChainDocumentRequest(id, null, null, null, null, null, null, null, null, null, null, null, List.of(lines), List.of());
    }

    private static ChainDocumentRequest bpoRequest(Long id, Long garmentsId, String address,
                                                   ChainDocumentRequest.Requirements requirements, ChainDocumentRequest.Line... lines) {
        return new ChainDocumentRequest(id, null, null, null, null, null, null, garmentsId, address, null, null, null,
            List.of(lines), List.of(), requirements, null);
    }

    private long garmentFactory(String code, String name) {
        jdbc.update("""
            INSERT INTO pty_parties (organization_id, code, name, active, deleted, version, created_by, created_at)
            SELECT ?, ?, ?, TRUE, FALSE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM pty_parties WHERE organization_id = ? AND code = ?)
            """, orgId, code, name, orgId, code);
        long id = jdbc.queryForObject("SELECT id FROM pty_parties WHERE organization_id = ? AND code = ?", Long.class, orgId, code);
        jdbc.update("""
            INSERT INTO pty_party_roles (organization_id, party_id, role_type, qualifier, granted_on, is_current, version)
            VALUES (?, ?, 'GARMENT_FACTORY', NULL, CURRENT_DATE, TRUE, 0) ON CONFLICT DO NOTHING
            """, orgId, id);
        return id;
    }

    private static ChainDocumentRequest store(ChainDocumentRequest r, long warehouseId) {
        return new ChainDocumentRequest(r.id(), r.documentDate(), r.requiredDate(), warehouseId, r.vendorId(), r.processKind(),
            r.referenceNo(), r.garmentsId(), r.garmentsAddress(), r.vehicleNo(), r.driverName(), r.remarks(), r.lines(), r.groups());
    }
}

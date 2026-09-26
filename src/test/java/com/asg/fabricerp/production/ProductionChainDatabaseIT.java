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

    private static ChainDocumentRequest store(ChainDocumentRequest r, long warehouseId) {
        return new ChainDocumentRequest(r.id(), r.documentDate(), r.requiredDate(), warehouseId, r.vendorId(), r.processKind(),
            r.referenceNo(), r.garmentsId(), r.garmentsAddress(), r.vehicleNo(), r.driverName(), r.remarks(), r.lines(), r.groups());
    }
}

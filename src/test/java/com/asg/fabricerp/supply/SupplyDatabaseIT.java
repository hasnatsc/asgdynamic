package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.production.*;
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
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Purchase and stores against real PostgreSQL: Flyway builds the schema (V31 included), Hibernate
 * validates the entities, and documents run through the real services - requisition to return,
 * weighted-average costing, transfers, adjustments, closed months, fabric lot transfers and the
 * goods-received ledger entry.
 *
 * <p>Opt-in, because it writes documents and stock. Point {@code FABRICERP_IT_DB_URL} at a
 * <b>throwaway</b> database, never the one the app uses:
 * <pre>
 *   createdb fabricerp_supply_it
 *   FABRICERP_IT_DB_URL=jdbc:postgresql://localhost:5432/fabricerp_supply_it mvn -Dtest=SupplyDatabaseIT test
 * </pre>
 * Approval is signed directly (status + the approval listener), since who may sign is the
 * approval engine's own concern, tested in ApprovalServiceTest. Every test makes its own items, so
 * balances never leak between tests or runs.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class SupplyDatabaseIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private SupplyDocumentService documents;
    @Autowired private SupplyPostingService posting;
    @Autowired private SupplyApprovalListener approvals;
    @Autowired private SupplyViews views;
    @Autowired private ItemStockQueries stockQueries;
    @Autowired private InventoryPeriodService periods;
    @Autowired private ChainDocumentService chainDocuments;
    @Autowired private ChainPostingService chainPosting;
    @Autowired private ChainApprovalListener chainApprovals;
    @Autowired private BusinessDocumentRepository repository;
    @Autowired private BusinessUnitRepository units;
    @Autowired private PartyRepository parties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private OrgContext context;

    private long orgId;
    private long unitId;
    private long supplierId;
    private long buyerId;
    private long processing;   // SAF001: greige and finished
    private long weaving;      // SAF002: greige only
    private long itemA;
    private long itemB;
    private static int seq;

    @BeforeEach
    void setUp() {
        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        unitId = jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = 'AF'", Long.class, orgId);
        processing = jdbc.queryForObject("SELECT id FROM org_warehouses WHERE code = 'SAF001'", Long.class);
        weaving = jdbc.queryForObject("SELECT id FROM org_warehouses WHERE code = 'SAF002'", Long.class);
        supplierId = party("IT-SUPPLIER", "IT Chemicals Ltd", "SUPPLIER", null);
        buyerId = party("IT-BUYER", "IT Buyer Ltd", "CUSTOMER", "MARKETING");
        itemA = item("Sodium sulphate");
        itemB = item("Soda ash");

        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(unitId);
        when(context.requireBusinessUnitId()).thenReturn(unitId);
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.requireBusinessUnitCode()).thenReturn("AF");
        when(context.username()).thenReturn("it");
        when(context.warehouseId()).thenReturn(processing);
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());
        when(context.requireRowScope()).thenReturn(RowScope.unrestrictedScope());
    }

    // ------------------------------------------------------------------------------- scenarios

    @Test
    void aRequisitionIsBoughtReceivedReturnedAndIssued() {
        BusinessDocument sr = documents.save(SupplyStep.SR, req().store(processing).item(itemA, "100").item(itemB, "40").build());
        approve(sr);

        // The purchase requisition takes the store requisition's lines, never more than was asked for.
        assertThatThrownBy(() -> documents.save(SupplyStep.SPR, req().from(lineOf(sr, 0), "101").build()))
            .hasMessageContaining("100 allowed");
        BusinessDocument spr = documents.save(SupplyStep.SPR, req().from(lineOf(sr, 0), "100").from(lineOf(sr, 1), "40").build());
        approve(spr);

        // The order prices them; it cannot order more than was requisitioned.
        assertThatThrownBy(() -> documents.save(SupplyStep.PO, req().supplier(supplierId).from(lineOf(spr, 0), "120", "50").build()))
            .hasMessageContaining("100 allowed");
        BusinessDocument po = documents.save(SupplyStep.PO, req().supplier(supplierId)
            .from(lineOf(spr, 0), "100", "50").from(lineOf(spr, 1), "40", "20").build());
        assertThat(po.getSubtotalAmount()).isEqualByComparingTo("5800");
        approve(po);
        assertThat(load(spr.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);

        // Received in two MRRs, at the order's price; the order is part-received, then complete.
        BusinessDocument mrr1 = documents.save(SupplyStep.MRR, req().store(processing).from(lineOf(po, 0), "60").from(lineOf(po, 1), "40").build());
        assertThat(load(mrr1.getId()).getParty().getId()).isEqualTo(supplierId);
        posting.post(SupplyStep.MRR, mrr1.getId());
        assertThat(qty(processing, itemA)).isEqualByComparingTo("60");
        assertThat(value(processing, itemA)).isEqualByComparingTo("3000");
        assertThat(value(processing, itemB)).isEqualByComparingTo("800");
        assertThat(load(po.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PARTIAL);

        assertThatThrownBy(() -> documents.save(SupplyStep.MRR, req().store(processing).from(lineOf(po, 0), "41").build()))
            .hasMessageContaining("only 40 is left");
        BusinessDocument mrr2 = documents.save(SupplyStep.MRR, req().store(processing).from(lineOf(po, 0), "40").build());
        posting.post(SupplyStep.MRR, mrr2.getId());
        assertThat(load(po.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        assertThat(qty(processing, itemA)).isEqualByComparingTo("100");

        // Ten go back to the supplier, out of the store they were received into.
        BusinessDocument prt = documents.save(SupplyStep.PRT, req().from(lineOf(mrr1, 0), "10").build());
        assertThat(load(prt.getId()).getWarehouse().getId()).isEqualTo(processing);
        posting.post(SupplyStep.PRT, prt.getId());
        assertThat(qty(processing, itemA)).isEqualByComparingTo("90");
        assertThat(value(processing, itemA)).isEqualByComparingTo("4500");
        assertThatThrownBy(() -> posting.cancel(SupplyStep.MRR, mrr1.getId(), "wrong supplier"))
            .hasMessageContaining("purchase returns");

        // Issued against the requisition, at the average cost; cancelling puts it back exactly.
        assertThatThrownBy(() -> documents.save(SupplyStep.MI, req().store(processing).from(lineOf(sr, 0), "101").build()))
            .hasMessageContaining("100 allowed");
        BusinessDocument mi = documents.save(SupplyStep.MI, req().store(processing).from(lineOf(sr, 0), "30").build());
        posting.post(SupplyStep.MI, mi.getId());
        assertThat(qty(processing, itemA)).isEqualByComparingTo("60");
        assertThat(value(processing, itemA)).isEqualByComparingTo("3000");
        assertThat(load(sr.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PARTIAL);
        posting.cancel(SupplyStep.MI, mi.getId(), "issued to the wrong batch");
        assertThat(qty(processing, itemA)).isEqualByComparingTo("90");
        assertThat(value(processing, itemA)).isEqualByComparingTo("4500");
        assertThat(load(sr.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);

        // The ledger reads back: +60 +40 -10 -30 +30, closing at 90.
        Map<String, Object> ledger = stockQueries.ledger(itemA, processing, LocalDate.now().minusDays(1), LocalDate.now());
        assertThat((List<?>) ledger.get("moves")).hasSize(5);
        assertThat((BigDecimal) ledger.get("closingQuantity")).isEqualByComparingTo("90");
        assertThat((BigDecimal) ledger.get("closingValue")).isEqualByComparingTo("4500");

        // The viewer shows where each order line stands.
        Map<String, Object> view = views.detail(SupplyStep.PO, po.getId());
        assertThat(figures(view, 0)).containsEntry("Received", bd("100")).containsEntry("Open", bd("0"));
    }

    @Test
    void stockIsValuedAtWeightedAverageAndCannotGoBelowNothing() {
        post(SupplyStep.MR, req().store(weaving).item(itemA, "10", "10").build());
        BusinessDocument second = post(SupplyStep.MR, req().store(weaving).item(itemA, "10", "30").build());
        assertThat(value(weaving, itemA)).isEqualByComparingTo("400");

        post(SupplyStep.MI, req().store(weaving).item(itemA, "5").build());
        assertThat(qty(weaving, itemA)).isEqualByComparingTo("15");
        assertThat(value(weaving, itemA)).isEqualByComparingTo("300");

        BusinessDocument tooMuch = documents.save(SupplyStep.MI, req().store(weaving).item(itemA, "16").build());
        assertThatThrownBy(() -> posting.post(SupplyStep.MI, tooMuch.getId())).hasMessageContaining("holds only 15");
        assertThat(load(tooMuch.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);

        post(SupplyStep.MI, req().store(weaving).item(itemA, "15").build());
        assertThat(qty(weaving, itemA)).isEqualByComparingTo("0");
        assertThat(value(weaving, itemA)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> posting.cancel(SupplyStep.MR, second.getId(), "entered twice"))
            .hasMessageContaining("issued or sent on");
    }

    @Test
    void aTransferLeavesOneStoreAndArrivesAtTheOtherAtTheSameCost() {
        post(SupplyStep.MR, req().store(processing).item(itemB, "50", "8").build());
        BusinessDocument st = documents.save(SupplyStep.ST, req().store(processing).toStore(weaving).item(itemB, "20").build());
        approve(st);

        BusinessDocument ti = documents.save(SupplyStep.TI, req().from(lineOf(st, 0), "20").build());
        assertThat(load(ti.getId()).getToWarehouse().getId()).isEqualTo(weaving);
        posting.post(SupplyStep.TI, ti.getId());
        assertThat(qty(processing, itemB)).isEqualByComparingTo("30");
        assertThat(qty(weaving, itemB)).isEqualByComparingTo("0");
        assertThat(load(st.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);

        BusinessDocument trc = documents.save(SupplyStep.TRC, req().from(lineOf(ti, 0), "20").build());
        posting.post(SupplyStep.TRC, trc.getId());
        assertThat(qty(weaving, itemB)).isEqualByComparingTo("20");
        assertThat(value(weaving, itemB)).isEqualByComparingTo("160");
        assertThat(load(ti.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);

        // Undone in order: the receipt first, and the issue is in transit again.
        posting.cancel(SupplyStep.TRC, trc.getId(), "received into the wrong store");
        assertThat(qty(weaving, itemB)).isEqualByComparingTo("0");
        assertThat(load(ti.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        posting.cancel(SupplyStep.TI, ti.getId(), "not sent");
        assertThat(qty(processing, itemB)).isEqualByComparingTo("50");
        assertThat(value(processing, itemB)).isEqualByComparingTo("400");
    }

    @Test
    void anAdjustmentIsWrittenToStockWhenApproved() {
        BusinessDocument refused = documents.save(SupplyStep.SA, req().store(processing)
            .adjust(itemA, "IN", "5", "12").adjust(itemB, "OUT", "3", null).build());
        assertThatThrownBy(() -> approve(refused)).hasMessageContaining("holds only 0");
        assertThat(qty(processing, itemA)).isEqualByComparingTo("0");

        BusinessDocument sa = documents.save(SupplyStep.SA, req().store(processing).adjust(itemA, "IN", "5", "12").build());
        approve(sa);
        assertThat(qty(processing, itemA)).isEqualByComparingTo("5");
        assertThat(value(processing, itemA)).isEqualByComparingTo("60");
        posting.cancel(SupplyStep.SA, sa.getId(), "count was wrong");
        assertThat(qty(processing, itemA)).isEqualByComparingTo("0");
    }

    @Test
    void aClosedMonthTakesNoPostings() {
        YearMonth month = YearMonth.of(2024, 1);
        try {
            BusinessDocument draft = documents.save(SupplyStep.MR, req().store(processing).date(month.atDay(15)).item(itemA, "4", "5").build());
            assertThatThrownBy(() -> periods.close(month, null)).hasMessageContaining(draft.getDocumentNo());
            posting.post(SupplyStep.MR, draft.getId());
            periods.close(month, "counted");

            BusinessDocument late = documents.save(SupplyStep.MR, req().store(processing).date(month.atDay(20)).item(itemA, "1", "5").build());
            assertThatThrownBy(() -> posting.post(SupplyStep.MR, late.getId())).hasMessageContaining("January 2024 is closed");
            periods.reopen(month, "late receipt found");
            posting.post(SupplyStep.MR, late.getId());
            assertThat(qty(processing, itemA)).isEqualByComparingTo("5");

            // The monthly report reads the month straight from the ledger.
            Map<String, Object> report = stockQueries.monthly(month, processing, null);
            assertThat((List<Map<String, Object>>) report.get("rows")).anySatisfy(r -> {
                assertThat(r.get("itemId")).isEqualTo(itemA);
                assertThat((BigDecimal) r.get("inQuantity")).isEqualByComparingTo("5");
                assertThat((BigDecimal) r.get("closingValue")).isEqualByComparingTo("25");
            });
        } finally {
            if (periods.isClosed(orgId, month)) periods.reopen(month, "test cleanup");
        }
    }

    @Test
    void aFabricLotMovesBetweenStoresAndBack() {
        long lot = greigeLotInWeavingStore("900");
        assertThat(fabric(weaving, lot)).isEqualByComparingTo("900");

        assertThatThrownBy(() -> posting.post(SupplyStep.FTI, documents.save(SupplyStep.FTI,
            req().store(weaving).toStore(processing).lot(lot, "901").build()).getId())).hasMessageContaining("900 free");
        BusinessDocument fti = documents.save(SupplyStep.FTI, req().store(weaving).toStore(processing).lot(lot, "600").build());
        posting.post(SupplyStep.FTI, fti.getId());
        assertThat(fabric(weaving, lot)).isEqualByComparingTo("300");
        assertThat(fabric(processing, lot)).isEqualByComparingTo("0");

        BusinessDocument ftr = documents.save(SupplyStep.FTR, req().from(lineOf(fti, 0), "600").build());
        posting.post(SupplyStep.FTR, ftr.getId());
        assertThat(fabric(processing, lot)).isEqualByComparingTo("600");
        assertThat(load(fti.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inv_fabric_moves WHERE document_id IN (?, ?)",
            Integer.class, fti.getId(), ftr.getId())).isEqualTo(2);

        posting.cancel(SupplyStep.FTR, ftr.getId(), "wrong store");
        posting.cancel(SupplyStep.FTI, fti.getId(), "not moved");
        assertThat(fabric(weaving, lot)).isEqualByComparingTo("900");
        assertThat(fabric(processing, lot)).isEqualByComparingTo("0");
    }

    @Test
    void anMrrRaisesTheGoodsReceivedLiabilityAndItsCancellationReversesIt() {
        openAccountingPeriod();
        BusinessDocument po = documents.save(SupplyStep.PO, req().supplier(supplierId).item(itemA, "10", "25").build());
        approve(po);
        BusinessDocument mrr = documents.save(SupplyStep.MRR, req().store(processing).from(lineOf(po, 0), "10").build());
        posting.post(SupplyStep.MRR, mrr.getId());
        List<Map<String, Object>> entries = jdbc.queryForList("""
            SELECT e.id, l.side, l.account_code, l.amount FROM acc_gl_entries e JOIN acc_gl_entry_lines l ON l.entry_id = e.id
            WHERE e.doc_type_code = 'GOODS_RECEIPT_NOTE' AND e.document_id = ? ORDER BY l.side
            """, mrr.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(e -> assertThat((BigDecimal) e.get("amount")).isEqualByComparingTo("250"));

        posting.cancel(SupplyStep.MRR, mrr.getId(), "goods rejected at the gate");
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM acc_gl_entries WHERE doc_type_code = 'GOODS_RECEIPT_NOTE' AND document_id = ? AND reverses_entry_id IS NOT NULL
            """, Integer.class, mrr.getId())).isEqualTo(1);
        assertThat(qty(processing, itemA)).isEqualByComparingTo("0");
    }

    // --------------------------------------------------------------------------------- helpers

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

    private BusinessDocument post(SupplyStep step, SupplyDocumentRequest request) {
        BusinessDocument doc = documents.save(step, request);
        return posting.post(step, doc.getId());
    }

    private BusinessDocument load(Long id) {
        return tx.execute(s -> {
            BusinessDocument d = repository.findScopedWithLines(id, orgId).orElseThrow();
            d.getLineGroups().forEach(g -> g.getColorLines().size());
            if (d.getParty() != null) d.getParty().getName();
            if (d.getWarehouse() != null) d.getWarehouse().getName();
            if (d.getToWarehouse() != null) d.getToWarehouse().getName();
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

    private BigDecimal qty(long store, long item) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM inv_item_balances WHERE warehouse_id = ? AND item_id = ?",
            BigDecimal.class, store, item);
    }

    private BigDecimal value(long store, long item) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(value), 0) FROM inv_item_balances WHERE warehouse_id = ? AND item_id = ?",
            BigDecimal.class, store, item);
    }

    private BigDecimal fabric(long store, long lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM inv_fabric_balances WHERE warehouse_id = ? AND lot_id = ?",
            BigDecimal.class, store, lot);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BigDecimal> figures(Map<String, Object> view, int line) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        List<Map<String, Object>> figures = (List<Map<String, Object>>) ((List<Map<String, Object>>) view.get("lines")).get(line).get("figures");
        figures.forEach(f -> out.put((String) f.get("label"), ((BigDecimal) f.get("value")).stripTrailingZeros()));
        return out;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).stripTrailingZeros();
    }

    private long party(String code, String name, String role, String qualifier) {
        jdbc.update("""
            INSERT INTO pty_parties (organization_id, code, name, active, deleted, version, created_by, created_at)
            SELECT ?, ?, ?, TRUE, FALSE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM pty_parties WHERE organization_id = ? AND code = ?)
            """, orgId, code, name, orgId, code);
        long id = jdbc.queryForObject("SELECT id FROM pty_parties WHERE organization_id = ? AND code = ?", Long.class, orgId, code);
        jdbc.update("""
            INSERT INTO pty_party_roles (organization_id, party_id, role_type, qualifier, granted_on, is_current, version)
            VALUES (?, ?, ?, ?, CURRENT_DATE, TRUE, 0) ON CONFLICT DO NOTHING
            """, orgId, id, role, qualifier);
        return id;
    }

    /** A fresh chemical, so every test starts with empty stores. */
    private long item(String name) {
        String code = "IT-" + System.nanoTime() + "-" + (++seq);
        jdbc.update("""
            INSERT INTO inv_items (organization_id, item_code, item_name, item_type, category_id, base_uom_id, cost_price,
                                   hazardous, approved, active, deleted, version, created_by, created_at)
            SELECT ?, ?, ?, 'CHEMICALS', c.id, u.id, 0, FALSE, TRUE, TRUE, FALSE, 0, 'it', now()
            FROM inv_item_categories c, inv_uoms u
            WHERE c.organization_id = ? AND c.code = 'CAF111112' AND u.organization_id = ? AND u.code = 'U111'
            """, orgId, code, name + " " + code, orgId, orgId);
        return jdbc.queryForObject("SELECT id FROM inv_items WHERE organization_id = ? AND item_code = ?", Long.class, orgId, code);
    }

    private void openAccountingPeriod() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        jdbc.update("""
            INSERT INTO acc_periods (organization_id, code, name, fiscal_year, period_no, starts_on, ends_on, period_status,
                                     active, deleted, version, created_by, created_at)
            SELECT ?, ?, ?, ?, ?, ?, ?, 'OPEN', TRUE, FALSE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM acc_periods WHERE organization_id = ? AND ? BETWEEN starts_on AND ends_on AND NOT deleted)
            """, orgId, "IT-" + first, "IT " + first, first.getYear(), first.getMonthValue(), first, first.plusMonths(1).minusDays(1),
            orgId, LocalDate.now());
    }

    /** Greige woven for an order and received into the weaving store - through the real chain. */
    private long greigeLotInWeavingStore(String quantity) {
        BusinessDocument booking = tx.execute(s -> {
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
            g.getFabric().setFabricType("Solid Dyed");
            g.getFabric().setConstruction("40x40/133x72");
            BusinessDocumentColorLine l = new BusinessDocumentColorLine();
            l.setColorName("Navy");
            l.setQuantity(new BigDecimal("1000"));
            l.setRate(new BigDecimal("2.5"));
            g.addColorLine(l);
            b.addLineGroup(g);
            g.setOrganizationId(orgId);
            l.setOrganizationId(orgId);
            b.recalculateTotals();
            b.transitionTo(BusinessDocumentStatus.SUBMITTED);
            b.transitionTo(BusinessDocumentStatus.APPROVED);
            return repository.save(b);
        });
        BusinessDocument bpo = load(chainDocuments.createFromBooking(booking.getId()).get(0).getId());
        approveChain(bpo);
        BusinessDocument wwo = chainDocuments.save(ChainStep.WWO, chainRequest(null,
            new ChainDocumentRequest.Line("GROUP", bpo.getLineGroups().get(0).getId(), new BigDecimal("1000"), null, null, null, null, null, null, null, null)));
        approveChain(wwo);
        BusinessDocument gr = chainDocuments.save(ChainStep.GR, chainRequest(weaving,
            new ChainDocumentRequest.Line("COLOUR", lineOf(wwo, 0), new BigDecimal(quantity), null, 20, null, null, null, null, null, null)));
        chainPosting.post(ChainStep.GR, gr.getId());
        return jdbc.queryForObject("SELECT fabric_lot_id FROM gbl_business_document_color_lines WHERE id = ?", Long.class, lineOf(gr, 0));
    }

    private void approveChain(BusinessDocument doc) {
        tx.executeWithoutResult(s -> {
            BusinessDocument d = load(doc.getId());
            d.transitionTo(BusinessDocumentStatus.SUBMITTED);
            d.transitionTo(BusinessDocumentStatus.APPROVED);
            chainApprovals.onApproved(d);
            repository.save(d);
        });
    }

    private static ChainDocumentRequest chainRequest(Long warehouseId, ChainDocumentRequest.Line... lines) {
        return new ChainDocumentRequest(null, null, null, warehouseId, null, null, null, null, null, null, null, null, List.of(lines), List.of());
    }

    private static Builder req() {
        return new Builder();
    }

    /** SupplyDocumentRequest has a long constructor; tests say only what matters. */
    private static final class Builder {
        private Long store, toStore, supplier;
        private LocalDate date;
        private final List<SupplyDocumentRequest.Line> lines = new ArrayList<>();

        Builder store(long id) { store = id; return this; }
        Builder toStore(long id) { toStore = id; return this; }
        Builder supplier(long id) { supplier = id; return this; }
        Builder date(LocalDate d) { date = d; return this; }

        Builder item(long itemId, String qty) { return item(itemId, qty, null); }

        Builder item(long itemId, String qty, String rate) {
            lines.add(new SupplyDocumentRequest.Line(null, itemId, null, new BigDecimal(qty), rate == null ? null : new BigDecimal(rate),
                null, null, null, null, null, null, null, null, null));
            return this;
        }

        Builder adjust(long itemId, String direction, String qty, String rate) {
            lines.add(new SupplyDocumentRequest.Line(null, itemId, null, new BigDecimal(qty), rate == null ? null : new BigDecimal(rate),
                null, null, null, null, null, null, direction, null, null));
            return this;
        }

        Builder from(long sourceId, String qty) { return from(sourceId, qty, null); }

        Builder from(long sourceId, String qty, String rate) {
            lines.add(new SupplyDocumentRequest.Line(sourceId, null, null, new BigDecimal(qty), rate == null ? null : new BigDecimal(rate),
                null, null, null, null, null, null, null, null, null));
            return this;
        }

        Builder lot(long lotId, String qty) {
            lines.add(new SupplyDocumentRequest.Line(null, null, lotId, new BigDecimal(qty), null, 12,
                null, null, null, null, null, null, null, null));
            return this;
        }

        SupplyDocumentRequest build() {
            return new SupplyDocumentRequest(null, date, null, store, toStore, supplier, null, null, null, null, null, null,
                null, null, null, null, lines);
        }
    }
}

package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.*;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.production.*;
import com.asg.fabricerp.supply.*;
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
 * Commercial against real PostgreSQL: Flyway builds the schema (V32 included), Hibernate validates
 * the entities, and export and import paper runs through the real services - from a delivery
 * schedule to a realized CI, and from a requisition to goods received at landed cost.
 *
 * <p>Opt-in, like the other database ITs; point {@code FABRICERP_IT_DB_URL} at a <b>throwaway</b>
 * database. Approval is signed directly (status + the approval listeners).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class CommercialDatabaseIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private CommercialDocumentService commercial;
    @Autowired private CommercialRecordsService records;
    @Autowired private CommercialPostingService commercialPosting;
    @Autowired private CommercialApprovalListener commercialApprovals;
    @Autowired private CommercialViews views;
    @Autowired private CommercialRegisterQueries register;
    @Autowired private ChainDocumentService chain;
    @Autowired private ChainPostingService chainPosting;
    @Autowired private ChainApprovalListener chainApprovals;
    @Autowired private SupplyDocumentService supply;
    @Autowired private SupplyPostingService supplyPosting;
    @Autowired private SupplyApprovalListener supplyApprovals;
    @Autowired private BusinessDocumentRepository repository;
    @Autowired private BusinessUnitRepository units;
    @Autowired private PartyRepository parties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private OrgContext context;

    private long orgId, unitId, buyerId, supplierId, bankId, ownAccount, buyerAccount, hsCode, weaving, processing, lcCost;
    private static int seq;

    @BeforeEach
    void setUp() {
        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        unitId = jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = 'AF'", Long.class, orgId);
        weaving = jdbc.queryForObject("SELECT id FROM org_warehouses WHERE code = 'SAF002'", Long.class);
        processing = jdbc.queryForObject("SELECT id FROM org_warehouses WHERE code = 'SAF001'", Long.class);
        buyerId = party("IT-COM-BUYER", "IT Garments Ltd", "CUSTOMER", "MARKETING");
        supplierId = party("IT-COM-SUPPLIER", "IT Yarn Traders", "SUPPLIER", null);
        bankId = party("IT-COM-BANK", "IT Bank PLC", "BANK", null);
        long self = jdbc.queryForObject("SELECT self_party_id FROM org_organizations WHERE id = ?", Long.class, orgId);
        ownAccount = account(self, "0011-OWN");
        buyerAccount = account(buyerId, "0022-BUYER");
        jdbc.update("INSERT INTO inv_hs_codes (organization_id, hs_code) VALUES (?, '5208.12.00') ON CONFLICT DO NOTHING", orgId);
        hsCode = jdbc.queryForObject("SELECT id FROM inv_hs_codes WHERE organization_id = ? AND hs_code = '5208.12.00'", Long.class, orgId);
        lcCost = jdbc.queryForObject("SELECT id FROM com_cost_heads WHERE organization_id = ? AND code = 'CH002'", Long.class, orgId);

        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(unitId);
        when(context.requireBusinessUnitId()).thenReturn(unitId);
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.requireBusinessUnitCode()).thenReturn("AF");
        when(context.username()).thenReturn("it");
        when(context.warehouseId()).thenReturn(weaving);
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());
        when(context.requireRowScope()).thenReturn(RowScope.unrestrictedScope());
    }

    // ------------------------------------------------------------------------------- export

    @Test
    void anExportOrderIsOfferedOpenedInvoicedAndRealized() {
        Delivered order = deliveredGreige("1000", "500");

        // The PI offers the schedule, at the schedule's price, and weighs the fabric.
        assertThatThrownBy(() -> commercial.save(CommercialStep.EPI, request(piDetails(), line(order.scheduleLine(), "1001"))))
            .hasMessageContaining("1000 allowed");
        BusinessDocument pi = commercial.save(CommercialStep.EPI, request(piDetails(), line(order.scheduleLine(), "1000")));
        assertThat(pi.getSubtotalAmount()).isEqualByComparingTo("2500");
        assertThat(load(pi.getId()).getParty().getId()).isEqualTo(buyerId);
        CommercialDetails piFacts = commercial.details(pi);
        assertThat(piFacts.getAmountInWords()).isEqualTo("US DOLLAR TWO THOUSAND FIVE HUNDRED ONLY");
        assertThat(piFacts.getCalcNetWeight()).isPositive();
        assertThat(piFacts.getGrossWeight()).isEqualByComparingTo(piFacts.getCalcGrossWeight());
        approve(pi);

        // The LC opens part of it; the PI is part-covered.
        BusinessDocument lc = commercial.save(CommercialStep.ELC, request(lcDetails("BTB-IT-1"), line(lineOf(pi, 0), "600")));
        assertThat(lc.getSubtotalAmount()).isEqualByComparingTo("1500");
        approve(lc);
        assertThat(load(pi.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PARTIAL);

        // UDs, UPs within them, a back-to-back LC.
        records.record(CommercialStep.ELC, lc.getId(), event(EventKind.UD, null, "UD-1", "1000"));
        assertThatThrownBy(() -> records.record(CommercialStep.ELC, lc.getId(), event(EventKind.UP, null, "UP-1", "1200")))
            .hasMessageContaining("more than the 1000 declared");
        records.record(CommercialStep.ELC, lc.getId(), event(EventKind.UP, null, "UP-1", "800"));
        records.record(CommercialStep.ELC, lc.getId(), event(EventKind.BTB_LC, "yarn", "BTB-Y-1", "700"));

        // A regular CI invoices the delivery challan under the LC, never twice.
        CommercialDocumentRequest ciRequest = request(ciDetails(CiKind.REGULAR), challan(lineOf(lc, 0), order.challanLine(), "500"));
        assertThatThrownBy(() -> commercial.save(CommercialStep.ECI, request(ciDetails(CiKind.REGULAR),
            challan(lineOf(lc, 0), order.challanLine(), "501")))).hasMessageContaining("500 allowed");
        BusinessDocument ci = commercial.save(CommercialStep.ECI, ciRequest);
        assertThat(ci.getSubtotalAmount()).isEqualByComparingTo("1250");
        // The LC still has 100 open, but that challan is already invoiced in full.
        assertThatThrownBy(() -> commercial.save(CommercialStep.ECI, request(ciDetails(CiKind.REGULAR),
            challan(lineOf(lc, 0), order.challanLine(), "100")))).hasMessageContaining("Challan").hasMessageContaining("only 0 is left");
        approve(ci);
        assertThat(load(lc.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.PARTIAL);

        // Realized in order; the final payment completes it, and can be taken back.
        assertThatThrownBy(() -> records.realize(ci.getId(), RealizationStep.BANK_SUBMISSION, LocalDate.now(), null, null, null))
            .hasMessageContaining("Record document submission first");
        LocalDate day = LocalDate.now();
        records.realize(ci.getId(), RealizationStep.DOC_SUBMISSION, day, null, null, null);
        records.realize(ci.getId(), RealizationStep.PARTY_ACCEPTANCE, day, null, null, null);
        assertThat((LocalDate) realization(ci).get("maturityDue")).isEqualTo(day.plusDays(90));
        records.realize(ci.getId(), RealizationStep.BANK_SUBMISSION, day, null, "BANK-REF", null);
        records.realize(ci.getId(), RealizationStep.BANK_ACCEPTANCE, day, null, null, null);
        records.realize(ci.getId(), RealizationStep.BANK_MATURITY, day, null, null, null);
        assertThatThrownBy(() -> records.realize(ci.getId(), RealizationStep.FINAL_PAYMENT, day, new BigDecimal("1300"), null, null))
            .hasMessageContaining("more than the CI's 1250");
        records.realize(ci.getId(), RealizationStep.FINAL_PAYMENT, day, new BigDecimal("1250"), null, null);
        assertThat(load(ci.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);
        records.undoRealization(ci.getId(), "entered against the wrong bill");
        assertThat(load(ci.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        assertThatThrownBy(() -> commercialPosting.cancel(CommercialStep.ECI, ci.getId(), "wrong")).hasMessageContaining("take it back first");

        // The register shows the LC with what is invoiced against it.
        assertThat(register.exportLcs("BTB-IT-1", null, null)).anySatisfy(r -> {
            assertThat((BigDecimal) r.get("invoiced")).isEqualByComparingTo("1250");
            assertThat((BigDecimal) r.get("udValue")).isEqualByComparingTo("1000");
        });

        // An amendment may not cut the LC below what is invoiced; raised to 700, it takes the CI over.
        BusinessDocument amendment = commercial.revise(CommercialStep.ELC, lc.getId(), "value increased");
        commercial.save(CommercialStep.ELC, withId(amendment.getId(), request(lcDetails("BTB-IT-1"), line(lineOf(pi, 0), "400"))));
        assertThatThrownBy(() -> approve(amendment)).hasMessageContaining("below the 500 already on export cis");
        commercial.save(CommercialStep.ELC, withId(amendment.getId(), request(lcDetails("BTB-IT-1"), line(lineOf(pi, 0), "700"))));
        approve(amendment);
        assertThat(load(lc.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.CANCELLED);
        assertThat(load(ci.getId()).getParentDocument().getId()).isEqualTo(amendment.getId());
        assertThat(jdbc.queryForObject("SELECT drawn_quantity FROM gbl_line_draws WHERE source_id = ? AND stream = 'EXPORT_COMMERCIAL_INVOICE'",
            BigDecimal.class, lineOf(amendment, 0))).isEqualByComparingTo("500");
        assertThat(jdbc.queryForObject("SELECT drawn_quantity FROM gbl_line_draws WHERE source_id = ? AND stream = 'EXPORT_LETTER_OF_CREDIT'",
            BigDecimal.class, lineOf(pi, 0))).isEqualByComparingTo("700");
        // The amended LC kept its UDs.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM com_document_events WHERE document_id = ? AND kind = 'UD'",
            Integer.class, amendment.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------- import

    @Test
    void anImportIsOrderedFromItsPiAndReceivedAtLandedCost() {
        long item = item("Reactive dye");
        BusinessDocument spr = supply.save(SupplyStep.SPR, new SupplyDocumentRequest(null, null, null, processing, null, null, null,
            null, null, null, null, null, null, null, null, null, List.of(itemLine(item, "100"))));
        approveSupply(spr);

        // The import PI buys the requisition - and a local order can no longer buy it again.
        BusinessDocument pi = commercial.save(CommercialStep.IPI, new CommercialDocumentRequest(null, null, supplierId, "USD",
            new BigDecimal("110"), "SUP-PI-9", null, CommercialDocumentRequest.Details.empty(),
            List.of(new CommercialDocumentRequest.Line(lineOf(spr, 0), null, null, new BigDecimal("100"), new BigDecimal("5"), null, null)), null));
        assertThat(pi.getSubtotalAmount()).isEqualByComparingTo("500");
        assertThatThrownBy(() -> supply.save(SupplyStep.PO, new SupplyDocumentRequest(null, null, null, null, null, supplierId, null, null,
            null, null, null, null, null, null, null, null, List.of(new SupplyDocumentRequest.Line(lineOf(spr, 0), null, null, BigDecimal.ONE,
                BigDecimal.ONE, null, null, null, null, null, null, null, null, null, null, null)))))
            .hasMessageContaining("only 0 is left");
        approve(pi);
        assertThat(load(spr.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);

        // Its checklist, in order.
        assertThatThrownBy(() -> records.milestone(pi.getId(), ImportMilestone.LC_CORRECTED, null, null)).hasMessageContaining("LC drafted first");
        records.milestone(pi.getId(), ImportMilestone.LC_DRAFT, null, null);
        records.milestone(pi.getId(), ImportMilestone.LC_CORRECTED, null, null);

        // The LC, with a freight cost of 5,500 taka.
        BusinessDocument lc = commercial.save(CommercialStep.ILC, new CommercialDocumentRequest(null, null, null, null, null, null, null,
            importLcDetails(), List.of(new CommercialDocumentRequest.Line(lineOf(pi, 0), null, null, new BigDecimal("100"), null, null, null)), null));
        approve(lc);
        records.record(CommercialStep.ILC, lc.getId(), new CommercialRecordsService.EventRequest(EventKind.COST, null, null, LocalDate.now(),
            new BigDecimal("5500"), null, lcCost, null, null));

        // The order is placed from the PI, at its price and rate, as an import.
        BusinessDocument po = supply.save(SupplyStep.PO, new SupplyDocumentRequest(null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, List.of(new SupplyDocumentRequest.Line(lineOf(pi, 0), null, null, new BigDecimal("100"),
                null, null, null, null, null, null, null, null, null, null, null, null))));
        BusinessDocument placed = load(po.getId());
        assertThat(placed.getPurchaseType()).isEqualTo(PurchaseType.IMPORT);
        assertThat(placed.getCurrencyCode()).isEqualTo("USD");
        assertThat(placed.getSubtotalAmount()).isEqualByComparingTo("500");
        approveSupply(po);
        assertThat(load(pi.getId()).getStatus()).isEqualTo(BusinessDocumentStatus.COMPLETED);

        // Half received, with its duties: 50 × 5 × 110 = 27,500 + 1,500 duty + half the freight.
        BusinessDocument mrr = supply.save(SupplyStep.MRR, new SupplyDocumentRequest(null, null, null, processing, null, null, null, null,
            null, null, null, null, null, null, null, null, List.of(new SupplyDocumentRequest.Line(lineOf(po, 0), null, null,
                new BigDecimal("50"), null, null, null, null, null, null, null, null, null, null, new BigDecimal("1000"), new BigDecimal("500")))));
        supplyPosting.post(SupplyStep.MRR, mrr.getId());
        assertThat(jdbc.queryForObject("SELECT value FROM inv_item_balances WHERE warehouse_id = ? AND item_id = ?",
            BigDecimal.class, processing, item)).isEqualByComparingTo("31750");
        assertThat(jdbc.queryForObject("SELECT allocated_cost FROM gbl_business_document_color_lines WHERE id = ?",
            BigDecimal.class, lineOf(mrr, 0))).isEqualByComparingTo("2750");
    }

    @Test
    void aPiIsNotSubmittedWithoutItsBankAndTerms() {
        Delivered order = deliveredGreige("300", null);
        BusinessDocument pi = commercial.save(CommercialStep.EPI, request(CommercialDocumentRequest.Details.empty(), line(order.scheduleLine(), "300")));
        assertThat(views.detail(CommercialStep.EPI, pi.getId())).containsEntry("submittable", false);   // no authorities in this test
        assertThat(commercial.details(pi).getBankId()).isNull();
        // Another party's account cannot be the company's own.
        assertThatThrownBy(() -> commercial.save(CommercialStep.EPI, withId(pi.getId(), request(
            detailsWith(bankId, buyerAccount), line(order.scheduleLine(), "300"))))).hasMessageContaining("not the company's own account");
    }

    // --------------------------------------------------------------------------------- helpers

    record Delivered(long scheduleLine, long challanLine) { }

    /** A greige yarn-dyed order woven, received, scheduled, ordered and (when {@code delivered}) delivered. */
    private Delivered deliveredGreige(String quantity, String delivered) {
        BusinessDocument booking = tx.execute(s -> {
            BusinessDocument b = new BusinessDocument();
            b.setOrganizationId(orgId);
            b.setDocumentType(DocumentType.BOOKING);
            b.setBusinessUnit(units.getReferenceById(unitId));
            b.setDocumentNo("IT-CBK-" + System.nanoTime() + "-" + (++seq));
            b.setDocumentDate(LocalDate.now());
            b.setRequiredDate(LocalDate.now().plusDays(30));
            b.setParty(parties.getReferenceById(buyerId));
            b.setCurrencyCode("USD");
            BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
            g.getFabric().setFabricType("Greige Yarn Dyed");
            g.getFabric().setConstruction("30x30/120x80");
            g.getFabric().setWarpCount1("30");
            g.getFabric().setWeftCount1("30");
            g.getFabric().setEpi(new BigDecimal("120"));
            g.getFabric().setPpi(new BigDecimal("80"));
            g.getFabric().setFinishWidth(new BigDecimal("58"));
            BusinessDocumentColorLine l = new BusinessDocumentColorLine();
            l.setColorName("Navy");
            l.setQuantity(new BigDecimal(quantity));
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
        BusinessDocument bpo = load(chain.createFromBooking(booking.getId()).get(0).getId());
        approveChain(bpo);
        BusinessDocument wwo = chain.save(ChainStep.WWO, chainRequest(null, chainLine("COLOUR", lineOf(bpo, 0), quantity, null)));
        approveChain(wwo);
        BusinessDocument gr = chain.save(ChainStep.GR, chainRequest(weaving, chainLine("COLOUR", lineOf(wwo, 0), quantity, null)));
        approveChain(gr);
        chainPosting.post(ChainStep.GR, gr.getId());
        long lot = jdbc.queryForObject("SELECT fabric_lot_id FROM gbl_business_document_color_lines WHERE id = ?", Long.class, lineOf(gr, 0));
        BusinessDocument rpi = chain.save(ChainStep.RPI, chainRequest(null, chainLine("COLOUR", lineOf(bpo, 0), quantity, null)));
        approveChain(rpi);
        if (delivered == null) return new Delivered(lineOf(rpi, 0), 0);
        BusinessDocument order = chain.save(ChainStep.DO, chainRequest(weaving, chainLine("COLOUR", lineOf(rpi, 0), delivered, lot)));
        approveChain(order);
        chainPosting.post(ChainStep.DO, order.getId());
        BusinessDocument fd = chain.save(ChainStep.FD, chainRequest(null, chainLine("COLOUR", lineOf(order, 0), delivered, null)));
        approveChain(fd);
        chainPosting.post(ChainStep.FD, fd.getId());
        return new Delivered(lineOf(rpi, 0), lineOf(fd, 0));
    }

    private CommercialDocumentRequest.Details piDetails() {
        return new CommercialDocumentRequest.Details(LocalDate.now().plusDays(30), null, null, null, null, null, Tenure.D90,
            PaymentTerms.DATE_OF_ACCEPTANCE, IncoTerms.FOB, bankId, ownAccount, null, null, null, null, null, null, null, hsCode,
            "BL-123", null, null, true, false, null, null, null, null, null, null, null, null, null, null, null);
    }

    private CommercialDocumentRequest.Details detailsWith(Long bank, Long ownAccountId) {
        return new CommercialDocumentRequest.Details(null, null, null, null, null, null, null, null, null, bank, ownAccountId, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null);
    }

    private CommercialDocumentRequest.Details lcDetails(String lcNo) {
        return new CommercialDocumentRequest.Details(LocalDate.now().plusDays(90), LocalDate.now().plusDays(60), LocalDate.now(), lcNo,
            "MASTER-1", LocalDate.now(), Tenure.D90, PaymentTerms.DATE_OF_ACCEPTANCE, IncoTerms.FOB, bankId, ownAccount, bankId,
            buyerAccount, null, null, null, null, null, null, null, null, null, true, true, null, null, null, null, null, null, null,
            null, null, null, null);
    }

    private CommercialDocumentRequest.Details ciDetails(CiKind kind) {
        return new CommercialDocumentRequest.Details(null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, kind, null, null, null, null, null, null, null, null,
            null, null);
    }

    private CommercialDocumentRequest.Details importLcDetails() {
        return new CommercialDocumentRequest.Details(LocalDate.now().plusDays(90), LocalDate.now().plusDays(45), LocalDate.now(), "ILC-IT-1",
            null, null, Tenure.D120, PaymentTerms.DATE_OF_ACCEPTANCE, IncoTerms.CFR, bankId, ownAccount, null, null, "Foreign Bank Ltd",
            null, "FBLTUS33", null, "ACC-99", null, null, null, null, false, null, null, ImportDocType.LC, LcType.UPAS, "Chittagong",
            "C&F Co", "IP-1", false, null, null, null, null);
    }

    private static CommercialDocumentRequest request(CommercialDocumentRequest.Details details, CommercialDocumentRequest.Line... lines) {
        return new CommercialDocumentRequest(null, null, null, null, null, null, null, details, List.of(lines), null);
    }

    private static CommercialDocumentRequest withId(Long id, CommercialDocumentRequest r) {
        return new CommercialDocumentRequest(id, r.documentDate(), r.partyId(), r.currencyCode(), r.exchangeRate(), r.referenceNo(),
            r.remarks(), r.details(), r.lines(), r.terms());
    }

    private static CommercialDocumentRequest.Line line(long sourceId, String qty) {
        return new CommercialDocumentRequest.Line(sourceId, null, null, new BigDecimal(qty), null, null, null);
    }

    private static CommercialDocumentRequest.Line challan(long lcLine, long challanLine, String qty) {
        return new CommercialDocumentRequest.Line(lcLine, challanLine, null, new BigDecimal(qty), null, null, null);
    }

    private static CommercialRecordsService.EventRequest event(EventKind kind, String code, String ref, String amount) {
        return new CommercialRecordsService.EventRequest(kind, code, ref, LocalDate.now(), new BigDecimal(amount), null, null, null, null);
    }

    private static SupplyDocumentRequest.Line itemLine(long item, String qty) {
        return new SupplyDocumentRequest.Line(null, item, null, new BigDecimal(qty), null, null, null, null, null, null, null, null, null,
            null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> realization(BusinessDocument ci) {
        return (Map<String, Object>) views.detail(CommercialStep.ECI, ci.getId()).get("realization");
    }

    private void approve(BusinessDocument doc) {
        tx.executeWithoutResult(s -> {
            BusinessDocument d = load(doc.getId());
            d.transitionTo(BusinessDocumentStatus.SUBMITTED);
            d.transitionTo(BusinessDocumentStatus.APPROVED);
            commercialApprovals.onApproved(d);
            repository.save(d);
        });
    }

    private void approveSupply(BusinessDocument doc) {
        tx.executeWithoutResult(s -> {
            BusinessDocument d = load(doc.getId());
            d.transitionTo(BusinessDocumentStatus.SUBMITTED);
            if (d.getDocumentType().isPostedAfterApproval()) {
                d.transitionTo(BusinessDocumentStatus.READY_TO_POST);   // in effect only once posted
            } else {
                d.transitionTo(BusinessDocumentStatus.APPROVED);
                supplyApprovals.onApproved(d);
            }
            repository.save(d);
        });
    }

    private void approveChain(BusinessDocument doc) {
        tx.executeWithoutResult(s -> {
            BusinessDocument d = load(doc.getId());
            d.transitionTo(BusinessDocumentStatus.SUBMITTED);
            if (d.getDocumentType().isPostedAfterApproval()) {
                d.transitionTo(BusinessDocumentStatus.READY_TO_POST);   // in effect only once posted
            } else {
                d.transitionTo(BusinessDocumentStatus.APPROVED);
                chainApprovals.onApproved(d);
            }
            repository.save(d);
        });
    }

    private BusinessDocument load(Long id) {
        return tx.execute(s -> {
            BusinessDocument d = repository.findScopedWithLines(id, orgId).orElseThrow();
            d.getLineGroups().forEach(g -> g.getColorLines().size());
            if (d.getParty() != null) d.getParty().getName();
            if (d.getParentDocument() != null) d.getParentDocument().getId();
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

    private static ChainDocumentRequest chainRequest(Long warehouseId, ChainDocumentRequest.Line... lines) {
        return new ChainDocumentRequest(null, null, null, warehouseId, null, null, null, null, null, null, null, null, List.of(lines), List.of());
    }

    private static ChainDocumentRequest.Line chainLine(String kind, long id, String qty, Long lot) {
        return new ChainDocumentRequest.Line(kind, id, new BigDecimal(qty), lot, null, null, null, null, null, null, null);
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

    private long account(long owner, String number) {
        jdbc.update("""
            INSERT INTO pty_party_bank_accounts (organization_id, party_id, bank_party_id, account_name, account_number, currency_code, is_primary, version)
            SELECT ?, ?, ?, 'IT account', ?, 'USD', TRUE, 0
            WHERE NOT EXISTS (SELECT 1 FROM pty_party_bank_accounts WHERE party_id = ? AND account_number = ?)
            """, orgId, owner, bankId, number, owner, number);
        return jdbc.queryForObject("SELECT id FROM pty_party_bank_accounts WHERE party_id = ? AND account_number = ?", Long.class, owner, number);
    }

    private long item(String name) {
        String code = "IT-C-" + System.nanoTime() + "-" + (++seq);
        jdbc.update("""
            INSERT INTO inv_items (organization_id, item_code, item_name, item_type, category_id, base_uom_id, cost_price,
                                   hazardous, approved, active, deleted, version, created_by, created_at)
            SELECT ?, ?, ?, 'CHEMICALS', c.id, u.id, 0, FALSE, TRUE, TRUE, FALSE, 0, 'it', now()
            FROM inv_item_categories c, inv_uoms u
            WHERE c.organization_id = ? AND c.code = 'CAF111112' AND u.organization_id = ? AND u.code = 'U111'
            """, orgId, code, name + " " + code, orgId, orgId);
        return jdbc.queryForObject("SELECT id FROM inv_items WHERE organization_id = ? AND item_code = ?", Long.class, orgId, code);
    }
}

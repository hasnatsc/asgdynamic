package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.costing.CostingService;
import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Revision semantics.
 *
 * <p>A revision must copy the specification but not the progress: carrying
 * {@code fulfilledQuantity} across would make a revised booking look partly delivered
 * before anything shipped against it, and would silently lower the ceiling that
 * {@link BusinessDocumentColorLine#fulfil} enforces. Exercised here with two colours under
 * one construction — the shape a real Booking payload actually has — not one colour per
 * group, since that flat shape is exactly what an earlier version of this model got wrong.
 */
class BookingServiceRevisionTest {

    private BusinessDocumentRepository repository;
    private DocumentNumberService numbering;
    private CostingService costing;
    private BookingService service;

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        numbering = mock(DocumentNumberService.class);
        costing = mock(CostingService.class);

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()   { return ORG; }
            @Override public Long businessUnitId()   { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()      { return 99L; }
            @Override public String username()       { return "tester"; }
        };

        // Real DocumentRevisionService over the same mocked repository/numbering, so the
        // revision behaviour under test is the actual shared logic, not a stand-in for it.
        DocumentRevisionService revisions = new DocumentRevisionService(repository, numbering);
        service = new BookingService(repository, numbering, costing, revisions, context);
        when(numbering.next(DocumentType.BOOKING)).thenReturn("BKAF000002");
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument approvedBookingWithProgress() {
        BusinessDocument doc = new BusinessDocument();
        doc.setId(1L);
        doc.setOrganizationId(ORG);
        doc.setBusinessUnitId(UNIT);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKAF000001");
        doc.setDocumentDate(LocalDate.of(2026, 1, 10));
        doc.setPartyId(77L);
        doc.setCurrencyCode("USD");

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.setGroupNo(1);
        group.getFabric().setConstruction("40X30/108X62");
        group.getFabric().setWeaveType("2/1 S Twill");

        BusinessDocumentColorLine white = new BusinessDocumentColorLine();
        white.setColorLineNo(1);
        white.setColorName("White");
        white.setLabDipReference("25-08A-2279 OPT-C");
        white.setQuantity(new BigDecimal("600"));
        white.setRate(new BigDecimal("2.50"));
        group.addColorLine(white);

        BusinessDocumentColorLine black = new BusinessDocumentColorLine();
        black.setColorLineNo(2);
        black.setColorName("Black");
        black.setLabDipReference("25-08A-2221 OPT-F");   // genuinely different per colour
        black.setQuantity(new BigDecimal("400"));
        black.setRate(new BigDecimal("2.50"));
        group.addColorLine(black);

        doc.addLineGroup(group);

        white.fulfil(new BigDecimal("400"));   // partly delivered, white only
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        doc.transitionTo(BusinessDocumentStatus.APPROVED);

        when(repository.findScopedWithLines(1L, ORG)).thenReturn(Optional.of(doc));
        return doc;
    }

    @Test
    void revisionCopiesTheSpecificationButNotTheProgress() {
        BusinessDocument original = approvedBookingWithProgress();

        BusinessDocument revision = service.revise(1L, "buyer changed the shade");

        assertThat(revision.getLineGroups()).hasSize(1);
        BusinessDocumentLineGroup copiedGroup = revision.getLineGroups().get(0);
        assertThat(copiedGroup.getColorLines()).hasSize(2);
        assertThat(copiedGroup.getFabric().getConstruction()).isEqualTo("40X30/108X62");
        assertThat(copiedGroup.getFabric().getWeaveType()).isEqualTo("2/1 S Twill");

        BusinessDocumentColorLine copiedWhite = copiedGroup.getColorLines().stream()
            .filter(l -> "White".equals(l.getColorName())).findFirst().orElseThrow();
        BusinessDocumentColorLine copiedBlack = copiedGroup.getColorLines().stream()
            .filter(l -> "Black".equals(l.getColorName())).findFirst().orElseThrow();

        // Per-colour reference fields travel across intact.
        assertThat(copiedWhite.getLabDipReference()).isEqualTo("25-08A-2279 OPT-C");
        assertThat(copiedBlack.getLabDipReference()).isEqualTo("25-08A-2221 OPT-F");
        assertThat(copiedWhite.getQuantity()).isEqualByComparingTo("600");
        assertThat(copiedBlack.getQuantity()).isEqualByComparingTo("400");

        // The point of the test.
        assertThat(copiedWhite.getFulfilledQuantity()).isEqualByComparingTo("0");
        assertThat(copiedWhite.outstandingQuantity()).isEqualByComparingTo("600");

        // And the original is untouched.
        BusinessDocumentColorLine originalWhite = original.getLineGroups().get(0).getColorLines().stream()
            .filter(l -> "White".equals(l.getColorName())).findFirst().orElseThrow();
        assertThat(originalWhite.getFulfilledQuantity()).isEqualByComparingTo("400");
        assertThat(original.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
    }

    @Test
    void revisionStartsAtRevisionOneAndPointsAtTheOriginal() {
        approvedBookingWithProgress();

        BusinessDocument revision = service.revise(1L, "reason");

        assertThat(revision.getRevisionNo()).isEqualTo(1);
        assertThat(revision.getRevisionOfId()).isEqualTo(1L);
        assertThat(revision.getDocumentNo()).isEqualTo("BKAF000002");
        assertThat(revision.getStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);
    }

    @Test
    void secondRevisionStillPointsAtTheRootNotItsPredecessor() {
        BusinessDocument firstRevision = new BusinessDocument();
        firstRevision.setId(2L);
        firstRevision.setOrganizationId(ORG);
        firstRevision.setBusinessUnitId(UNIT);
        firstRevision.setDocumentType(DocumentType.BOOKING);
        firstRevision.setDocumentNo("BKAF000002");
        firstRevision.setDocumentDate(LocalDate.now());
        firstRevision.setRevisionNo(1);
        firstRevision.setRevisionOfId(1L);          // root
        firstRevision.transitionTo(BusinessDocumentStatus.SUBMITTED);
        firstRevision.transitionTo(BusinessDocumentStatus.APPROVED);
        when(repository.findScopedWithLines(2L, ORG)).thenReturn(Optional.of(firstRevision));

        BusinessDocument second = service.revise(2L, "again");

        assertThat(second.getRevisionNo()).isEqualTo(2);
        // Lineage stays flat: every revision points at the root, so no chain walking.
        assertThat(second.getRevisionOfId()).isEqualTo(1L);
    }

    @Test
    void refusesToReviseADraft() {
        BusinessDocument draft = new BusinessDocument();
        draft.setId(3L);
        draft.setOrganizationId(ORG);
        draft.setDocumentType(DocumentType.BOOKING);
        draft.setDocumentNo("BKAF000003");
        when(repository.findScopedWithLines(3L, ORG)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.revise(3L, "why"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("edit");

        verify(numbering, never()).next(any(DocumentType.class));
    }

    // submit() no longer lives on this service — see ApprovalServiceTest's
    // submitRefusesADocumentWithNoLines for the equivalent, now-generic guard.

    @Test
    void saveDoesNotCallCostingForGroupsWithoutACostingCode() {
        BusinessDocument doc = new BusinessDocument();
        doc.setDocumentDate(LocalDate.now());

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setQuantity(new BigDecimal("10"));
        colorLine.setRate(new BigDecimal("3"));
        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        doc.setLineGroups(List.of(group));

        BusinessDocument saved = service.save(doc);

        verifyNoInteractions(costing);
        assertThat(saved.getSubtotalAmount()).isEqualByComparingTo("30");
        assertThat(saved.getLineGroups().get(0).getGroupNo()).isEqualTo(1);
        assertThat(colorLine.getColorLineNo()).isEqualTo(1);
    }
}

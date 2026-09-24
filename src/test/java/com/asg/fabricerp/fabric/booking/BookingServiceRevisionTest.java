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
 * {@link BusinessDocumentLine#fulfil} enforces.
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
        when(numbering.next(DocumentType.BOOKING)).thenReturn("BKGAF000002");
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument approvedBookingWithProgress() {
        BusinessDocument doc = new BusinessDocument();
        doc.setId(1L);
        doc.setOrganizationId(ORG);
        doc.setBusinessUnitId(UNIT);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKGAF000001");
        doc.setDocumentDate(LocalDate.of(2026, 1, 10));
        doc.setPartyId(77L);
        doc.setCurrencyCode("USD");

        BusinessDocumentLine line = new BusinessDocumentLine();
        line.setLineNo(1);
        line.setQuantity(new BigDecimal("1000"));
        line.setRate(new BigDecimal("2.50"));
        line.getFabric().setConstruction("40X30/108X62");
        line.getFabric().setWeaveType("2/1 S Twill");
        doc.addLine(line);

        line.fulfil(new BigDecimal("400"));           // partly delivered
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        doc.transitionTo(BusinessDocumentStatus.APPROVED);

        when(repository.findScopedWithLines(1L, ORG)).thenReturn(Optional.of(doc));
        return doc;
    }

    @Test
    void revisionCopiesTheSpecificationButNotTheProgress() {
        BusinessDocument original = approvedBookingWithProgress();

        BusinessDocument revision = service.revise(1L, "buyer changed the shade");

        assertThat(revision.getLines()).hasSize(1);
        BusinessDocumentLine copied = revision.getLines().get(0);

        assertThat(copied.getQuantity()).isEqualByComparingTo("1000");
        assertThat(copied.getRate()).isEqualByComparingTo("2.50");
        assertThat(copied.getFabric().getConstruction()).isEqualTo("40X30/108X62");
        assertThat(copied.getFabric().getWeaveType()).isEqualTo("2/1 S Twill");

        // The point of the test.
        assertThat(copied.getFulfilledQuantity()).isEqualByComparingTo("0");
        assertThat(copied.outstandingQuantity()).isEqualByComparingTo("1000");

        // And the original is untouched.
        assertThat(original.getLines().get(0).getFulfilledQuantity()).isEqualByComparingTo("400");
        assertThat(original.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
    }

    @Test
    void revisionStartsAtRevisionOneAndPointsAtTheOriginal() {
        approvedBookingWithProgress();

        BusinessDocument revision = service.revise(1L, "reason");

        assertThat(revision.getRevisionNo()).isEqualTo(1);
        assertThat(revision.getRevisionOfId()).isEqualTo(1L);
        assertThat(revision.getDocumentNo()).isEqualTo("BKGAF000002");
        assertThat(revision.getStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);
    }

    @Test
    void secondRevisionStillPointsAtTheRootNotItsPredecessor() {
        BusinessDocument firstRevision = new BusinessDocument();
        firstRevision.setId(2L);
        firstRevision.setOrganizationId(ORG);
        firstRevision.setBusinessUnitId(UNIT);
        firstRevision.setDocumentType(DocumentType.BOOKING);
        firstRevision.setDocumentNo("BKGAF000002");
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
        draft.setDocumentNo("BKGAF000003");
        when(repository.findScopedWithLines(3L, ORG)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.revise(3L, "why"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("edit");

        verify(numbering, never()).next(any(DocumentType.class));
    }

    @Test
    void submitRefusesAnEmptyBooking() {
        BusinessDocument empty = new BusinessDocument();
        empty.setId(4L);
        empty.setOrganizationId(ORG);
        empty.setDocumentType(DocumentType.BOOKING);
        empty.setDocumentNo("BKGAF000004");
        when(repository.findScopedWithLines(4L, ORG)).thenReturn(Optional.of(empty));

        assertThatThrownBy(() -> service.submit(4L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no lines");
    }

    @Test
    void saveDoesNotCallCostingForLinesWithoutACostingCode() {
        BusinessDocument doc = new BusinessDocument();
        doc.setDocumentDate(LocalDate.now());
        BusinessDocumentLine line = new BusinessDocumentLine();
        line.setQuantity(new BigDecimal("10"));
        line.setRate(new BigDecimal("3"));
        doc.setLines(List.of(line));

        BusinessDocument saved = service.save(doc);

        verifyNoInteractions(costing);
        assertThat(saved.getSubtotalAmount()).isEqualByComparingTo("30");
        assertThat(saved.getLines().get(0).getLineNo()).isEqualTo(1);
    }
}

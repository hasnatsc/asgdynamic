package com.asg.fabricerp.fabric.productionorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.costing.CostingService;
import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The point of building a second document type: {@link BusinessDocumentColorLine#fulfil}
 * and {@link BusinessDocumentColorLine#release}, written for Booking's own ceiling, are
 * exercised here purely through {@link BpoService} drawing against a <i>different</i>
 * document's colour lines. Nothing in the entity had to change for a second consumer to
 * use it correctly — that is the generalization the redesign was for.
 */
class BpoServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long BOOKING_ID = 500L;
    private static final Long BOOKING_COLOR_LINE_ID = 501L;

    private BusinessDocumentRepository repository;
    private BpoService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        DocumentNumberService numbering = mock(DocumentNumberService.class);
        when(numbering.next(DocumentType.BULK_PRODUCTION_ORDER)).thenReturn("BPOAF000001");
        CostingService costing = mock(CostingService.class);
        DocumentRevisionService revisions = new DocumentRevisionService(repository, numbering);

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new BpoService(repository, numbering, costing, revisions, parentDraw, DocumentRefs.references(UNIT), context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** A Booking with one construction, one colour ordering 1000 units, nothing drawn yet. */
    private BusinessDocument bookingWithOpenLine(BigDecimal quantity) {
        BusinessDocument booking = new BusinessDocument();
        booking.setId(BOOKING_ID);
        booking.setOrganizationId(ORG);
        booking.setBusinessUnit(DocumentRefs.unit(UNIT));
        booking.setDocumentType(DocumentType.BOOKING);
        booking.setDocumentNo("BKAF000001");
        booking.setParty(DocumentRefs.party(42L));

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setId(BOOKING_COLOR_LINE_ID);
        colorLine.setColorLineNo(1);
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.setGroupNo(1);
        group.addColorLine(colorLine);
        booking.addLineGroup(group);

        when(repository.findScopedWithLines(BOOKING_ID, ORG)).thenReturn(Optional.of(booking));
        return booking;
    }

    private BusinessDocumentColorLine onlyColorLine(BusinessDocument doc) {
        return doc.getLineGroups().get(0).getColorLines().get(0);
    }

    private BusinessDocument bpoRequest(BigDecimal quantity) {
        BusinessDocument bpo = new BusinessDocument();
        bpo.setDocumentDate(LocalDate.now());
        bpo.setParentDocument(DocumentRefs.document(BOOKING_ID));

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setSourceColorLine(DocumentRefs.colorLine(BOOKING_COLOR_LINE_ID));
        colorLine.setQuantity(quantity);
        colorLine.setRate(new BigDecimal("2"));

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        bpo.setLineGroups(List.of(group));
        return bpo;
    }

    @Test
    void drawingWithinTheBookingLinesOutstandingQuantitySucceeds() {
        BusinessDocument booking = bookingWithOpenLine(new BigDecimal("1000"));

        BusinessDocument bpo = service.save(bpoRequest(new BigDecimal("600")));

        assertThat(bpo.getDocumentType()).isEqualTo(DocumentType.BULK_PRODUCTION_ORDER);
        assertThat(DocumentRefs.id(bpo.getParentDocument())).isEqualTo(BOOKING_ID);
        assertThat(DocumentRefs.id(bpo.getParty())).isEqualTo(42L);   // inherited from the booking

        BusinessDocumentColorLine bookingLine = onlyColorLine(booking);
        assertThat(bookingLine.getFulfilledQuantity()).isEqualByComparingTo("600");
        assertThat(bookingLine.outstandingQuantity()).isEqualByComparingTo("400");

        // The booking, whose line ledger changed, must be persisted too.
        verify(repository, times(1)).save(booking);
    }

    @Test
    void refusesToDrawMoreThanTheBookingLineHasOutstanding() {
        bookingWithOpenLine(new BigDecimal("1000"));

        assertThatThrownBy(() -> service.save(bpoRequest(new BigDecimal("1500"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exceed the ordered quantity");
    }

    @Test
    void aSecondBpoCanOnlyDrawWhatTheFirstLeftOutstanding() {
        BusinessDocument booking = bookingWithOpenLine(new BigDecimal("1000"));

        service.save(bpoRequest(new BigDecimal("700")));
        assertThat(onlyColorLine(booking).outstandingQuantity()).isEqualByComparingTo("300");

        assertThatThrownBy(() -> service.save(bpoRequest(new BigDecimal("400"))))
            .isInstanceOf(IllegalStateException.class);

        // The successful 300 still fits.
        service.save(bpoRequest(new BigDecimal("300")));
        assertThat(onlyColorLine(booking).outstandingQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void deletingADraftBpoReleasesWhatItHadReserved() {
        BusinessDocument booking = bookingWithOpenLine(new BigDecimal("1000"));
        BusinessDocument bpo = service.save(bpoRequest(new BigDecimal("600")));
        bpo.setId(900L);
        when(repository.findScopedWithLines(900L, ORG)).thenReturn(Optional.of(bpo));

        assertThat(onlyColorLine(booking).outstandingQuantity()).isEqualByComparingTo("400");

        service.delete(900L);

        assertThat(onlyColorLine(booking).outstandingQuantity()).isEqualByComparingTo("1000");
        assertThat(bpo.getDeleted()).isTrue();
    }

    @Test
    void editingADraftBpoReplacesItsReservationRatherThanStacking() {
        BusinessDocument booking = bookingWithOpenLine(new BigDecimal("1000"));
        BusinessDocument bpo = service.save(bpoRequest(new BigDecimal("600")));
        bpo.setId(900L);
        when(repository.findScopedWithLines(900L, ORG)).thenReturn(Optional.of(bpo));

        BusinessDocument edited = bpoRequest(new BigDecimal("250"));
        edited.setId(900L);

        service.save(edited);

        // Not 600 + 250 = 850 outstanding-350; the old draw is released before the new one applies.
        assertThat(onlyColorLine(booking).getFulfilledQuantity()).isEqualByComparingTo("250");
        assertThat(onlyColorLine(booking).outstandingQuantity()).isEqualByComparingTo("750");
    }

    @Test
    void rejectsASourceLineThatDoesNotBelongToTheNamedBooking() {
        bookingWithOpenLine(new BigDecimal("1000"));

        BusinessDocument request = bpoRequest(new BigDecimal("100"));
        request.getLineGroups().get(0).getColorLines().get(0).setSourceColorLine(DocumentRefs.colorLine(999999L));   // not on this booking

        assertThatThrownBy(() -> service.save(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not on Booking");
    }

    @Test
    void refusesToCreateWithoutNamingABooking() {
        BusinessDocument orphan = bpoRequest(new BigDecimal("100"));
        orphan.setParentDocument(null);

        assertThatThrownBy(() -> service.save(orphan))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Booking");
    }

    @Test
    void openBookingLinesExcludesFullyDrawnLines() {
        bookingWithOpenLine(new BigDecimal("500"));
        service.save(bpoRequest(new BigDecimal("500")));   // fully consumes it

        assertThat(service.openBookingLines(BOOKING_ID)).isEmpty();
    }
}

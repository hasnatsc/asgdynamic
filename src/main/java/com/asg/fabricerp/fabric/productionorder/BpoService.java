package com.asg.fabricerp.fabric.productionorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.costing.CostingService;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulk Production Order — raised against a Booking to commit weaving capacity.
 *
 * <h2>What this proves</h2>
 * {@code BookingService} showed the document model works for one type. This is the second,
 * chosen because it is genuinely different from Booking rather than a copy: a BPO line
 * draws against a specific Booking line's outstanding quantity ({@code transaction_qty_so}
 * alongside {@code transactionQty} in the legacy {@code productionOrder} screen). That
 * needed a real model change — {@link BusinessDocumentLine#getSourceLineId()} — not a
 * workaround, which is the outcome worth having: the gap showed up here instead of in
 * production.
 *
 * <p>{@link BusinessDocumentLine#fulfil} and {@link BusinessDocumentLine#release}, built
 * for Booking's own ceiling, are reused unchanged to enforce that a BPO can never commit
 * more than a Booking line has outstanding — proof the guard generalizes rather than being
 * Booking-specific.
 */
@Service
public class BpoService {

    private static final DocumentType TYPE = DocumentType.BULK_PRODUCTION_ORDER;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final CostingService costing;
    private final DocumentRevisionService revisions;
    private final OrgContext context;

    public BpoService(BusinessDocumentRepository repository,
                      DocumentNumberService numbering,
                      CostingService costing,
                      DocumentRevisionService revisions,
                      OrgContext context) {
        this.repository = repository;
        this.numbering = numbering;
        this.costing = costing;
        this.revisions = revisions;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(BusinessDocumentStatus status,
                                         LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(
            context.requireOrganizationId(), context.requireBusinessUnitId(),
            TYPE, status, from, to, query, pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .orElseThrow(() -> new IllegalArgumentException("BPO not found: " + id));
    }

    /**
     * The Booking lines still available to draw against, with what remains on each —
     * feeds the "raise BPO" form's line picker.
     */
    @Transactional(readOnly = true)
    public List<BusinessDocumentLine> openBookingLines(Long bookingId) {
        return loadParent(bookingId).getLines().stream()
            .filter(l -> l.outstandingQuantity().signum() > 0)
            .toList();
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        if (submitted.getId() == null) {
            return create(submitted);
        }
        return update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        Long bookingId = submitted.getParentDocumentId();
        if (bookingId == null) {
            throw new IllegalArgumentException("A BPO must name the Booking it is raised against");
        }
        BusinessDocument booking = loadParent(bookingId);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnitId(context.requireBusinessUnitId());
        submitted.setParentDocumentId(booking.getId());
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getPartyId() == null) {
            submitted.setPartyId(booking.getPartyId());
        }

        drawAgainstBooking(booking, submitted.getLines());
        repository.save(booking);

        refreshCostingFigures(submitted);
        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument booking = loadParent(target.getParentDocumentId());
        releaseAgainstBooking(booking, target.getLines());

        applyHeader(submitted, target);
        target.setLines(submitted.getLines());

        drawAgainstBooking(booking, target.getLines());
        repository.save(booking);

        refreshCostingFigures(target);
        target.recalculateTotals();
        return repository.save(target);
    }

    @Transactional
    public BusinessDocument submit(Long id) {
        BusinessDocument doc = get(id);
        if (doc.getLines().isEmpty()) {
            throw new IllegalStateException(
                "BPO %s has no lines and cannot be submitted".formatted(doc.getDocumentNo()));
        }
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        return repository.save(doc);
    }

    @Transactional
    public BusinessDocument revise(Long id, String reason) {
        return revisions.revise(get(id), reason);
    }

    /**
     * Deleting a still-draft BPO must give back whatever it had reserved against the
     * Booking, or the Booking line stays short even though nothing was ever produced.
     */
    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();

        BusinessDocument booking = loadParent(doc.getParentDocumentId());
        releaseAgainstBooking(booking, doc.getLines());
        repository.save(booking);

        doc.markDeleted();
        repository.save(doc);
    }

    private BusinessDocument loadParent(Long bookingId) {
        BusinessDocument booking = repository.findScopedWithLines(bookingId, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getDocumentType() != DocumentType.BOOKING) {
            throw new IllegalArgumentException(
                "Document %s is not a Booking".formatted(booking.getDocumentNo()));
        }
        return booking;
    }

    /** Consumes booking-line capacity for every BPO line that names a source. */
    private void drawAgainstBooking(BusinessDocument booking, List<BusinessDocumentLine> bpoLines) {
        Map<Long, BusinessDocumentLine> bookingLinesById = indexById(booking.getLines());
        int lineNo = 1;
        for (BusinessDocumentLine bpoLine : bpoLines) {
            bpoLine.setLineNo(lineNo++);
            if (bpoLine.getSourceLineId() == null) continue;
            sourceLine(bookingLinesById, bpoLine, booking).fulfil(bpoLine.getQuantity());
        }
    }

    /** Gives back whatever a set of BPO lines had previously drawn. */
    private void releaseAgainstBooking(BusinessDocument booking, List<BusinessDocumentLine> bpoLines) {
        Map<Long, BusinessDocumentLine> bookingLinesById = indexById(booking.getLines());
        for (BusinessDocumentLine bpoLine : bpoLines) {
            if (bpoLine.getSourceLineId() == null) continue;
            sourceLine(bookingLinesById, bpoLine, booking).release(bpoLine.getQuantity());
        }
    }

    private BusinessDocumentLine sourceLine(Map<Long, BusinessDocumentLine> bookingLinesById,
                                            BusinessDocumentLine bpoLine, BusinessDocument booking) {
        BusinessDocumentLine source = bookingLinesById.get(bpoLine.getSourceLineId());
        if (source == null) {
            throw new IllegalArgumentException(
                "Line %d names source line %d, which is not on Booking %s"
                    .formatted(bpoLine.getLineNo(), bpoLine.getSourceLineId(), booking.getDocumentNo()));
        }
        return source;
    }

    private Map<Long, BusinessDocumentLine> indexById(List<BusinessDocumentLine> lines) {
        Map<Long, BusinessDocumentLine> byId = new HashMap<>();
        for (BusinessDocumentLine line : lines) byId.put(line.getId(), line);
        return byId;
    }

    private void applyHeader(BusinessDocument from, BusinessDocument to) {
        to.setDocumentDate(from.getDocumentDate());
        to.setRequiredDate(from.getRequiredDate());
        to.setRemarks(from.getRemarks());
        to.setWarehouseId(from.getWarehouseId());
        // partyId, currency and parentDocumentId are set once at creation from the Booking
        // and never move on an edit.
    }

    private void refreshCostingFigures(BusinessDocument doc) {
        for (BusinessDocumentLine line : doc.getLines()) {
            if (!line.getFabric().hasCostingCode()) continue;
            BigDecimal breakEven = costing.applyTo(line.getFabric());
            if (line.getRate().signum() == 0 && breakEven != null) {
                line.setRate(breakEven);
            }
        }
    }
}

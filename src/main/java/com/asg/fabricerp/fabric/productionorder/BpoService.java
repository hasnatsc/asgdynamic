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
import java.util.List;

/**
 * Bulk Production Order — raised against a Booking to commit weaving capacity.
 *
 * <h2>What this proves</h2>
 * {@code BookingService} showed the document model works for one type. This is the second,
 * chosen because it is genuinely different from Booking rather than a copy: a BPO line
 * draws against a specific Booking line's outstanding quantity ({@code transaction_qty_so}
 * alongside {@code transactionQty} in the legacy {@code productionOrder} screen). That
 * needed a real model change — {@link BusinessDocumentColorLine#getSourceColorLineId()} — not a
 * workaround, which is the outcome worth having: the gap showed up here instead of in
 * production.
 *
 * <p>The draw/release/loadParent mechanics below now live in {@link ParentLineDrawService},
 * shared with {@code RequestForPiService} and {@code WeavingWorkOrderService} — this class
 * was the original, and moving out once a second consumer needed the same logic left it
 * holding only what is actually BPO-specific: costing, and which fields to copy on edit.
 */
@Service
public class BpoService {

    private static final DocumentType TYPE = DocumentType.BULK_PRODUCTION_ORDER;
    private static final DocumentType PARENT_TYPE = DocumentType.BOOKING;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final CostingService costing;
    private final DocumentRevisionService revisions;
    private final ParentLineDrawService parentDraw;
    private final OrgContext context;

    public BpoService(BusinessDocumentRepository repository,
                      DocumentNumberService numbering,
                      CostingService costing,
                      DocumentRevisionService revisions,
                      ParentLineDrawService parentDraw,
                      OrgContext context) {
        this.repository = repository;
        this.numbering = numbering;
        this.costing = costing;
        this.revisions = revisions;
        this.parentDraw = parentDraw;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(BusinessDocumentStatus status,
                                         LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(
            context.requireOrganizationId(), context.requireBusinessUnitId(),
            TYPE, status, from, to, query, context.requireRowScope(), pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException("BPO not found: " + id));
    }

    /** The Booking lines still available to draw against — feeds the "raise BPO" line picker. */
    @Transactional(readOnly = true)
    public List<BusinessDocumentColorLine> openBookingLines(Long bookingId) {
        return parentDraw.openLines(parentDraw.loadParent(bookingId, PARENT_TYPE));
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        return submitted.getId() == null ? create(submitted) : update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        BusinessDocument booking = parentDraw.loadParent(submitted.getParentDocumentId(), PARENT_TYPE);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnitId(context.requireBusinessUnitId());
        submitted.setParentDocumentId(booking.getId());
        submitted.stampMarketingTeam(booking.getMarketingTeamId());   // ADM-7: the team travels downstream
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getPartyId() == null) {
            submitted.setPartyId(booking.getPartyId());
        }

        parentDraw.draw(booking, submitted.getLineGroups());
        parentDraw.save(booking);

        refreshCostingFigures(submitted);
        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument booking = parentDraw.loadParent(target.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(booking, target.getLineGroups());

        applyHeader(submitted, target);
        target.setLineGroups(submitted.getLineGroups());

        parentDraw.draw(booking, target.getLineGroups());
        parentDraw.save(booking);

        refreshCostingFigures(target);
        target.recalculateTotals();
        return repository.save(target);
    }

    // submit/approve/reject live in ApprovalService now — generic over every document
    // type rather than a one-line copy per service. See BpoController.

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

        BusinessDocument booking = parentDraw.loadParent(doc.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(booking, doc.getLineGroups());
        parentDraw.save(booking);

        doc.markDeleted();
        repository.save(doc);
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
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            if (!group.getFabric().hasCostingCode()) continue;
            BigDecimal breakEven = costing.applyTo(group.getFabric());
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                if (line.getRate().signum() == 0 && breakEven != null) {
                    line.setRate(breakEven);
                }
            }
        }
    }
}

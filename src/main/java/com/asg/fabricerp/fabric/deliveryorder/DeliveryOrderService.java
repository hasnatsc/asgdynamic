package com.asg.fabricerp.fabric.deliveryorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Delivery Order — the fourth link in the sales chain, raised against a Request-for-PI
 * rather than against the BPO directly. Confirmed by the legacy {@code deliveryOrder}
 * screen's own fields: {@code transactionQtySchedule} and {@code prevTransactionQty} name
 * the schedule (Request-for-PI) explicitly, and its grid shows both "Schedule Code" and
 * "BPO NO" as separate, traced-through columns — the BPO reference is read-only context,
 * the schedule is what quantity is actually checked against.
 *
 * <p>No revision handler and no costing handler in its captured functions, matching
 * {@code WeavingWorkOrderService}'s shape rather than {@code RequestForPiService}'s.
 */
@Service
public class DeliveryOrderService {

    private static final DocumentType TYPE = DocumentType.DELIVERY_ORDER;
    private static final DocumentType PARENT_TYPE = DocumentType.REQUEST_FOR_PI;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final ParentLineDrawService parentDraw;
    private final OrgContext context;

    public DeliveryOrderService(BusinessDocumentRepository repository,
                                DocumentNumberService numbering,
                                ParentLineDrawService parentDraw,
                                OrgContext context) {
        this.repository = repository;
        this.numbering = numbering;
        this.parentDraw = parentDraw;
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
            .orElseThrow(() -> new IllegalArgumentException("Delivery Order not found: " + id));
    }

    /** The Request-for-PI lines still available to deliver against. */
    @Transactional(readOnly = true)
    public List<BusinessDocumentLine> openScheduleLines(Long requestForPiId) {
        return parentDraw.openLines(parentDraw.loadParent(requestForPiId, PARENT_TYPE));
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        return submitted.getId() == null ? create(submitted) : update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        BusinessDocument schedule = parentDraw.loadParent(submitted.getParentDocumentId(), PARENT_TYPE);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnitId(context.requireBusinessUnitId());
        submitted.setParentDocumentId(schedule.getId());
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getPartyId() == null) {
            submitted.setPartyId(schedule.getPartyId());
        }

        parentDraw.draw(schedule, submitted.getLines());
        parentDraw.save(schedule);

        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument schedule = parentDraw.loadParent(target.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(schedule, target.getLines());

        applyHeader(submitted, target);
        target.setLines(submitted.getLines());

        parentDraw.draw(schedule, target.getLines());
        parentDraw.save(schedule);

        target.recalculateTotals();
        return repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();

        BusinessDocument schedule = parentDraw.loadParent(doc.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(schedule, doc.getLines());
        parentDraw.save(schedule);

        doc.markDeleted();
        repository.save(doc);
    }

    private void applyHeader(BusinessDocument from, BusinessDocument to) {
        to.setDocumentDate(from.getDocumentDate());
        to.setRequiredDate(from.getRequiredDate());
        to.setRemarks(from.getRemarks());
        to.setWarehouseId(from.getWarehouseId());
    }
}

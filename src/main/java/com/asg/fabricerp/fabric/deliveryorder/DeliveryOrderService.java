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
    private final DocumentReferences references;
    private final OrgContext context;

    public DeliveryOrderService(BusinessDocumentRepository repository,
                                DocumentNumberService numbering,
                                ParentLineDrawService parentDraw,
                                DocumentReferences references,
                                OrgContext context) {
        this.repository = repository;
        this.numbering = numbering;
        this.parentDraw = parentDraw;
        this.references = references;
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
            .orElseThrow(() -> new IllegalArgumentException("Delivery Order not found: " + id));
    }

    /** The Request-for-PI lines still available to deliver against. */
    @Transactional(readOnly = true)
    public List<BusinessDocumentColorLine> openScheduleLines(Long requestForPiId) {
        return parentDraw.openLines(parentDraw.loadParent(requestForPiId, PARENT_TYPE));
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        return submitted.getId() == null ? create(submitted) : update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        BusinessDocument schedule = parentDraw.loadParent(submitted.getParentDocument(), PARENT_TYPE);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnit(references.currentBusinessUnit());
        submitted.setParentDocument(schedule);
        submitted.stampMarketingTeam(schedule.getMarketingTeam());   // ADM-7: the team travels downstream
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getParty() == null) {
            submitted.setParty(schedule.getParty());
        }

        parentDraw.draw(schedule, submitted.getLineGroups());
        parentDraw.save(schedule);

        references.resolve(submitted);

        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument schedule = parentDraw.loadParent(target.getParentDocument(), PARENT_TYPE);
        parentDraw.release(schedule, target.getLineGroups());

        applyHeader(submitted, target);
        target.setLineGroups(submitted.getLineGroups());

        parentDraw.draw(schedule, target.getLineGroups());
        parentDraw.save(schedule);

        references.resolve(target);

        target.recalculateTotals();
        return repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();

        BusinessDocument schedule = parentDraw.loadParent(doc.getParentDocument(), PARENT_TYPE);
        parentDraw.release(schedule, doc.getLineGroups());
        parentDraw.save(schedule);

        doc.markDeleted();
        repository.save(doc);
    }

    private void applyHeader(BusinessDocument from, BusinessDocument to) {
        to.setDocumentDate(from.getDocumentDate());
        to.setRequiredDate(from.getRequiredDate());
        to.setRemarks(from.getRemarks());
        to.setWarehouse(from.getWarehouse());
    }
}

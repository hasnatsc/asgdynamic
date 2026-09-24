package com.asg.fabricerp.fabric.processingworkorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Processing Work Order — the dyeing-side counterpart to Weaving Work Order, both raised
 * independently against the same BPO (legacy {@code dyeingWoByPro}, "Processing WO against
 * BPO"). Its captured functions are the exact same shape as {@code textileWoByPro}'s —
 * {@code proPopulate}/{@code proReceive}, no revision handler, no costing handler — so this
 * class is structurally identical to {@code WeavingWorkOrderService}. Kept as its own type
 * and its own service rather than folded together because they draw against different
 * halves of the BPO's fabric spec in the real mill (weaving capacity vs. processing/dyeing
 * capacity) even though the code shape is currently the same; if that stays true as more of
 * the model is built, revisit merging them.
 */
@Service
public class ProcessingWorkOrderService {

    private static final DocumentType TYPE = DocumentType.PROCESSING_WORK_ORDER;
    private static final DocumentType PARENT_TYPE = DocumentType.BULK_PRODUCTION_ORDER;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final ParentLineDrawService parentDraw;
    private final OrgContext context;

    public ProcessingWorkOrderService(BusinessDocumentRepository repository,
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
            .orElseThrow(() -> new IllegalArgumentException("Processing Work Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BusinessDocumentLine> openBpoLines(Long bpoId) {
        return parentDraw.openLines(parentDraw.loadParent(bpoId, PARENT_TYPE));
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        return submitted.getId() == null ? create(submitted) : update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        BusinessDocument bpo = parentDraw.loadParent(submitted.getParentDocumentId(), PARENT_TYPE);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnitId(context.requireBusinessUnitId());
        submitted.setParentDocumentId(bpo.getId());
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getPartyId() == null) {
            submitted.setPartyId(bpo.getPartyId());
        }

        parentDraw.draw(bpo, submitted.getLines());
        parentDraw.save(bpo);

        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument bpo = parentDraw.loadParent(target.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(bpo, target.getLines());

        applyHeader(submitted, target);
        target.setLines(submitted.getLines());

        parentDraw.draw(bpo, target.getLines());
        parentDraw.save(bpo);

        target.recalculateTotals();
        return repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();

        BusinessDocument bpo = parentDraw.loadParent(doc.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(bpo, doc.getLines());
        parentDraw.save(bpo);

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

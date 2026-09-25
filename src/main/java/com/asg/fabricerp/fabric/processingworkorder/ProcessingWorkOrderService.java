package com.asg.fabricerp.fabric.processingworkorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
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
    private final BusinessNumberService numbering;
    private final ParentLineDrawService parentDraw;
    private final DocumentReferences references;
    private final OrgContext context;

    public ProcessingWorkOrderService(BusinessDocumentRepository repository,
                                      BusinessNumberService numbering,
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
            .orElseThrow(() -> new IllegalArgumentException("Processing Work Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BusinessDocumentColorLine> openBpoLines(Long bpoId) {
        return parentDraw.openLines(parentDraw.loadParent(bpoId, PARENT_TYPE));
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        references.resolve(submitted, TYPE);   // before anything is copied or flushed
        return submitted.getId() == null ? create(submitted) : update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        BusinessDocument bpo = parentDraw.loadParent(submitted.getParentDocument(), PARENT_TYPE);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnit(references.currentBusinessUnit());
        submitted.setParentDocument(bpo);
        submitted.stampMarketingTeam(bpo.getMarketingTeam());   // ADM-7: the team travels downstream
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        submitted.setDocumentNo(numbering.next(TYPE, submitted.getDocumentDate(), submitted.getBusinessUnit()));
        if (submitted.getParty() == null) {
            submitted.setParty(bpo.getParty());
        }

        parentDraw.draw(bpo, submitted.getLineGroups());
        parentDraw.save(bpo);

        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument bpo = parentDraw.loadParent(target.getParentDocument(), PARENT_TYPE);
        parentDraw.release(bpo, target.getLineGroups());

        applyHeader(submitted, target);
        target.setLineGroups(submitted.getLineGroups());

        parentDraw.draw(bpo, target.getLineGroups());
        parentDraw.save(bpo);

        target.recalculateTotals();
        return repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();

        BusinessDocument bpo = parentDraw.loadParent(doc.getParentDocument(), PARENT_TYPE);
        parentDraw.release(bpo, doc.getLineGroups());
        parentDraw.save(bpo);

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

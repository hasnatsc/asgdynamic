package com.asg.fabricerp.fabric.greigereceive;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Greige Fabrics Received — records greige coming off the loom, raised against the same
 * BPO as Weaving/Processing Work Order (legacy {@code textileReceive}, confirmed by the
 * same {@code proPopulate}/{@code proReceive} functions on all three). Structurally
 * identical to {@code WeavingWorkOrderService} for the same reason
 * {@code ProcessingWorkOrderService} is.
 *
 * <p>What is genuinely uncertain: whether {@code GreigeIssueService} (not yet built) should
 * draw against this document's lines or against the Weaving Work Order's — the legacy
 * {@code textileIssue} screen's captured fields point at the Work Order, not this one. See
 * that gap noted on {@link DocumentType#GREIGE_ISSUE} until it is resolved and built.
 */
@Service
public class GreigeReceiveService {

    private static final DocumentType TYPE = DocumentType.GREIGE_RECEIVE;
    private static final DocumentType PARENT_TYPE = DocumentType.BULK_PRODUCTION_ORDER;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final ParentLineDrawService parentDraw;
    private final OrgContext context;

    public GreigeReceiveService(BusinessDocumentRepository repository,
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
            TYPE, status, from, to, query, context.requireRowScope(), pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException("Greige Receive not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BusinessDocumentColorLine> openBpoLines(Long bpoId) {
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
        submitted.stampMarketingTeam(bpo.getMarketingTeamId());   // ADM-7: the team travels downstream
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getPartyId() == null) {
            submitted.setPartyId(bpo.getPartyId());
        }

        parentDraw.draw(bpo, submitted.getLineGroups());
        parentDraw.save(bpo);

        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument bpo = parentDraw.loadParent(target.getParentDocumentId(), PARENT_TYPE);
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

        BusinessDocument bpo = parentDraw.loadParent(doc.getParentDocumentId(), PARENT_TYPE);
        parentDraw.release(bpo, doc.getLineGroups());
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

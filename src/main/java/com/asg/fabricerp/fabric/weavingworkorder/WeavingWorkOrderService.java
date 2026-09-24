package com.asg.fabricerp.fabric.weavingworkorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Weaving Work Order — the production side's counterpart to Request-for-PI, raised against
 * the same BPO (legacy {@code textileWoByPro}, "Weaving WO against BPO"). Confirmed by the
 * crawl to draw directly against BPO lines via {@code proPopulate}/{@code proReceive}, the
 * same functions Processing WO and Greige Receive use — all three draw independently
 * against the BPO rather than against each other.
 *
 * <h2>The first branch in the pattern</h2>
 * Every document type built so far is revisable ({@link DocumentType#isRevisable()}). This
 * one is not — {@code textileWoByPro}'s captured functions carry no revision handler,
 * unlike {@code productionOrder}'s {@code poReset}/BPO's own revision or
 * {@code proPiDeliverySchedule}'s {@code pdsRevisedConfirm}. A shop-floor work order is
 * corrected by editing it while still {@code DRAFT}/{@code REJECTED}, not by superseding an
 * approved one with a new version — there is no {@code revise()} method here, and
 * {@code WeavingWorkOrderController} exposes no revise route. This is a real difference in
 * the business process, not an oversight: verify it against the legacy screen (and, ideally,
 * the real production users) before adding one.
 */
@Service
public class WeavingWorkOrderService {

    private static final DocumentType TYPE = DocumentType.WEAVING_WORK_ORDER;
    private static final DocumentType PARENT_TYPE = DocumentType.BULK_PRODUCTION_ORDER;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final ParentLineDrawService parentDraw;
    private final OrgContext context;

    public WeavingWorkOrderService(BusinessDocumentRepository repository,
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
            .orElseThrow(() -> new IllegalArgumentException("Weaving Work Order not found: " + id));
    }

    /** The BPO lines still available to commit to a loom — feeds the "raise" form's picker. */
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
        // Traced through for display only — a work order has no commercial party of its
        // own, but showing the buyer it ultimately serves needs no extra join this way.
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

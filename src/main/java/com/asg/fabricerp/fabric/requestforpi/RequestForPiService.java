package com.asg.fabricerp.fabric.requestforpi;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Request For PI — a delivery schedule raised against a BPO (legacy
 * {@code proPiDeliverySchedule}), the third link in the sales chain: Booking → BPO →
 * Request-for-PI → Delivery Order → Fabrics Delivery.
 *
 * <h2>What this is, not what it might look like</h2>
 * Same shape as {@code BpoService} on the surface — draws against a parent's lines via
 * {@link ParentLineDrawService} — but with a real difference confirmed by the crawl: the
 * legacy screen's captured functions carry no {@code fabricsCost}/{@code gsmCalculated}
 * handlers, unlike Booking and BPO. A Request-for-PI schedules delivery of fabric already
 * costed at the BPO stage; it does not re-cost anything, so this service has no
 * {@code CostingService} dependency at all rather than one that happens to go unused.
 *
 * <p>{@code dispo_code}, a legacy planning-system reference field, is deliberately not
 * modelled as its own column — it appeared on exactly this one screen, is not read by any
 * business rule, and fits in {@code remarks} until something actually needs to query on it.
 */
@Service
public class RequestForPiService {

    private static final DocumentType TYPE = DocumentType.REQUEST_FOR_PI;
    private static final DocumentType PARENT_TYPE = DocumentType.BULK_PRODUCTION_ORDER;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final DocumentRevisionService revisions;
    private final ParentLineDrawService parentDraw;
    private final OrgContext context;

    public RequestForPiService(BusinessDocumentRepository repository,
                               DocumentNumberService numbering,
                               DocumentRevisionService revisions,
                               ParentLineDrawService parentDraw,
                               OrgContext context) {
        this.repository = repository;
        this.numbering = numbering;
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
            TYPE, status, from, to, query, pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .orElseThrow(() -> new IllegalArgumentException("Request For PI not found: " + id));
    }

    /** The BPO lines still available to schedule — feeds the "raise" form's line picker. */
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
    public BusinessDocument revise(Long id, String reason) {
        return revisions.revise(get(id), reason);
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

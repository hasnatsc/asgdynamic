package com.asg.fabricerp.fabric.fabricsdelivery;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Fabrics Delivery — the last link in the sales chain, raised against a Delivery Order.
 * The legacy {@code fabricsDelivery} screen's captured functions name this explicitly:
 * {@code dloPopulate}/{@code dloReceive} ({@code dlo} = the Delivery Order prefix) — the
 * only place in the whole crawl where a "populate from parent" function names its parent
 * this unambiguously.
 */
@Service
public class FabricsDeliveryService {

    private static final DocumentType TYPE = DocumentType.FABRICS_DELIVERY;
    private static final DocumentType PARENT_TYPE = DocumentType.DELIVERY_ORDER;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final ParentLineDrawService parentDraw;
    private final DocumentReferences references;
    private final OrgContext context;

    public FabricsDeliveryService(BusinessDocumentRepository repository,
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
            .orElseThrow(() -> new IllegalArgumentException("Fabrics Delivery not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BusinessDocumentColorLine> openDeliveryOrderLines(Long deliveryOrderId) {
        return parentDraw.openLines(parentDraw.loadParent(deliveryOrderId, PARENT_TYPE));
    }

    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        references.resolve(submitted, TYPE);   // before anything is copied or flushed
        return submitted.getId() == null ? create(submitted) : update(submitted);
    }

    private BusinessDocument create(BusinessDocument submitted) {
        BusinessDocument deliveryOrder = parentDraw.loadParent(submitted.getParentDocument(), PARENT_TYPE);

        submitted.setDocumentType(TYPE);
        submitted.setOrganizationId(context.requireOrganizationId());
        submitted.setBusinessUnit(references.currentBusinessUnit());
        submitted.setParentDocument(deliveryOrder);
        submitted.stampMarketingTeam(deliveryOrder.getMarketingTeam());   // ADM-7: the team travels downstream
        submitted.setDocumentNo(numbering.next(TYPE));
        if (submitted.getDocumentDate() == null) {
            submitted.setDocumentDate(LocalDate.now());
        }
        if (submitted.getParty() == null) {
            submitted.setParty(deliveryOrder.getParty());
        }

        parentDraw.draw(deliveryOrder, submitted.getLineGroups());
        parentDraw.save(deliveryOrder);

        submitted.recalculateTotals();
        return repository.save(submitted);
    }

    private BusinessDocument update(BusinessDocument submitted) {
        BusinessDocument target = get(submitted.getId());
        target.assertEditable();

        BusinessDocument deliveryOrder = parentDraw.loadParent(target.getParentDocument(), PARENT_TYPE);
        parentDraw.release(deliveryOrder, target.getLineGroups());

        applyHeader(submitted, target);
        target.setLineGroups(submitted.getLineGroups());

        parentDraw.draw(deliveryOrder, target.getLineGroups());
        parentDraw.save(deliveryOrder);

        target.recalculateTotals();
        return repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();

        BusinessDocument deliveryOrder = parentDraw.loadParent(doc.getParentDocument(), PARENT_TYPE);
        parentDraw.release(deliveryOrder, doc.getLineGroups());
        parentDraw.save(deliveryOrder);

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

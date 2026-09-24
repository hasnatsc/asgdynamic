package com.asg.fabricerp.global.documents;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Raises revision n+1 of a document as a new row, leaving the committed original untouched.
 *
 * <p>Extracted rather than left on {@code BookingService} the moment a second document type
 * needed it: {@code BpoService} calls the same method. Every field this copies —
 * {@code partyId}, currency, dates, remarks, the fabric-specced lines — lives on the generic
 * {@link BusinessDocument} / {@link BusinessDocumentLine}, so nothing here is Booking- or
 * BPO-specific. A third document type needs no changes to this class, only a call to it.
 */
@Service
public class DocumentRevisionService {

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;

    public DocumentRevisionService(BusinessDocumentRepository repository, DocumentNumberService numbering) {
        this.repository = repository;
        this.numbering = numbering;
    }

    @Transactional
    public BusinessDocument revise(BusinessDocument original, String reason) {
        if (!original.getDocumentType().isRevisable()) {
            throw new IllegalStateException(
                "%s documents are not revisable; edit or reverse instead"
                    .formatted(original.getDocumentType().label()));
        }
        if (!original.getStatus().isCommitted()) {
            throw new IllegalStateException(
                "Only a committed document needs a revision; edit %s directly"
                    .formatted(original.getDocumentNo()));
        }

        BusinessDocument revision = new BusinessDocument();
        revision.setDocumentType(original.getDocumentType());
        revision.setOrganizationId(original.getOrganizationId());
        revision.setBusinessUnitId(original.getBusinessUnitId());
        revision.setWarehouseId(original.getWarehouseId());
        revision.setPartyId(original.getPartyId());
        revision.setCurrencyCode(original.getCurrencyCode());
        revision.setExchangeRate(original.getExchangeRate());
        revision.setDocumentDate(LocalDate.now());
        revision.setRequiredDate(original.getRequiredDate());
        revision.setReferenceNo(original.getReferenceNo());
        revision.setRemarks(reason);
        // Upstream link travels with the revision: a revised BPO still traces to the same
        // Booking. revisionOfId is a different axis (previous version of THIS document) and
        // is set below, not here.
        revision.setParentDocumentId(original.getParentDocumentId());
        revision.setDocumentNo(numbering.next(original.getDocumentType()));

        // Lineage always points at the root, so a chain never has to be walked to find it.
        Long rootId = original.getRevisionOfId() != null ? original.getRevisionOfId() : original.getId();
        revision.setRevisionOfId(rootId);
        revision.setRevisionNo(original.getRevisionNo() + 1);

        for (BusinessDocumentLine line : original.getLines()) {
            revision.addLine(copyOf(line));
        }
        revision.recalculateTotals();
        return repository.save(revision);
    }

    /**
     * {@code sourceLineId} travels across the copy — the revision still traces to the same
     * upstream allocation. What does NOT happen here is a fresh {@code fulfil()} call
     * against that source: the original draw already recorded the consumption, and revising
     * the wording or price of a line must not consume the source a second time. A revision
     * that changes <i>how much</i> was drawn is a deliberate reallocation, not implied by
     * raising a revision, and is out of scope until a document type actually needs it.
     */
    private BusinessDocumentLine copyOf(BusinessDocumentLine source) {
        BusinessDocumentLine copy = new BusinessDocumentLine();
        copy.setLineNo(source.getLineNo());
        copy.setSourceLineId(source.getSourceLineId());
        copy.setItemId(source.getItemId());
        copy.setUomId(source.getUomId());
        copy.setFabric(source.getFabric());
        copy.setQuantity(source.getQuantity());
        copy.setRate(source.getRate());
        copy.setRemarks(source.getRemarks());
        // fulfilledQuantity is deliberately not copied: a revision starts unfulfilled
        // against its own row, even though its source line's ledger is untouched.
        return copy;
    }
}

package com.asg.fabricerp.commercial;

import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.supply.SupplyDraws;
import com.asg.fabricerp.supply.SupplyProgress;
import com.asg.fabricerp.supply.SupplyQueries;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Commercial status moves by itself: an export PI is <b>Partial</b> once an approved LC covers part
 * of it and <b>Completed</b> when every line is on an LC; an export LC the same by its approved CIs.
 * A CI is Completed by its final payment ({@link CommercialRecordsService}). An import PI moves by the
 * purchase orders placed on it, which the purchase module refreshes.
 *
 * <p>Only commercial documents are refreshed here: the delivery schedule a PI was raised from keeps
 * the production chain's own progress (by delivery), whatever its PIs do.
 */
@Component
public class CommercialProgress {

    private static final Set<BusinessDocumentStatus> IN_FLIGHT = EnumSet.of(BusinessDocumentStatus.APPROVED,
        BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL, BusinessDocumentStatus.COMPLETED);
    private static final Set<DocumentType> BY_QUANTITY = EnumSet.of(DocumentType.EXPORT_PROFORMA_INVOICE,
        DocumentType.EXPORT_LETTER_OF_CREDIT);

    private final SupplyQueries queries;
    private final SupplyProgress supplyProgress;
    private final BusinessDocumentRepository repository;
    private final EntityManager em;

    public CommercialProgress(SupplyQueries queries, SupplyProgress supplyProgress, BusinessDocumentRepository repository,
                              EntityManager em) {
        this.queries = queries;
        this.supplyProgress = supplyProgress;
        this.repository = repository;
        this.em = em;
    }

    /** Refreshes a document and the commercial documents its lines were raised against. */
    public void refreshUpwards(BusinessDocument doc) {
        if (doc.getDocumentType() == DocumentType.IMPORT_PROFORMA_INVOICE) {
            supplyProgress.refreshUpwards(doc);   // the requisition it buys, by what is ordered
            return;
        }
        Set<Long> done = new HashSet<>();
        Deque<BusinessDocument> todo = new ArrayDeque<>(List.of(doc));
        while (!todo.isEmpty()) {
            BusinessDocument d = todo.poll();
            if (!done.add(d.getId()) || CommercialStep.of(d.getDocumentType()).isEmpty()) continue;
            refresh(d);
            for (BusinessDocumentColorLine l : SupplyDraws.lines(d)) {
                if (l.getSourceColorLine() != null) todo.add(l.getSourceColorLine().getLineGroup().getDocument());
            }
        }
    }

    public void refresh(BusinessDocument doc) {
        if (!BY_QUANTITY.contains(doc.getDocumentType()) || !IN_FLIGHT.contains(doc.getStatus())) return;
        em.flush();
        List<BusinessDocumentColorLine> lines = SupplyDraws.lines(doc);
        Map<Long, BigDecimal> committed = queries.committed(doc.getDocumentType().fulfilledBy(),
            lines.stream().map(BusinessDocumentColorLine::getId).toList());
        BusinessDocumentStatus next = SupplyProgress.byQuantities(lines, committed);
        if (next != doc.getStatus()) {
            doc.progressTo(next);
            repository.save(doc);
        }
    }
}

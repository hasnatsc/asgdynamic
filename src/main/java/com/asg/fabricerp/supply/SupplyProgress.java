package com.asg.fabricerp.supply;

import com.asg.fabricerp.global.documents.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Status moves by itself, read from what later documents have committed rather than typed in: a
 * store requisition is <b>Partial</b> at its first posted issue and <b>Completed</b> when every
 * line is issued or short-closed; a purchase requisition by its approved orders; a purchase order
 * by its posted MRRs; a transfer request by its issues and a transfer issue by its receipts.
 *
 * <p>Drafts do not count - a purchase order is not part-received because someone started an MRR.
 * Closed stays a human decision, and a cancelled receipt moves a completed order back.
 */
@Component
public class SupplyProgress {

    private static final Set<BusinessDocumentStatus> IN_FLIGHT = EnumSet.of(BusinessDocumentStatus.APPROVED,
        BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL, BusinessDocumentStatus.COMPLETED);

    private final SupplyQueries queries;
    private final BusinessDocumentRepository repository;
    private final EntityManager em;

    public SupplyProgress(SupplyQueries queries, BusinessDocumentRepository repository, EntityManager em) {
        this.queries = queries;
        this.repository = repository;
        this.em = em;
    }

    /** Refreshes a document and every document its lines were raised against. */
    public void refreshUpwards(BusinessDocument doc) {
        Set<Long> done = new HashSet<>();
        Deque<BusinessDocument> todo = new ArrayDeque<>(List.of(doc));
        while (!todo.isEmpty()) {
            BusinessDocument d = todo.poll();
            if (!done.add(d.getId())) continue;
            refresh(d);
            for (BusinessDocumentColorLine l : SupplyDraws.lines(d)) {
                if (l.getSourceColorLine() != null) todo.add(l.getSourceColorLine().getLineGroup().getDocument());
            }
        }
    }

    /** Sets one document's progress from its lines. */
    public void refresh(BusinessDocument doc) {
        DocumentType principal = doc.getDocumentType().fulfilledBy();
        if (principal == null || !IN_FLIGHT.contains(doc.getStatus())) return;
        em.flush();   // the figures below are read with SQL, which sees only what has been written
        List<BusinessDocumentColorLine> lines = SupplyDraws.lines(doc);
        Map<Long, BigDecimal> committed = queries.committed(principal, lines.stream().map(BusinessDocumentColorLine::getId).toList());
        BusinessDocumentStatus next = byQuantities(lines, committed);
        if (next != doc.getStatus()) {
            doc.progressTo(next);
            repository.save(doc);
        }
    }

    static BusinessDocumentStatus byQuantities(List<BusinessDocumentColorLine> lines, Map<Long, BigDecimal> done) {
        boolean any = false, all = !lines.isEmpty();
        for (BusinessDocumentColorLine l : lines) {
            BigDecimal d = done.getOrDefault(l.getId(), BigDecimal.ZERO);
            any |= d.signum() > 0;
            all &= l.isShortClosed() || d.compareTo(l.getQuantity()) >= 0;
        }
        if (all) return BusinessDocumentStatus.COMPLETED;
        return any ? BusinessDocumentStatus.PARTIAL : BusinessDocumentStatus.APPROVED;
    }
}

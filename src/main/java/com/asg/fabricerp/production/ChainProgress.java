package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Status moves by itself, read from the documents rather than typed in:
 * <ul>
 *   <li>a production order is <b>Processing</b> once a Weaving WO is approved, <b>Partial</b> at its
 *       first delivery, <b>Completed</b> when every line is delivered within tolerance or short-closed;</li>
 *   <li>work orders, schedules and delivery orders are <b>Partial</b> once anything is received or
 *       delivered against them and <b>Completed</b> when every line is, or is short-closed;</li>
 *   <li>a Booking is <b>Partial</b> at its first delivery and <b>Completed</b> when it is fully on
 *       production orders and all of them are complete.</li>
 * </ul>
 * Closed stays a human decision, and a cancelled delivery moves a completed order back.
 */
@Component
public class ChainProgress {

    private static final Set<BusinessDocumentStatus> IN_FLIGHT = EnumSet.of(BusinessDocumentStatus.APPROVED,
        BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL, BusinessDocumentStatus.COMPLETED);

    private final ChainSupport chain;
    private final ChainQueries queries;
    private final BusinessDocumentRepository repository;
    private final jakarta.persistence.EntityManager em;

    public ChainProgress(ChainSupport chain, ChainQueries queries, BusinessDocumentRepository repository,
                         jakarta.persistence.EntityManager em) {
        this.chain = chain;
        this.queries = queries;
        this.repository = repository;
        this.em = em;
    }

    /** Refreshes a document and everything up the chain whose progress it can move. */
    public void refreshUpwards(BusinessDocument doc) {
        Set<Long> done = new HashSet<>();
        Deque<BusinessDocument> todo = new ArrayDeque<>(List.of(doc));
        while (!todo.isEmpty()) {
            BusinessDocument d = todo.poll();
            if (!done.add(d.getId())) continue;
            refresh(d);
            // Parents: every document a line of d was drawn from (a schedule may span orders).
            for (BusinessDocumentLineGroup g : d.getLineGroups()) {
                for (BusinessDocumentColorLine l : g.getColorLines()) {
                    BusinessDocumentLineGroup parentGroup = chain.sourceGroup(l);
                    if (parentGroup != null) todo.add(parentGroup.getDocument());
                }
            }
        }
    }

    /** Sets one document's progress from its lines. */
    public void refresh(BusinessDocument doc) {
        if (!IN_FLIGHT.contains(doc.getStatus())) return;
        em.flush();   // the figures below are read with SQL, which sees only what has been written
        BusinessDocumentStatus next = switch (doc.getDocumentType()) {
            case BULK_PRODUCTION_ORDER -> productionOrder(doc);
            case BOOKING -> booking(doc);
            case WEAVING_WORK_ORDER, PROCESSING_WORK_ORDER, DELIVERY_ORDER -> byPrincipalStream(doc);
            case REQUEST_FOR_PI -> schedule(doc);
            default -> doc.getStatus();
        };
        if (next != doc.getStatus()) {
            doc.progressTo(next);
            repository.save(doc);
        }
    }

    private BusinessDocumentStatus productionOrder(BusinessDocument bpo) {
        List<BusinessDocumentColorLine> lines = lines(bpo);
        Map<Long, BigDecimal> delivered = queries.deliveredByBpoLine(ids(lines));
        boolean any = false, all = !lines.isEmpty();
        for (BusinessDocumentColorLine l : lines) {
            BigDecimal d = delivered.getOrDefault(l.getId(), BigDecimal.ZERO);
            any |= d.signum() > 0;
            BigDecimal tol = l.getLineGroup().getRoute().getDeliveryTolerancePct();
            BigDecimal enough = l.getQuantity().multiply(BigDecimal.ONE.subtract(tol.movePointLeft(2)));
            all &= l.isShortClosed() || d.compareTo(enough) >= 0;
        }
        if (all && any) return BusinessDocumentStatus.COMPLETED;
        if (all && lines.stream().allMatch(BusinessDocumentColorLine::isShortClosed)) return BusinessDocumentStatus.COMPLETED;
        if (any) return BusinessDocumentStatus.PARTIAL;
        return queries.committedChildren(bpo.getId(), DocumentType.WEAVING_WORK_ORDER.name()) > 0
            ? BusinessDocumentStatus.PROCESSING : BusinessDocumentStatus.APPROVED;
    }

    private BusinessDocumentStatus schedule(BusinessDocument rpi) {
        List<BusinessDocumentColorLine> lines = lines(rpi);
        return byQuantities(rpi, lines, queries.deliveredByScheduleLine(ids(lines)));
    }

    private BusinessDocumentStatus byPrincipalStream(BusinessDocument doc) {
        if (doc.isBatchClosed()) return BusinessDocumentStatus.COMPLETED;
        String stream = ChainStep.principalChildOf(doc.getDocumentType()).orElseThrow().stream();
        List<BusinessDocumentColorLine> lines = lines(doc);
        Map<Long, BigDecimal> drawn = new HashMap<>();
        chain.ledger().streams(SourceKind.COLOUR, ids(lines))
            .forEach((id, streams) -> drawn.put(id, streams.getOrDefault(stream, BigDecimal.ZERO)));
        return byQuantities(doc, lines, drawn);
    }

    private static BusinessDocumentStatus byQuantities(BusinessDocument doc, List<BusinessDocumentColorLine> lines,
                                                       Map<Long, BigDecimal> done) {
        boolean any = false, all = !lines.isEmpty();
        for (BusinessDocumentColorLine l : lines) {
            BigDecimal d = done.getOrDefault(l.getId(), BigDecimal.ZERO);
            any |= d.signum() > 0;
            all &= l.isShortClosed() || d.compareTo(l.getQuantity()) >= 0;
        }
        if (all) return BusinessDocumentStatus.COMPLETED;
        return any ? BusinessDocumentStatus.PARTIAL : BusinessDocumentStatus.APPROVED;
    }

    private BusinessDocumentStatus booking(BusinessDocument booking) {
        List<BusinessDocument> orders = repository.childrenOf(booking.getId(), booking.getOrganizationId()).stream()
            .filter(d -> d.getDocumentType() == DocumentType.BULK_PRODUCTION_ORDER)
            .filter(d -> d.getStatus().isCommitted())
            .toList();
        boolean anyDelivered = orders.stream().anyMatch(o -> o.getStatus() == BusinessDocumentStatus.PARTIAL
            || o.getStatus() == BusinessDocumentStatus.COMPLETED || o.getStatus() == BusinessDocumentStatus.CLOSED);
        List<BusinessDocumentColorLine> lines = lines(booking);
        Map<Long, Map<String, BigDecimal>> streams = chain.ledger().streams(SourceKind.COLOUR, ids(lines));
        boolean fullyOrdered = !lines.isEmpty() && lines.stream().allMatch(l -> streams.getOrDefault(l.getId(), Map.of())
            .getOrDefault(ChainStep.BPO.stream(), BigDecimal.ZERO).compareTo(l.getQuantity()) >= 0);
        boolean allComplete = !orders.isEmpty() && orders.stream().allMatch(o ->
            o.getStatus() == BusinessDocumentStatus.COMPLETED || o.getStatus() == BusinessDocumentStatus.CLOSED);
        if (fullyOrdered && allComplete) return BusinessDocumentStatus.COMPLETED;
        return anyDelivered ? BusinessDocumentStatus.PARTIAL : BusinessDocumentStatus.APPROVED;
    }

    private static List<BusinessDocumentColorLine> lines(BusinessDocument doc) {
        return doc.getLineGroups().stream().flatMap(g -> g.getColorLines().stream()).toList();
    }

    private static List<Long> ids(List<BusinessDocumentColorLine> lines) {
        return lines.stream().map(BusinessDocumentColorLine::getId).toList();
    }
}

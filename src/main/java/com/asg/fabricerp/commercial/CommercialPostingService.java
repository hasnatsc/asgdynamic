package com.asg.fabricerp.commercial;

import com.asg.fabricerp.accounts.PostingEvent;
import com.asg.fabricerp.approval.ApprovalAction;
import com.asg.fabricerp.approval.ApprovalRequestRepository;
import com.asg.fabricerp.approval.ApprovalService;
import com.asg.fabricerp.commercial.CommercialTerms.EventKind;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentRepository;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;

/**
 * Cancelling and closing commercial documents. A draft simply gives back what it drew. An approved
 * PI or LC that others were raised against is not cancelled - amend it instead; an approved CI
 * is cancelled only before anything is realized on it, and its invoice is reversed in the ledger.
 */
@Service
public class CommercialPostingService {

    private final CommercialDocumentService documents;
    private final CommercialDraws draws;
    private final CommercialProgress progress;
    private final CommercialAccounting accounting;
    private final CommercialEventRepository events;
    private final ApprovalService approvals;
    private final ApprovalRequestRepository requests;
    private final BusinessDocumentRepository repository;

    public CommercialPostingService(CommercialDocumentService documents, CommercialDraws draws, CommercialProgress progress,
                                    CommercialAccounting accounting, CommercialEventRepository events, ApprovalService approvals,
                                    ApprovalRequestRepository requests, BusinessDocumentRepository repository) {
        this.documents = documents;
        this.draws = draws;
        this.progress = progress;
        this.accounting = accounting;
        this.events = events;
        this.approvals = approvals;
        this.requests = requests;
        this.repository = repository;
    }

    @Transactional
    public BusinessDocument cancel(CommercialStep step, Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why it is cancelled");
        BusinessDocument doc = documents.get(step, id);
        BusinessDocumentStatus from = doc.getStatus();
        switch (from) {
            case CANCELLED, CLOSED -> throw new IllegalStateException("%s is already %s".formatted(doc.getDocumentNo(), from.label().toLowerCase()));
            case SUBMITTED -> throw new IllegalStateException("%s is awaiting approval; have it returned or rejected first".formatted(doc.getDocumentNo()));
            case COMPLETED -> throw new IllegalStateException("%s is completed; close it instead".formatted(doc.getDocumentNo()));
            default -> { }
        }
        requests.findByDocumentIdAndPendingTrue(doc.getId()).ifPresent(r -> {
            throw new IllegalStateException(doc.getDocumentNo() + " has an approval in progress");
        });
        String why = "Cancelled: " + reason.strip();
        String note = null;
        boolean committed = from.isCommitted();
        if (committed) {
            refuseIfDrawnOn(step, doc);
            if (step == CommercialStep.ECI) {
                if (!events.findByDocumentIdAndKindOrderByEventDateAscIdAsc(doc.getId(), EventKind.REALIZATION).isEmpty()) {
                    throw new IllegalStateException("%s has realization recorded; take it back first".formatted(doc.getDocumentNo()));
                }
                note = accounting.reverse(doc, PostingEvent.INVOICE, why);
            }
        }
        if (draws.holdsDraws(doc)) draws.releaseAll(step, doc);
        doc.transitionTo(BusinessDocumentStatus.CANCELLED);
        doc.setRemarks(doc.getRemarks() == null ? why : (doc.getRemarks() + "\n" + why));
        repository.save(doc);
        approvals.record(doc, ApprovalAction.CANCELLED, from, note == null ? reason.strip() : reason.strip() + ". " + note);
        if (committed) progress.refreshUpwards(doc);
        return doc;
    }

    /** Refuses to cancel a document others are still raised against (an LC on a PI, a CI on an LC, a PO on an import PI). */
    private void refuseIfDrawnOn(CommercialStep step, BusinessDocument doc) {
        Set<String> taken = new TreeSet<>();
        draws.streams(doc).values().forEach(streams -> streams.forEach((stream, q) -> {
            if (q.signum() > 0) taken.add(label(stream));
        }));
        if (!taken.isEmpty()) {
            throw new IllegalStateException("%s has %s raised against it; %s".formatted(doc.getDocumentNo(), String.join(", ", taken),
                step.isRevisable() ? "amend it instead, or cancel those first" : "cancel those first"));
        }
    }

    private static String label(String stream) {
        DocumentType type = DocumentType.valueOf(stream);
        return CommercialViews.labelOf(type).toLowerCase() + "s";
    }

    @Transactional
    public BusinessDocument close(CommercialStep step, Long id, String remarks) {
        BusinessDocument doc = documents.get(step, id);
        BusinessDocumentStatus from = doc.getStatus();
        if (from != BusinessDocumentStatus.COMPLETED) {
            throw new IllegalStateException("%s is %s; only a completed document is closed".formatted(doc.getDocumentNo(), from.label().toLowerCase()));
        }
        doc.transitionTo(BusinessDocumentStatus.CLOSED);
        repository.save(doc);
        approvals.record(doc, ApprovalAction.CLOSED, from, remarks == null || remarks.isBlank() ? null : remarks.strip());
        return doc;
    }
}

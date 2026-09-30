package com.asg.fabricerp.supply;

import com.asg.fabricerp.approval.ApprovalAction;
import com.asg.fabricerp.approval.ApprovalRequestRepository;
import com.asg.fabricerp.approval.ApprovalService;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.production.FabricStockService;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asg.fabricerp.supply.SupplyDraws.lineName;

/**
 * What happens to a purchase or store document after it is saved: MRRs and purchase returns are
 * posted to the stock ledger in one step, every other store document once it is approved; anything may be cancelled with a reason (a posted document by
 * exact reversing rows, refused if what it brought in has gone on); an approved requisition's or
 * order's line may be short-closed; a completed document is closed by hand.
 */
@Service
public class SupplyPostingService {

    private final SupplyDocumentService documents;
    private final SupplyDraws draws;
    private final SupplyProgress progress;
    private final SupplyAccounting accounting;
    private final SupplyStockWriter writer;
    private final ItemStockService stock;
    private final FabricStockService fabric;
    private final InventoryPeriodService periods;
    private final ApprovalService approvals;
    private final ApprovalRequestRepository requests;
    private final BusinessDocumentRepository repository;
    private final OrgContext context;
    private final EntityManager em;

    public SupplyPostingService(SupplyDocumentService documents, SupplyDraws draws, SupplyProgress progress,
                                SupplyAccounting accounting, SupplyStockWriter writer, ItemStockService stock, FabricStockService fabric,
                                InventoryPeriodService periods, ApprovalService approvals, ApprovalRequestRepository requests,
                                BusinessDocumentRepository repository, OrgContext context, EntityManager em) {
        this.documents = documents;
        this.draws = draws;
        this.progress = progress;
        this.accounting = accounting;
        this.writer = writer;
        this.stock = stock;
        this.fabric = fabric;
        this.periods = periods;
        this.approvals = approvals;
        this.requests = requests;
        this.repository = repository;
        this.context = context;
        this.em = em;
    }

    // ------------------------------------------------------------------------------------ post

    /**
     * Posts a store document - an MRR or purchase return as a draft, anything else once its approvers
     * have signed it: its stock moves are written (a requisition or transfer request is released to
     * the store) and it is final - cancel it to undo.
     */
    @Transactional
    public BusinessDocument post(SupplyStep step, Long id) {
        if (!step.isPosted()) throw new IllegalStateException(step.plural() + " are approved, not posted");
        BusinessDocument doc = documents.get(step, id);
        if (doc.getStatus() != step.postsFrom()) {
            throw new IllegalStateException(step.postsFrom() == BusinessDocumentStatus.DRAFT
                ? "%s is %s; only a draft is posted".formatted(doc.getDocumentNo(), doc.getStatus().label().toLowerCase())
                : "%s is %s; only an approved %s is posted - submit it for approval first"
                    .formatted(doc.getDocumentNo(), doc.getStatus().label().toLowerCase(), step.label().toLowerCase()));
        }
        if (SupplyDraws.lines(doc).isEmpty()) throw new IllegalStateException(doc.getDocumentNo() + " has no lines to post");
        if (step.movesStock()) writer.write(step, doc);

        BusinessDocumentStatus from = doc.getStatus();
        if (from == BusinessDocumentStatus.DRAFT) doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        doc.transitionTo(BusinessDocumentStatus.APPROVED);
        repository.save(doc);
        approvals.record(doc, ApprovalAction.POSTED, from, accounting.post(step, doc));
        progress.refreshUpwards(doc);
        return doc;
    }

    // ---------------------------------------------------------------------------------- cancel

    /**
     * Cancels a document with a reason. A draft simply gives back what it drew. A posted one writes
     * exact reversing rows - refused if what it brought in has since been issued or sent on. An
     * approved requisition or order that others were raised against is short-closed instead.
     */
    @Transactional
    public BusinessDocument cancel(SupplyStep step, Long id, String reason) {
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
            if (step.movesStock()) {
                LocalDate today = LocalDate.now();
                periods.requireOpen(today);
                if (step.lines() == SupplyStep.Lines.FABRIC_LOT) {
                    fabric.reverseDocument(context.requireOrganizationId(), doc.getId(), why, doc.getDocumentNo(), context.username());
                } else {
                    stock.reverseDocument(context.requireOrganizationId(), doc.getId(), today, why, doc.getDocumentNo(), context.username());
                }
                note = accounting.reverse(doc, why);
            }
        }
        if (draws.holdsDraws(doc)) draws.releaseAll(step, doc);
        doc.transitionTo(BusinessDocumentStatus.CANCELLED);
        doc.setRemarks(append(doc.getRemarks(), why));
        repository.save(doc);
        approvals.record(doc, ApprovalAction.CANCELLED, from, note == null ? reason.strip() : reason.strip() + ". " + note);
        if (committed) progress.refreshUpwards(doc);
        return doc;
    }

    /** Refuses to cancel a document later documents are still raised against (drafts included). */
    private void refuseIfDrawnOn(SupplyStep step, BusinessDocument doc) {
        Set<String> taken = new TreeSet<>();
        draws.streams(doc).values().forEach(streams -> streams.forEach((stream, q) -> {
            if (q.signum() > 0) taken.add(SupplyStep.of(DocumentType.valueOf(stream)).map(s -> s.plural().toLowerCase()).orElse(stream));
        }));
        if (taken.isEmpty()) return;
        throw new IllegalStateException(step.movesStock()
            ? "%s has %s raised against it; cancel or delete those first".formatted(doc.getDocumentNo(), String.join(", ", taken))
            : "%s has %s raised against it and cannot be cancelled. Short-close its lines instead."
                .formatted(doc.getDocumentNo(), String.join(", ", taken)));
    }

    // ----------------------------------------------------------------------------- short-close

    /**
     * Gives up an approved line's remaining balance, with a reason: nothing more may be raised
     * against it, and what it held on its own parent is given back. Nothing is deleted - the
     * shortfall stays on the document.
     */
    @Transactional
    public BusinessDocument shortClose(SupplyStep step, Long lineId, String reason) {
        if (step.movesStock()) throw new IllegalStateException(step.plural() + " are cancelled, not short-closed");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the line is short-closed");
        BusinessDocumentColorLine line = em.find(BusinessDocumentColorLine.class, lineId);
        if (line == null) throw new IllegalArgumentException("Line not found: " + lineId);
        BusinessDocument doc = documents.get(step, line.getLineGroup().getDocument().getId());
        if (!EnumSet.of(BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL)
                .contains(doc.getStatus())) {
            throw new IllegalStateException("Only an approved, open %s's lines are short-closed".formatted(step.label().toLowerCase()));
        }
        if (line.isShortClosed()) throw new IllegalStateException(lineName(line) + " is already short-closed");
        DocumentType principal = step.type().fulfilledBy();
        BigDecimal done = principal == null ? BigDecimal.ZERO
            : draws.ledger().drawn(SourceKind.COLOUR, line.getId(), principal.name());
        BigDecimal balance = line.getQuantity().subtract(done).max(BigDecimal.ZERO);
        line.shortClose(balance, reason.strip());
        if (balance.signum() > 0 && line.getSourceColorLine() != null) {
            draws.ledger().release(SourceKind.COLOUR, line.getSourceColorLine().getId(), step.stream(), balance,
                SupplyDraws.typeOf(line.getSourceColorLine()));
        }
        repository.save(doc);
        approvals.record(doc, ApprovalAction.SHORT_CLOSED, doc.getStatus(), "%s: %s".formatted(lineName(line), reason.strip()));
        progress.refreshUpwards(doc);
        return doc;
    }

    /** Closes a completed document by hand. */
    @Transactional
    public BusinessDocument close(SupplyStep step, Long id, String remarks) {
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

    private static String append(String remarks, String note) {
        String next = remarks == null || remarks.isBlank() ? note : remarks + "\n" + note;
        return next.length() <= 1000 ? next : next.substring(next.length() - 1000);
    }
}

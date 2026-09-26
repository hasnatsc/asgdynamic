package com.asg.fabricerp.production;

import com.asg.fabricerp.approval.ApprovalAction;
import com.asg.fabricerp.approval.ApprovalRequestRepository;
import com.asg.fabricerp.approval.ApprovalService;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.production.ChainSupport.Anchor;
import com.asg.fabricerp.production.FabricStockService.LotKey;
import com.asg.fabricerp.production.FabricStockService.Move;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.asg.fabricerp.production.ChainDocumentService.lineName;
import static com.asg.fabricerp.production.ChainSupport.qty;

/**
 * What happens to a chain document after it is saved: store documents are posted to the stock
 * ledger in one step; anything may be cancelled with a reason (a posted document by exact
 * reversing ledger rows); a committed line's balance may be short-closed; a dyeing batch is
 * closed with its loss measured; a completed order is closed by hand.
 */
@Service
public class ChainPostingService {

    private final ChainDocumentService documents;
    private final ChainSupport chain;
    private final ChainQueries queries;
    private final ChainProgress progress;
    private final FabricStockService stock;
    private final ApprovalService approvals;
    private final ApprovalRequestRepository requests;
    private final BusinessDocumentRepository repository;
    private final OrgContext context;
    private final EntityManager em;

    public ChainPostingService(ChainDocumentService documents, ChainSupport chain, ChainQueries queries,
                               ChainProgress progress, FabricStockService stock, ApprovalService approvals,
                               ApprovalRequestRepository requests, BusinessDocumentRepository repository,
                               OrgContext context, EntityManager em) {
        this.documents = documents;
        this.chain = chain;
        this.queries = queries;
        this.progress = progress;
        this.stock = stock;
        this.approvals = approvals;
        this.requests = requests;
        this.repository = repository;
        this.context = context;
        this.em = em;
    }

    // ------------------------------------------------------------------------------------ post

    /** Posts a store document: its stock moves are written and it is final - cancel it to undo. */
    @Transactional
    public BusinessDocument post(ChainStep step, Long id) {
        if (!step.isPosting()) throw new IllegalStateException(step.plural() + " are approved, not posted");
        BusinessDocument doc = documents.get(step, id);
        if (doc.getStatus() != BusinessDocumentStatus.DRAFT) {
            throw new IllegalStateException("%s is %s; only a draft is posted".formatted(doc.getDocumentNo(), doc.getStatus().label().toLowerCase()));
        }
        if (doc.getLineGroups().stream().allMatch(g -> g.getColorLines().isEmpty())) {
            throw new IllegalStateException(doc.getDocumentNo() + " has no lines to post");
        }
        if (doc.getWarehouse() == null) doc.setWarehouse(documents.defaultStore(step, doc));
        Warehouse store = doc.getWarehouse();
        if (store == null) {
            throw new IllegalStateException(("Choose the %s store on %s before posting it: edit the draft and pick it "
                + "(your own store does not hold %s fabric, or you have none).").formatted(
                    step == ChainStep.FFR ? "finished" : "greige", doc.getDocumentNo(), step == ChainStep.FFR ? "finished" : "greige"));
        }

        Long orgId = context.requireOrganizationId();
        String user = context.username();
        for (BusinessDocumentLineGroup g : doc.getLineGroups()) {
            Long uomId = g.getUom() == null ? null : g.getUom().getId();
            for (BusinessDocumentColorLine line : g.getColorLines()) {
                if (line.getQuantity().signum() <= 0) continue;
                int rolls = line.getRolls() == null ? 0 : line.getRolls();
                String what = "%s, %s".formatted(doc.getDocumentNo(), lineName(line));
                switch (step) {
                    case GR -> {
                        Anchor a = chain.anchor(line);
                        Long colour = a.route().getGreigeKey() == GreigeKey.COLOUR && a.line() != null ? a.line().getId() : null;
                        long lot = stock.lotFor(orgId, new LotKey(FabricStockService.GREIGE, a.bpo().getId(), a.group().getId(),
                            colour, null, null, null), user);
                        line.setFabricLotId(lot);
                        stock.receive(orgId, new Move(store.getId(), lot, line.getQuantity(), rolls, uomId, "GREIGE_RECEIVE",
                            doc.getId(), line.getId()), user);
                    }
                    case GI -> {
                        if (line.getFabricLotId() == null) throw new IllegalStateException("Pick the lot to issue for " + what);
                        Anchor a = chain.anchor(line.getSourceColorLine());
                        if (!queries.lotFits(line.getFabricLotId(), ChainDocumentService.lotRule(step, doc.getProcessKind(), a))) {
                            throw new IllegalStateException("The lot picked for %s is not stock of that order line".formatted(what));
                        }
                        stock.issue(orgId, new Move(store.getId(), line.getFabricLotId(), line.getQuantity(), rolls, uomId,
                            "GREIGE_ISSUE", doc.getId(), line.getId()), what, user);
                    }
                    case FFR -> {
                        Anchor a = chain.anchor(line);
                        if (a.line() == null) throw new IllegalStateException(what + " does not trace to a production order colour");
                        long lot = stock.lotFor(orgId, new LotKey(FabricStockService.FINISHED, a.bpo().getId(), a.group().getId(),
                            a.line().getId(), line.getDyeLot(), line.getShade(), line.getGrade() == null ? "A" : line.getGrade()), user);
                        line.setFabricLotId(lot);
                        stock.receive(orgId, new Move(store.getId(), lot, line.getQuantity(), rolls, uomId, "FINISHED_RECEIVE",
                            doc.getId(), line.getId()), user);
                    }
                    case FD -> {
                        BusinessDocumentColorLine doLine = line.getSourceColorLine();
                        if (doLine.getFabricLotId() == null) throw new IllegalStateException(what + ": its delivery order line has no lot");
                        stock.deliverReserved(orgId, doLine.getId(), new Move(store.getId(), doLine.getFabricLotId(),
                            line.getQuantity(), rolls, uomId, "DELIVERY", doc.getId(), line.getId()), what, user);
                    }
                    default -> throw new IllegalStateException(step.label() + " has no stock posting");
                }
            }
        }
        BusinessDocumentStatus from = doc.getStatus();
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        doc.transitionTo(BusinessDocumentStatus.APPROVED);
        repository.save(doc);
        approvals.record(doc, ApprovalAction.POSTED, from, null);
        progress.refreshUpwards(doc);
        return doc;
    }

    // ---------------------------------------------------------------------------------- cancel

    /**
     * Cancels a document with a reason. A draft simply gives back what it drew. A posted store
     * document writes exact reversing ledger rows - refused if its stock has already gone on. An
     * approved order that others were raised against is short-closed instead.
     */
    @Transactional
    public BusinessDocument cancel(ChainStep step, Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why it is cancelled");
        BusinessDocument doc = documents.get(step, id);
        BusinessDocumentStatus from = doc.getStatus();
        switch (from) {
            case CANCELLED, CLOSED -> throw new IllegalStateException("%s is already %s".formatted(doc.getDocumentNo(), from.label().toLowerCase()));
            case SUBMITTED -> throw new IllegalStateException("%s is awaiting approval; have it returned or rejected first".formatted(doc.getDocumentNo()));
            case COMPLETED -> throw new IllegalStateException("%s is completed; close it instead".formatted(doc.getDocumentNo()));
            default -> { }
        }
        boolean committed = from.isCommitted();
        if (committed && step.isPosting()) {
            if (step == ChainStep.GI) refuseIfFinishedAgainstIssue(doc);
            List<FabricStockService.Reversed> reversed = stock.reverseDocument(context.requireOrganizationId(), doc.getId(),
                "Cancelled: " + reason.strip(), doc.getDocumentNo(), context.username());
            if (step == ChainStep.FD) restoreReservations(doc, reversed);
        } else if (committed) {
            refuseIfDrawnOn(doc);
            if (step == ChainStep.DO) {
                for (BusinessDocumentColorLine l : lines(doc)) stock.releaseReservation(l.getId());
            }
        }
        if (chain.holdsDraws(doc)) chain.releaseAll(step, doc);
        requests.findByDocumentIdAndPendingTrue(doc.getId()).ifPresent(r -> {
            throw new IllegalStateException(doc.getDocumentNo() + " has an approval in progress");
        });
        doc.transitionTo(BusinessDocumentStatus.CANCELLED);
        doc.setRemarks(append(doc.getRemarks(), "Cancelled: " + reason.strip()));
        repository.save(doc);
        approvals.record(doc, ApprovalAction.CANCELLED, from, reason.strip());
        if (committed) progress.refreshUpwards(doc);
        return doc;
    }

    private void refuseIfDrawnOn(BusinessDocument doc) {
        List<BusinessDocumentColorLine> lines = lines(doc);
        Map<Long, Map<String, BigDecimal>> colour = chain.ledger().streams(SourceKind.COLOUR,
            lines.stream().map(BusinessDocumentColorLine::getId).toList());
        Map<Long, Map<String, BigDecimal>> group = chain.ledger().streams(SourceKind.GROUP,
            doc.getLineGroups().stream().map(BusinessDocumentLineGroup::getId).toList());
        List<String> taken = new ArrayList<>();
        colour.values().forEach(s -> s.forEach((stream, q) -> { if (q.signum() > 0) taken.add(streamLabel(stream)); }));
        group.values().forEach(s -> s.forEach((stream, q) -> { if (q.signum() > 0) taken.add(streamLabel(stream)); }));
        if (!taken.isEmpty()) {
            throw new IllegalStateException("%s has %s raised against it and cannot be cancelled. Short-close its lines instead."
                .formatted(doc.getDocumentNo(), String.join(", ", new TreeSet<>(taken))));
        }
    }

    private void refuseIfFinishedAgainstIssue(BusinessDocument gi) {
        for (BusinessDocumentColorLine l : lines(gi)) {
            BusinessDocumentColorLine pwoLine = l.getSourceColorLine();
            BigDecimal issued = chain.ledger().drawn(SourceKind.COLOUR, pwoLine.getId(), ChainStep.GI.stream());
            BigDecimal finished = chain.ledger().drawn(SourceKind.COLOUR, pwoLine.getId(), ChainStep.FFR.stream());
            if (finished.compareTo(issued.subtract(l.getQuantity())) > 0) {
                throw new IllegalStateException(("%s cannot be cancelled: %s finished is already received against %s, "
                    + "more than would be left issued. Cancel that finished receive first.")
                    .formatted(gi.getDocumentNo(), qty(finished), lineName(pwoLine)));
            }
        }
    }

    /** A cancelled delivery puts its fabric back on hold for its delivery order line, if that line is still open. */
    private void restoreReservations(BusinessDocument fd, List<FabricStockService.Reversed> reversed) {
        Map<Long, BusinessDocumentColorLine> fdLines = new HashMap<>();
        for (BusinessDocumentColorLine l : lines(fd)) fdLines.put(l.getId(), l);
        for (FabricStockService.Reversed r : reversed) {
            BusinessDocumentColorLine fdLine = fdLines.get(r.lineId());
            if (fdLine == null) continue;
            BusinessDocumentColorLine doLine = fdLine.getSourceColorLine();
            BusinessDocument order = doLine.getLineGroup().getDocument();
            if (doLine.isShortClosed() || order.getStatus() == BusinessDocumentStatus.CANCELLED
                || order.getStatus() == BusinessDocumentStatus.CLOSED) continue;
            stock.reserve(context.requireOrganizationId(), doLine.getId(), r.warehouseId(), r.lotId(), r.quantity().abs(),
                order.getDocumentNo() + ", " + lineName(doLine));
        }
    }

    // ----------------------------------------------------------------------------- short-close

    /**
     * Gives up a committed line's balance, with a reason: nothing more may be drawn on it, what it
     * held upstream is given back (so a top-up can be raised), and a delivery order line's
     * reservation is released. Nothing is deleted - the shortfall stays on the order.
     */
    @Transactional
    public BusinessDocument shortClose(ChainStep step, Long lineId, String reason) {
        if (step.isPosting()) throw new IllegalStateException(step.plural() + " are cancelled, not short-closed");
        BusinessDocumentColorLine line = em.find(BusinessDocumentColorLine.class, lineId);
        if (line == null) throw new IllegalArgumentException("Line not found: " + lineId);
        BusinessDocument doc = documents.get(step, line.getLineGroup().getDocument().getId());
        if (!EnumSet.of(BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL)
                .contains(doc.getStatus())) {
            throw new IllegalStateException("Only an approved, open %s's lines are short-closed".formatted(step.label().toLowerCase()));
        }
        closeLine(step, doc, line, reason);
        repository.save(doc);
        approvals.record(doc, ApprovalAction.SHORT_CLOSED, doc.getStatus(), "%s: %s".formatted(lineName(line), reason.strip()));
        progress.refreshUpwards(doc);
        return doc;
    }

    private void closeLine(ChainStep step, BusinessDocument doc, BusinessDocumentColorLine line, String reason) {
        String principal = ChainStep.principalChildOf(step.type()).map(ChainStep::stream).orElse(null);
        BigDecimal done = principal == null ? BigDecimal.ZERO : chain.ledger().drawn(SourceKind.COLOUR, line.getId(), principal);
        BigDecimal balance = line.getQuantity().subtract(done).max(BigDecimal.ZERO);
        line.shortClose(balance, reason);
        if (step != ChainStep.BPO && balance.signum() > 0 && chain.sourceKind(line) != null) {
            chain.ledger().release(chain.sourceKind(line), chain.sourceId(line), DrawCaps.stream(step, doc.getProcessKind()),
                balance, step.parentType());
        }
        if (step == ChainStep.DO) stock.releaseReservation(line.getId());
    }

    /**
     * Closes a dyeing batch: every open line is short-closed at what was received, and the greige
     * issued but not returned as finished is its measured process loss.
     */
    @Transactional
    public BusinessDocument closeBatch(Long id, String remarks) {
        BusinessDocument doc = documents.get(ChainStep.PWO, id);
        if (doc.isBatchClosed()) throw new IllegalStateException(doc.getDocumentNo() + " is already closed");
        if (!doc.getStatus().isCommitted() || doc.getStatus() == BusinessDocumentStatus.CLOSED) {
            throw new IllegalStateException("Only an approved dyeing work order is closed");
        }
        BigDecimal issued = BigDecimal.ZERO, finished = BigDecimal.ZERO;
        for (BusinessDocumentColorLine l : lines(doc)) {
            issued = issued.add(chain.ledger().drawn(SourceKind.COLOUR, l.getId(), ChainStep.GI.stream()));
            finished = finished.add(chain.ledger().drawn(SourceKind.COLOUR, l.getId(), ChainStep.FFR.stream()));
            if (!l.isShortClosed()) closeLine(ChainStep.PWO, doc, l, "Batch closed");
        }
        doc.closeBatch();
        BigDecimal loss = issued.subtract(finished).max(BigDecimal.ZERO);
        String note = "Batch closed: %s greige issued, %s finished received, %s process loss%s".formatted(qty(issued), qty(finished),
            qty(loss), issued.signum() > 0 ? " (" + loss.multiply(BigDecimal.valueOf(100)).divide(issued, 2, java.math.RoundingMode.HALF_UP) + " %)" : "");
        BusinessDocumentStatus from = doc.getStatus();
        repository.save(doc);
        approvals.record(doc, ApprovalAction.CLOSED, from, remarks == null || remarks.isBlank() ? note : note + ". " + remarks.strip());
        progress.refreshUpwards(doc);
        return doc;
    }

    /** Closes a completed document by hand, once commercial settlement is done. */
    @Transactional
    public BusinessDocument close(ChainStep step, Long id, String remarks) {
        BusinessDocument doc = documents.get(step, id);
        BusinessDocumentStatus from = doc.getStatus();
        if (from != BusinessDocumentStatus.COMPLETED) {
            throw new IllegalStateException("%s is %s; only a completed document is closed".formatted(doc.getDocumentNo(), from.label().toLowerCase()));
        }
        doc.transitionTo(BusinessDocumentStatus.CLOSED);
        repository.save(doc);
        approvals.record(doc, ApprovalAction.CLOSED, from, remarks);
        return doc;
    }

    // --------------------------------------------------------------------------------- helpers

    static String streamLabel(String stream) {
        if (ChainStep.REWORK_STREAM.equals(stream)) return "rework dyeing work orders";
        return Arrays.stream(ChainStep.values()).filter(s -> s.stream().equals(stream)).findFirst()
            .map(s -> s.plural().toLowerCase()).orElse(stream.toLowerCase());
    }

    private static List<BusinessDocumentColorLine> lines(BusinessDocument doc) {
        return doc.getLineGroups().stream().flatMap(g -> g.getColorLines().stream()).toList();
    }

    private static String append(String remarks, String note) {
        String next = remarks == null || remarks.isBlank() ? note : remarks + "\n" + note;
        return next.length() <= 1000 ? next : next.substring(next.length() - 1000);
    }
}

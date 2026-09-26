package com.asg.fabricerp.production;

import com.asg.fabricerp.accounts.CreditService;
import com.asg.fabricerp.approval.ApprovalAction;
import com.asg.fabricerp.approval.ApprovalHistory;
import com.asg.fabricerp.approval.ApprovalHistoryRepository;
import com.asg.fabricerp.approval.ApprovalListener;
import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asg.fabricerp.production.ChainDocumentService.lineName;
import static com.asg.fabricerp.production.ChainSupport.qty;

/**
 * What the chain does the moment a document's last approval level signs:
 * <ul>
 *   <li>a <b>Weaving WO</b> starts its production order (Processing);</li>
 *   <li>a <b>delivery order</b> passes the buyer's credit control and reserves its lots, so no
 *       other order can promise the same metres;</li>
 *   <li>an approved <b>revision</b> of a Booking, production order or delivery schedule takes over
 *       from the version it replaces: its draws move across, everything raised against the old
 *       lines is re-pointed to the new ones, and the old version is superseded. A revision may not
 *       bring a line below what later documents have already drawn on it.</li>
 * </ul>
 * Any refusal throws, and the approval is not signed.
 */
@Component
public class ChainApprovalListener implements ApprovalListener {

    private static final Set<DocumentType> HANDLED = EnumSet.of(DocumentType.BOOKING, DocumentType.BULK_PRODUCTION_ORDER,
        DocumentType.REQUEST_FOR_PI, DocumentType.WEAVING_WORK_ORDER, DocumentType.DELIVERY_ORDER);

    private final ChainSupport chain;
    private final ChainProgress progress;
    private final FabricStockService stock;
    private final CreditService credit;
    private final BusinessDocumentRepository repository;
    private final ApprovalHistoryRepository history;
    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;
    private final EntityManager em;

    public ChainApprovalListener(ChainSupport chain, ChainProgress progress, FabricStockService stock, CreditService credit,
                                 BusinessDocumentRepository repository, ApprovalHistoryRepository history,
                                 NamedParameterJdbcTemplate jdbc, OrgContext context, EntityManager em) {
        this.chain = chain;
        this.progress = progress;
        this.stock = stock;
        this.credit = credit;
        this.repository = repository;
        this.history = history;
        this.jdbc = jdbc;
        this.context = context;
        this.em = em;
    }

    @Override
    public boolean handles(DocumentType type) {
        return HANDLED.contains(type);
    }

    @Override
    public void onApproved(BusinessDocument doc) {
        if (doc.getRevisionNo() != null && doc.getRevisionNo() > 0) {
            succeed(doc);
            return;
        }
        switch (doc.getDocumentType()) {
            case WEAVING_WORK_ORDER -> {
                if (doc.getParentDocument() != null) progress.refresh(doc.getParentDocument());
            }
            case DELIVERY_ORDER -> reserve(doc);
            default -> { }
        }
    }

    // ---------------------------------------------------------------------------- delivery order

    private void reserve(BusinessDocument order) {
        if (order.getWarehouse() == null) {
            throw new IllegalStateException("Choose the delivering store on %s before approving it".formatted(order.getDocumentNo()));
        }
        checkCredit(order);
        for (BusinessDocumentLineGroup g : order.getLineGroups()) {
            for (BusinessDocumentColorLine l : g.getColorLines()) {
                if (l.getFabricLotId() == null) {
                    throw new IllegalStateException("Pick the lot for %s on %s before approving it".formatted(lineName(l), order.getDocumentNo()));
                }
                stock.reserve(context.requireOrganizationId(), l.getId(), order.getWarehouse().getId(), l.getFabricLotId(),
                    l.getQuantity(), order.getDocumentNo() + ", " + lineName(l));
            }
        }
    }

    /** The buyer's credit control: a buyer on hold, or this order taking them over their limit, stops it. */
    private void checkCredit(BusinessDocument order) {
        Long partyId = AuditableEntity.idOf(order.getParty());
        if (partyId == null) return;
        BigDecimal value = order.getSubtotalAmount();
        CreditService.Exposure probe = credit.exposureOf(partyId, BigDecimal.ZERO, LocalDate.now());
        if (!probe.hasLimit()) return;
        if (!Objects.equals(probe.currencyCode(), order.getCurrencyCode())) {
            value = value.multiply(order.getExchangeRate() == null ? BigDecimal.ONE : order.getExchangeRate());
        }
        CreditService.Exposure exposure = credit.exposureOf(partyId, value, LocalDate.now());
        switch (exposure.verdict()) {
            case ON_HOLD -> throw new IllegalStateException("%s cannot be approved: %s is on credit hold%s"
                .formatted(order.getDocumentNo(), order.getParty().getName(),
                    exposure.holdReason() == null ? "" : " (" + exposure.holdReason() + ")"));
            case OVER_LIMIT -> throw new IllegalStateException(("%s cannot be approved: it takes %s %s over the credit limit "
                + "(owes %s, limit %s %s). Raise the limit or collect first.")
                .formatted(order.getDocumentNo(), order.getParty().getName(), qty(exposure.headroom().negate()),
                    qty(exposure.receivable()), qty(exposure.limit()), exposure.currencyCode()));
            default -> { }
        }
    }

    // --------------------------------------------------------------------------------- revisions

    private void succeed(BusinessDocument revision) {
        Long rootId = revision.getRevisionOf() != null ? revision.getRevisionOf().getId() : revision.getId();
        BusinessDocument previous = repository.revisionsOf(rootId, revision.getOrganizationId()).stream()
            .filter(d -> !d.getId().equals(revision.getId()))
            .filter(d -> d.getRevisionNo() < revision.getRevisionNo())
            .filter(d -> d.getStatus().isCommitted())
            .max(Comparator.comparing(BusinessDocument::getRevisionNo))
            .flatMap(d -> repository.findScopedWithLines(d.getId(), d.getOrganizationId()))
            .orElse(null);
        Optional<ChainStep> step = ChainStep.of(revision.getDocumentType());
        em.flush();
        if (previous == null) {
            step.ifPresent(s -> chain.drawAll(s, revision));
            return;
        }
        // 1. The revision's own draws replace its predecessor's.
        step.ifPresent(s -> {
            chain.releaseAll(s, previous);
            chain.drawAll(s, revision);
        });

        // 2. Everything raised against the old lines moves to their continuations.
        Map<Long, BusinessDocumentLineGroup> groupMap = new HashMap<>();
        Map<Long, BusinessDocumentColorLine> lineMap = new HashMap<>();
        for (BusinessDocumentLineGroup old : previous.getLineGroups()) {
            BusinessDocumentLineGroup next = revision.getLineGroups().stream()
                .filter(g -> Objects.equals(g.getRevisedFromGroupId(), old.getId())).findFirst()
                .orElseGet(() -> revision.getLineGroups().stream()
                    .filter(g -> g.getRevisedFromGroupId() == null && Objects.equals(g.getGroupNo(), old.getGroupNo()))
                    .findFirst().orElse(null));
            if (next != null) groupMap.put(old.getId(), next);
            for (BusinessDocumentColorLine l : old.getColorLines()) {
                BusinessDocumentColorLine match = revision.getLineGroups().stream().flatMap(g -> g.getColorLines().stream())
                    .filter(n -> Objects.equals(n.getRevisedFromLineId(), l.getId())).findFirst()
                    .orElseGet(() -> next == null ? null : next.getColorLines().stream()
                        .filter(n -> n.getRevisedFromLineId() == null && Objects.equals(n.getColorLineNo(), l.getColorLineNo()))
                        .findFirst().orElse(null));
                if (match != null) lineMap.put(l.getId(), match);
            }
        }
        List<Long> oldLineIds = previous.getLineGroups().stream().flatMap(g -> g.getColorLines().stream())
            .map(BusinessDocumentColorLine::getId).toList();
        Map<Long, Map<String, BigDecimal>> lineStreams = chain.ledger().streams(SourceKind.COLOUR, oldLineIds);
        Map<Long, Map<String, BigDecimal>> groupStreams = chain.ledger().streams(SourceKind.GROUP,
            previous.getLineGroups().stream().map(BusinessDocumentLineGroup::getId).toList());

        for (BusinessDocumentLineGroup old : previous.getLineGroups()) {
            for (BusinessDocumentColorLine l : old.getColorLines()) {
                Map<String, BigDecimal> streams = lineStreams.getOrDefault(l.getId(), Map.of());
                BusinessDocumentColorLine next = lineMap.get(l.getId());
                if (next == null) {
                    if (streams.values().stream().anyMatch(q -> q.signum() > 0) || referenced("source_color_line_id", l.getId())) {
                        throw new IllegalStateException("%s removes %s, which already has %s raised against it. Keep the line."
                            .formatted(revision.getDocumentNo(), lineName(l), describe(streams)));
                    }
                    continue;
                }
                streams.forEach((stream, drawn) -> {
                    BigDecimal cap = capOf(stream, next);
                    if (cap != null && drawn.compareTo(cap) > 0) {
                        throw new IllegalStateException(("%s brings %s to %s, below the %s already on %s (%s allowed). "
                            + "Keep at least that much.").formatted(revision.getDocumentNo(), lineName(l), qty(next.getQuantity()),
                            qty(drawn), ChainPostingService.streamLabel(stream), qty(cap)));
                    }
                });
                if (l.isShortClosed() && !next.isShortClosed()) next.shortClose(l.getShortClosedQuantity(), l.getShortCloseReason());
                chain.ledger().repoint(SourceKind.COLOUR, l.getId(), next.getId());
                update("UPDATE gbl_business_document_color_lines SET source_color_line_id = :to WHERE source_color_line_id = :from",
                    l.getId(), next.getId());
                update("UPDATE inv_fabric_lots SET color_line_id = :to WHERE color_line_id = :from", l.getId(), next.getId());
                update("""
                    UPDATE gbl_business_document_color_lines SET fulfilled_quantity = LEAST(
                        (SELECT o.fulfilled_quantity FROM gbl_business_document_color_lines o WHERE o.id = :from), quantity)
                    WHERE id = :to
                    """, l.getId(), next.getId());
            }
            Map<String, BigDecimal> streams = groupStreams.getOrDefault(old.getId(), Map.of());
            BusinessDocumentLineGroup next = groupMap.get(old.getId());
            if (next == null) {
                if (streams.values().stream().anyMatch(q -> q.signum() > 0) || referenced("source_line_group_id", old.getId())) {
                    throw new IllegalStateException("%s removes fabric line %d, which is already being woven. Keep the line."
                        .formatted(revision.getDocumentNo(), old.getGroupNo()));
                }
                continue;
            }
            streams.forEach((stream, drawn) -> {
                BigDecimal cap = next.getRoute().greigeFor(next.groupQuantity());
                if (drawn.compareTo(cap) > 0) {
                    throw new IllegalStateException("%s leaves fabric line %d %s of greige, below the %s already on weaving work orders"
                        .formatted(revision.getDocumentNo(), next.getGroupNo(), qty(cap), qty(drawn)));
                }
            });
            chain.ledger().repoint(SourceKind.GROUP, old.getId(), next.getId());
            update("UPDATE gbl_business_document_color_lines SET source_line_group_id = :to WHERE source_line_group_id = :from",
                old.getId(), next.getId());
            update("UPDATE inv_fabric_lots SET line_group_id = :to WHERE line_group_id = :from", old.getId(), next.getId());
        }
        update("UPDATE gbl_business_documents SET parent_document_id = :to WHERE parent_document_id = :from AND id <> :to",
            previous.getId(), revision.getId());
        update("UPDATE inv_fabric_lots SET bpo_document_id = :to WHERE bpo_document_id = :from", previous.getId(), revision.getId());

        // 3. The old version is superseded; the revision carries on where it was.
        BusinessDocumentStatus was = previous.getStatus();
        if (was.canTransitionTo(BusinessDocumentStatus.CANCELLED)) {
            previous.transitionTo(BusinessDocumentStatus.CANCELLED);
        } else if (was.canTransitionTo(BusinessDocumentStatus.CLOSED)) {
            previous.transitionTo(BusinessDocumentStatus.CLOSED);
        }
        String note = "Superseded by revision " + revision.getDocumentNo();
        previous.setRemarks(previous.getRemarks() == null ? note : (previous.getRemarks() + "\n" + note));
        repository.save(previous);
        history.save(new ApprovalHistory(previous.getId(), previous.getDocumentType(), ApprovalAction.SUPERSEDED, was,
            previous.getStatus(), note));
        if (was == BusinessDocumentStatus.PROCESSING || was == BusinessDocumentStatus.PARTIAL || was == BusinessDocumentStatus.COMPLETED) {
            revision.progressTo(was);
        }
        progress.refresh(revision);
    }

    /** What a child stream may hold on the revised line: the same cap it was drawn under. */
    private BigDecimal capOf(String stream, BusinessDocumentColorLine line) {
        if (ChainStep.REWORK_STREAM.equals(stream)) return line.getQuantity();
        return Arrays.stream(ChainStep.values()).filter(s -> s.stream().equals(stream)).findFirst()
            .map(s -> s == ChainStep.FFR ? null : DrawCaps.cap(s, null, line.getQuantity(), line.getLineGroup().getRoute(), null))
            .orElse(null);
    }

    private boolean referenced(String column, Long id) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM gbl_business_document_color_lines l
            JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
            JOIN gbl_business_documents d ON d.id = g.document_id
            WHERE l.%s = :id AND d.deleted = false AND d.status <> 'CANCELLED'
            """.formatted(column), new MapSqlParameterSource("id", id), Integer.class);
        return n != null && n > 0;
    }

    private static String describe(Map<String, BigDecimal> streams) {
        List<String> parts = new ArrayList<>();
        streams.forEach((s, q) -> { if (q.signum() > 0) parts.add(qty(q) + " on " + ChainPostingService.streamLabel(s)); });
        return parts.isEmpty() ? "documents" : String.join(", ", parts);
    }

    private void update(String sql, Long from, Long to) {
        jdbc.update(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }
}

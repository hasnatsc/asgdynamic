package com.asg.fabricerp.commercial;

import com.asg.fabricerp.approval.ApprovalAction;
import com.asg.fabricerp.approval.ApprovalHistory;
import com.asg.fabricerp.approval.ApprovalHistoryRepository;
import com.asg.fabricerp.approval.ApprovalListener;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import com.asg.fabricerp.supply.SupplyDraws;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * What a commercial document does the moment its last approval level signs:
 * <ul>
 *   <li>an <b>amendment</b> of a PI or LC takes over from the version it amends: its draws replace the
 *       old version's, everything raised against the old lines (LCs on a PI, CIs on an LC, import LCs
 *       backed by an export LC) moves to the new ones, and the old version is superseded. An amendment
 *       may not bring a line below what later documents already hold of it;</li>
 *   <li>an <b>export CI</b> raises the buyer's receivable in the ledger;</li>
 *   <li>the PI or LC it was raised against moves on (Partial, Completed).</li>
 * </ul>
 * Any refusal throws, and the approval is not signed.
 */
@Component
public class CommercialApprovalListener implements ApprovalListener {

    private final CommercialDraws draws;
    private final CommercialProgress progress;
    private final CommercialAccounting accounting;
    private final BusinessDocumentRepository repository;
    private final ApprovalHistoryRepository history;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;

    public CommercialApprovalListener(CommercialDraws draws, CommercialProgress progress, CommercialAccounting accounting,
                                      BusinessDocumentRepository repository, ApprovalHistoryRepository history,
                                      NamedParameterJdbcTemplate jdbc, EntityManager em) {
        this.draws = draws;
        this.progress = progress;
        this.accounting = accounting;
        this.repository = repository;
        this.history = history;
        this.jdbc = jdbc;
        this.em = em;
    }

    @Override
    public boolean handles(DocumentType type) {
        return CommercialStep.of(type).isPresent();
    }

    @Override
    public void onApproved(BusinessDocument doc) {
        CommercialStep step = CommercialStep.of(doc.getDocumentType()).orElseThrow();
        if (doc.getRevisionNo() != null && doc.getRevisionNo() > 0) succeed(step, doc);
        if (step == CommercialStep.ECI) {
            String note = accounting.invoice(doc);
            history.save(new ApprovalHistory(doc.getId(), doc.getDocumentType(), ApprovalAction.ACCOUNTED, doc.getStatus(),
                doc.getStatus(), note));
        }
        progress.refreshUpwards(doc);
    }

    private void succeed(CommercialStep step, BusinessDocument revision) {
        Long rootId = revision.getRevisionOf() != null ? revision.getRevisionOf().getId() : revision.getId();
        BusinessDocument previous = repository.revisionsOf(rootId, revision.getOrganizationId()).stream()
            .filter(d -> !d.getId().equals(revision.getId()))
            .filter(d -> d.getRevisionNo() < revision.getRevisionNo())
            .filter(d -> d.getStatus().isCommitted())
            .max(Comparator.comparing(BusinessDocument::getRevisionNo))
            .flatMap(d -> repository.findScopedWithLines(d.getId(), d.getOrganizationId()))
            .orElse(null);
        em.flush();
        if (previous == null) {
            draws.drawAll(step, revision);
            return;
        }
        // 1. The amendment's draws replace its predecessor's.
        draws.releaseAll(step, previous);
        draws.drawAll(step, revision);

        // 2. What was raised against the old lines moves to their continuations.
        Map<Long, BusinessDocumentColorLine> next = new HashMap<>();
        for (BusinessDocumentColorLine l : SupplyDraws.lines(revision)) {
            if (l.getRevisedFromLineId() != null) next.put(l.getRevisedFromLineId(), l);
        }
        Map<Long, Map<String, BigDecimal>> streams = draws.streams(previous);
        for (BusinessDocumentColorLine old : SupplyDraws.lines(previous)) {
            Map<String, BigDecimal> held = streams.getOrDefault(old.getId(), Map.of());
            boolean used = held.values().stream().anyMatch(q -> q.signum() > 0) || referenced(old.getId());
            BusinessDocumentColorLine continuation = next.get(old.getId());
            if (continuation == null) {
                if (used) {
                    throw new IllegalStateException("%s removes %s, which already has documents raised against it. Keep the line."
                        .formatted(revision.getDocumentNo(), SupplyDraws.lineName(old)));
                }
                continue;
            }
            held.forEach((stream, drawn) -> {
                if (drawn.compareTo(continuation.getQuantity()) > 0) {
                    throw new IllegalStateException("%s brings %s to %s, below the %s already on %s. Keep at least that much."
                        .formatted(revision.getDocumentNo(), SupplyDraws.lineName(old), plain(continuation.getQuantity()), plain(drawn),
                            CommercialViews.labelOf(DocumentType.valueOf(stream)).toLowerCase() + "s"));
                }
            });
            draws.ledger().repoint(SourceKind.COLOUR, old.getId(), continuation.getId());
            update("UPDATE gbl_business_document_color_lines SET source_color_line_id = :to WHERE source_color_line_id = :from",
                old.getId(), continuation.getId());
            update("""
                UPDATE gbl_business_document_color_lines SET fulfilled_quantity = LEAST(
                    (SELECT o.fulfilled_quantity FROM gbl_business_document_color_lines o WHERE o.id = :from), quantity)
                WHERE id = :to
                """, old.getId(), continuation.getId());
        }
        update("UPDATE gbl_business_documents SET parent_document_id = :to WHERE parent_document_id = :from AND id <> :to",
            previous.getId(), revision.getId());
        update("UPDATE com_document_details SET backed_by_document_id = :to WHERE backed_by_document_id = :from",
            previous.getId(), revision.getId());

        // 3. The old version is superseded; the amendment carries on where it was.
        BusinessDocumentStatus was = previous.getStatus();
        if (was.canTransitionTo(BusinessDocumentStatus.CANCELLED)) previous.transitionTo(BusinessDocumentStatus.CANCELLED);
        else if (was.canTransitionTo(BusinessDocumentStatus.CLOSED)) previous.transitionTo(BusinessDocumentStatus.CLOSED);
        String note = "Superseded by amendment " + revision.getDocumentNo();
        previous.setRemarks(previous.getRemarks() == null ? note : previous.getRemarks() + "\n" + note);
        repository.save(previous);
        history.save(new ApprovalHistory(previous.getId(), previous.getDocumentType(), ApprovalAction.SUPERSEDED, was,
            previous.getStatus(), note));
        if (was == BusinessDocumentStatus.PARTIAL || was == BusinessDocumentStatus.COMPLETED) revision.progressTo(was);
    }

    private boolean referenced(Long lineId) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM gbl_business_document_color_lines l
            JOIN gbl_business_document_line_groups g ON g.id = l.line_group_id
            JOIN gbl_business_documents d ON d.id = g.document_id
            WHERE l.source_color_line_id = :id AND d.deleted = FALSE AND d.status <> 'CANCELLED'
            """, new MapSqlParameterSource("id", lineId), Integer.class);
        return n != null && n > 0;
    }

    private void update(String sql, Long from, Long to) {
        jdbc.update(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}

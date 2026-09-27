package com.asg.fabricerp.commercial;

import com.asg.fabricerp.approval.ApprovalAction;
import com.asg.fabricerp.approval.ApprovalService;
import com.asg.fabricerp.commercial.CommercialTerms.*;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentRepository;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.party.PartyRoleType;
import com.asg.fabricerp.party.PartyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * What is recorded against a commercial document after it is raised - the legacy LC's UD, UP, raw
 * material BTB LC, sales contract, required document and cost tabs; an import PI's checklist; a CI's
 * realization - and the facts that arrive later: the LC's acknowledgement and cash incentive, the
 * import LC's bill of entry, the CI's IBC number.
 *
 * <p>A CI is realized step by step, in order: a step may only be recorded once those before it are,
 * never dated before them; only the latest may be taken back. Its final payment completes the CI and
 * posts the receipt to the ledger.
 */
@Service
public class CommercialRecordsService {

    /** What each document may carry; realization and milestones have their own calls. */
    private static final Map<CommercialStep, Set<EventKind>> ALLOWED = Map.of(
        CommercialStep.EPI, EnumSet.of(EventKind.COST),
        CommercialStep.ELC, EnumSet.of(EventKind.UD, EventKind.UP, EventKind.BTB_LC, EventKind.SALES_CONTRACT,
            EventKind.REQUIRED_DOC, EventKind.COST),
        CommercialStep.ECI, EnumSet.of(EventKind.COST),
        CommercialStep.IPI, EnumSet.of(EventKind.COST),
        CommercialStep.ILC, EnumSet.of(EventKind.REQUIRED_DOC, EventKind.COST));

    public record EventRequest(EventKind kind, String code, String refNo, LocalDate date, BigDecimal amount, Long partyId,
                               Long costHeadId, Long documentNameId, String remarks) { }

    public record FactsRequest(LocalDate acknowledgedOn, BigDecimal incentiveAmount, LocalDate incentiveAppliedOn,
                               LocalDate incentiveConfirmedOn, String billOfEntryNo, LocalDate billOfEntryDate, String ibcNo) { }

    private final CommercialDocumentService documents;
    private final CommercialDetailsRepository detailsRepository;
    private final CommercialEventRepository events;
    private final CostHeadRepository costHeads;
    private final DocumentNameRepository documentNames;
    private final CommercialAccounting accounting;
    private final PartyService parties;
    private final ApprovalService approvals;
    private final BusinessDocumentRepository repository;
    private final OrgContext context;

    public CommercialRecordsService(CommercialDocumentService documents, CommercialDetailsRepository detailsRepository,
                                    CommercialEventRepository events, CostHeadRepository costHeads, DocumentNameRepository documentNames,
                                    CommercialAccounting accounting, PartyService parties, ApprovalService approvals,
                                    BusinessDocumentRepository repository, OrgContext context) {
        this.documents = documents;
        this.detailsRepository = detailsRepository;
        this.events = events;
        this.costHeads = costHeads;
        this.documentNames = documentNames;
        this.accounting = accounting;
        this.parties = parties;
        this.approvals = approvals;
        this.repository = repository;
        this.context = context;
    }

    // ---------------------------------------------------------------------------------- records

    @Transactional
    public CommercialEvent record(CommercialStep step, Long documentId, EventRequest r) {
        BusinessDocument doc = live(step, documentId);
        if (r.kind() == null || !ALLOWED.get(step).contains(r.kind())) {
            throw new IllegalArgumentException("A %s does not record %s".formatted(step.label(), r.kind() == null ? "that" : r.kind().label()));
        }
        CommercialEvent e = new CommercialEvent(doc.getOrganizationId(), doc.getId(), r.kind());
        e.setRefNo(CommercialDocumentService.blank(r.refNo()));
        e.setEventDate(r.date());
        e.setAmount(r.amount());
        e.setRemarks(CommercialDocumentService.blank(r.remarks()));
        if (r.amount() != null && r.amount().signum() < 0) throw new IllegalArgumentException("An amount cannot be negative");
        switch (r.kind()) {
            case UD, UP -> {
                require(e.getRefNo(), "Give the %s number".formatted(r.kind().name()));
                require(r.date(), "Give the date it was %s".formatted(r.kind() == EventKind.UD ? "received" : "issued"));
                require(r.amount(), "Give its value");
                if (r.kind() == EventKind.UP) {
                    BigDecimal ud = sum(doc.getId(), EventKind.UD), up = sum(doc.getId(), EventKind.UP).add(r.amount());
                    if (up.compareTo(ud) > 0) {
                        throw new IllegalStateException("UPs would total %s, more than the %s declared on UDs".formatted(plain(up), plain(ud)));
                    }
                }
            }
            case BTB_LC -> {
                require(e.getRefNo(), "Give the back-to-back LC number");
                require(r.date(), "Give the date it was opened");
                require(r.amount(), "Give its amount");
                String material = r.code() == null ? null : r.code().strip();
                if (material == null || MATERIAL_TYPES_LOWER.stream().noneMatch(material::equalsIgnoreCase)) {
                    throw new IllegalArgumentException("Material is one of " + String.join(", ", CommercialTerms.MATERIAL_TYPES));
                }
                e.setCode(CommercialTerms.MATERIAL_TYPES.stream().filter(material::equalsIgnoreCase).findFirst().orElse(material));
                if (r.partyId() != null) e.setPartyId(parties.requireHolder(r.partyId(), PartyRoleType.SUPPLIER).getId());
            }
            case SALES_CONTRACT -> {
                require(e.getRefNo(), "Give the sales contract number");
                require(r.date(), "Give the sales contract date");
            }
            case REQUIRED_DOC -> {
                DocumentName name = documentNames.findScoped(r.documentNameId(), context.requireOrganizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Choose the document the LC requires"));
                boolean already = events.findByDocumentIdAndKindOrderByEventDateAscIdAsc(doc.getId(), EventKind.REQUIRED_DOC).stream()
                    .anyMatch(x -> name.getId().equals(x.getDocumentNameId()));
                if (already) throw new IllegalArgumentException(name.getName() + " is already required");
                e.setDocumentNameId(name.getId());
            }
            case COST -> {
                CostHead head = costHeads.findScoped(r.costHeadId(), context.requireOrganizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Choose the cost head"));
                require(r.amount(), "Give the cost");
                if (r.amount().signum() == 0) throw new IllegalArgumentException("A cost is more than nothing");
                require(r.date(), "Give the date of the cost");
                e.setCostHeadId(head.getId());
            }
            default -> throw new IllegalArgumentException(r.kind().label() + " is recorded elsewhere");
        }
        e.setRecordedBy(context.username());
        return events.save(e);
    }

    @Transactional
    public void remove(CommercialStep step, Long documentId, Long eventId) {
        BusinessDocument doc = live(step, documentId);
        CommercialEvent e = events.findById(eventId).filter(x -> x.getDocumentId().equals(doc.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Record not found: " + eventId));
        if (e.getKind() == EventKind.REALIZATION) {
            throw new IllegalArgumentException("Take a realization step back with Undo, latest first");
        }
        if (e.getKind() == EventKind.UD) {
            BigDecimal ud = sum(doc.getId(), EventKind.UD).subtract(e.getAmount() == null ? BigDecimal.ZERO : e.getAmount());
            if (sum(doc.getId(), EventKind.UP).compareTo(ud) > 0) {
                throw new IllegalStateException("UPs already issued exceed what would be left on UDs; remove the UP first");
            }
        }
        events.delete(e);
    }

    // --------------------------------------------------------------------------- milestones

    /** Ticks an import PI's checkpoint (CED, C&F, bond, PI corrected, LC drafted, LC corrected). */
    @Transactional
    public CommercialEvent milestone(Long documentId, ImportMilestone milestone, LocalDate date, String remarks) {
        BusinessDocument doc = live(CommercialStep.IPI, documentId);
        if (milestone == null) throw new IllegalArgumentException("Choose the milestone");
        if (events.findByDocumentIdAndKindOrderByEventDateAscIdAsc(doc.getId(), EventKind.MILESTONE).stream()
                .anyMatch(e -> milestone.name().equals(e.getCode()))) {
            throw new IllegalStateException(milestone.label() + " is already recorded");
        }
        if ((milestone == ImportMilestone.LC_CORRECTED) && !done(doc.getId(), ImportMilestone.LC_DRAFT)) {
            throw new IllegalStateException("The LC is corrected once it is drafted; record LC drafted first");
        }
        CommercialEvent e = new CommercialEvent(doc.getOrganizationId(), doc.getId(), EventKind.MILESTONE);
        e.setCode(milestone.name());
        e.setEventDate(date == null ? LocalDate.now() : date);
        e.setRemarks(CommercialDocumentService.blank(remarks));
        e.setRecordedBy(context.username());
        return events.save(e);
    }

    @Transactional
    public void undoMilestone(Long documentId, Long eventId) {
        BusinessDocument doc = live(CommercialStep.IPI, documentId);
        CommercialEvent e = events.findById(eventId)
            .filter(x -> x.getDocumentId().equals(doc.getId()) && x.getKind() == EventKind.MILESTONE)
            .orElseThrow(() -> new IllegalArgumentException("Milestone not found: " + eventId));
        if (ImportMilestone.LC_DRAFT.name().equals(e.getCode()) && done(doc.getId(), ImportMilestone.LC_CORRECTED)) {
            throw new IllegalStateException("Take back LC corrected first");
        }
        events.delete(e);
    }

    // --------------------------------------------------------------------------- realization

    /** Records a CI's next realization step. */
    @Transactional
    public BusinessDocument realize(Long ciId, RealizationStep step, LocalDate date, BigDecimal amount, String refNo, String remarks) {
        BusinessDocument ci = documents.get(CommercialStep.ECI, ciId);
        if (!ci.getStatus().isCommitted() || ci.getStatus() == BusinessDocumentStatus.CLOSED) {
            throw new IllegalStateException("%s is %s; a CI is realized once approved".formatted(ci.getDocumentNo(), ci.getStatus().label().toLowerCase()));
        }
        if (step == null) throw new IllegalArgumentException("Choose the step");
        if (date == null) throw new IllegalArgumentException("Give the date of " + step.label().toLowerCase());
        Map<RealizationStep, CommercialEvent> done = realized(ci.getId());
        if (done.containsKey(RealizationStep.FINAL_PAYMENT)) throw new IllegalStateException(ci.getDocumentNo() + " is fully realized");
        if (done.containsKey(step)) throw new IllegalStateException(step.label() + " is already recorded");
        List<RealizationStep> missing = step.required().stream().filter(s -> !done.containsKey(s)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Record %s first".formatted(missing.get(0).label().toLowerCase()));
        }
        if (date.isBefore(ci.getDocumentDate())) throw new IllegalArgumentException("A step cannot be dated before the CI");
        done.values().stream().map(CommercialEvent::getEventDate).filter(Objects::nonNull).max(Comparator.naturalOrder())
            .filter(date::isBefore).ifPresent(last -> {
                throw new IllegalArgumentException("It cannot be dated before the step recorded on " + last);
            });
        if (step.hasAmount()) {
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Give the amount " + (step == RealizationStep.PURCHASE ? "the bank paid" : "received"));
            if (amount.compareTo(ci.getSubtotalAmount()) > 0) {
                throw new IllegalArgumentException("%s is more than the CI's %s %s".formatted(plain(amount), plain(ci.getSubtotalAmount()), ci.getCurrencyCode()));
            }
        } else if (amount != null) {
            throw new IllegalArgumentException(step.label() + " records no amount");
        }
        CommercialEvent e = new CommercialEvent(ci.getOrganizationId(), ci.getId(), EventKind.REALIZATION);
        e.setCode(step.name());
        e.setEventDate(date);
        e.setAmount(amount);
        e.setRefNo(CommercialDocumentService.blank(refNo));
        e.setRemarks(CommercialDocumentService.blank(remarks));
        e.setRecordedBy(context.username());
        events.save(e);

        CommercialDetails det = documents.details(ci);
        det.setRealizationStep(step);
        detailsRepository.save(det);
        if (step == RealizationStep.FINAL_PAYMENT) {
            BusinessDocumentStatus from = ci.getStatus();
            ci.progressTo(BusinessDocumentStatus.COMPLETED);
            repository.save(ci);
            approvals.record(ci, ApprovalAction.REALIZED, from, "Realized: %s %s received on %s. %s".formatted(plain(amount),
                ci.getCurrencyCode(), date, accounting.receipt(ci, amount, date)));
        }
        return ci;
    }

    /** Takes back a CI's latest realization step - the final payment's receipt is reversed with it. */
    @Transactional
    public BusinessDocument undoRealization(Long ciId, String reason) {
        BusinessDocument ci = documents.get(CommercialStep.ECI, ciId);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the step is taken back");
        if (ci.getStatus() == BusinessDocumentStatus.CLOSED) throw new IllegalStateException(ci.getDocumentNo() + " is closed");
        Map<RealizationStep, CommercialEvent> done = realized(ci.getId());
        RealizationStep latest = done.keySet().stream().max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException("Nothing is recorded to take back"));
        events.delete(done.get(latest));
        CommercialDetails det = documents.details(ci);
        det.setRealizationStep(done.keySet().stream().filter(s -> s != latest).max(Comparator.naturalOrder()).orElse(null));
        detailsRepository.save(det);
        if (latest == RealizationStep.FINAL_PAYMENT) {
            String note = accounting.reverse(ci, com.asg.fabricerp.accounts.PostingEvent.RECEIPT, "Realization taken back: " + reason.strip());
            BusinessDocumentStatus from = ci.getStatus();
            ci.progressTo(BusinessDocumentStatus.APPROVED);
            repository.save(ci);
            approvals.record(ci, ApprovalAction.REALIZATION_UNDONE, from, "Final payment taken back: %s%s".formatted(reason.strip(), note == null ? "" : ". " + note));
        }
        return ci;
    }

    // ----------------------------------------------------------------------------------- facts

    /** Facts that arrive after approval: acknowledgement, incentive, bill of entry, IBC number. */
    @Transactional
    public void updateFacts(CommercialStep step, Long documentId, FactsRequest r) {
        BusinessDocument doc = live(step, documentId);
        CommercialDetails det = documents.details(doc);
        switch (step) {
            case ELC -> {
                det.setAcknowledgedOn(r.acknowledgedOn());
                if (r.incentiveAmount() != null && r.incentiveAmount().signum() < 0) throw new IllegalArgumentException("An incentive cannot be negative");
                if (r.incentiveConfirmedOn() != null && r.incentiveAppliedOn() == null) {
                    throw new IllegalArgumentException("An incentive is confirmed after it is applied for; give the application date");
                }
                if (r.incentiveConfirmedOn() != null && r.incentiveConfirmedOn().isBefore(r.incentiveAppliedOn())) {
                    throw new IllegalArgumentException("The incentive is confirmed before it was applied for");
                }
                det.setIncentiveAmount(r.incentiveAmount());
                det.setIncentiveAppliedOn(r.incentiveAppliedOn());
                det.setIncentiveConfirmedOn(r.incentiveConfirmedOn());
            }
            case ILC -> {
                if (r.billOfEntryDate() != null && r.billOfEntryNo() == null) throw new IllegalArgumentException("Give the bill of entry number");
                det.setBillOfEntryNo(CommercialDocumentService.blank(r.billOfEntryNo()));
                det.setBillOfEntryDate(r.billOfEntryDate());
            }
            case ECI -> det.setIbcNo(CommercialDocumentService.blank(r.ibcNo()));
            default -> throw new IllegalArgumentException(step.label() + " has no facts to add after approval");
        }
        detailsRepository.save(det);
    }

    // --------------------------------------------------------------------------------- helpers

    private static final List<String> MATERIAL_TYPES_LOWER = CommercialTerms.MATERIAL_TYPES;

    private BusinessDocument live(CommercialStep step, Long id) {
        BusinessDocument doc = documents.get(step, id);
        if (doc.getStatus() == BusinessDocumentStatus.CANCELLED || doc.getStatus() == BusinessDocumentStatus.CLOSED) {
            throw new IllegalStateException("%s is %s; nothing more is recorded against it".formatted(doc.getDocumentNo(), doc.getStatus().label().toLowerCase()));
        }
        return doc;
    }

    private Map<RealizationStep, CommercialEvent> realized(Long ciId) {
        Map<RealizationStep, CommercialEvent> out = new EnumMap<>(RealizationStep.class);
        for (CommercialEvent e : events.findByDocumentIdAndKindOrderByEventDateAscIdAsc(ciId, EventKind.REALIZATION)) {
            out.put(RealizationStep.valueOf(e.getCode()), e);
        }
        return out;
    }

    private boolean done(Long docId, ImportMilestone m) {
        return events.findByDocumentIdAndKindOrderByEventDateAscIdAsc(docId, EventKind.MILESTONE).stream().anyMatch(e -> m.name().equals(e.getCode()));
    }

    private BigDecimal sum(Long docId, EventKind kind) {
        return events.findByDocumentIdAndKindOrderByEventDateAscIdAsc(docId, kind).stream().map(CommercialEvent::getAmount)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void require(Object v, String message) {
        if (v == null) throw new IllegalArgumentException(message);
    }

    static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}

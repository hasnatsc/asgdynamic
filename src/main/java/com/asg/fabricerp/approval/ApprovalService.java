package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentRepository;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The approval engine for every document type - asfl-erp's {@code core} approval, on this system's
 * {@link BusinessDocument}.
 *
 * <h2>Routing</h2>
 * On submission the document's own marketing team's matrix is used if it has an active one, else
 * the unit-wide matrix for the type (asfl-erp V27). Only the levels whose amount band covers the
 * document's total apply, in order. A type with no matrix at all keeps this system's earlier rule -
 * one level, anyone holding the screen's {@code APPROVE} verb - so configuring matrices narrows who
 * approves without ever stopping a type that nobody has configured yet.
 *
 * <p>The matrix and the number of levels are fixed on the {@link ApprovalRequest} at submission and
 * never re-resolved: a matrix edited half way through, or an approver from another team, cannot
 * change the rules for a document already on its way.
 *
 * <h2>Deciding</h2>
 * The actor must satisfy the current level (the role, the named user, or for the default rule the
 * screen verb), must be able to see the document, and must not be the one who submitted it
 * (four-eyes, however senior). Approving moves to the next level, or approves the document at the
 * last; Return sends it back to the maker as a draft; Reject refuses it. Both refusals need a reason.
 *
 * <h2>Why the checks are here and not {@code @PreAuthorize}</h2>
 * The requirement depends on the document - its type, its team, its amount, its current level -
 * which is known only once it is loaded.
 */
@Service
public class ApprovalService {

    private final BusinessDocumentRepository repository;
    private final ApprovalHistoryRepository historyRepository;
    private final ApprovalRequestRepository requests;
    private final ApprovalMatrixRepository matrices;
    private final ApprovalActors actors;
    private final ApprovalLabels labels;
    private final OrgContext context;

    public ApprovalService(BusinessDocumentRepository repository,
                           ApprovalHistoryRepository historyRepository,
                           ApprovalRequestRepository requests,
                           ApprovalMatrixRepository matrices,
                           ApprovalActors actors,
                           ApprovalLabels labels,
                           OrgContext context) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.requests = requests;
        this.matrices = matrices;
        this.actors = actors;
        this.labels = labels;
        this.context = context;
    }

    // ------------------------------------------------------------------------------ submit

    @Transactional
    public BusinessDocument submit(Long documentId) {
        BusinessDocument doc = load(documentId);
        AuthorityChecks.requireAny(
            doc.getDocumentType().createAuthority(), doc.getDocumentType().amendAuthority());
        if (doc.getLineGroups().isEmpty()) {
            throw new IllegalStateException(
                "%s %s has no lines and cannot be submitted"
                    .formatted(doc.getDocumentType().label(), doc.getDocumentNo()));
        }
        requests.findByDocumentIdAndPendingTrue(documentId).ifPresent(live -> {
            throw new IllegalStateException("%s is already awaiting approval at level %d of %d"
                .formatted(doc.getDocumentNo(), live.getCurrentLevel(), live.getTotalLevels()));
        });

        BusinessDocumentStatus from = doc.getStatus();
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        Approver.Actor actor = actors.current();
        ApprovalRequest request = requests.save(open(doc, actor.userId(), actor.username()));
        recordHistory(doc, ApprovalAction.SUBMITTED, from, null, request, null);
        return repository.save(doc);
    }

    // ------------------------------------------------------------------------------ decide

    @Transactional
    public BusinessDocument approve(Long documentId, String remarks) {
        return decide(documentId, ApprovalDecision.APPROVED, remarks);
    }

    @Transactional
    public BusinessDocument returnToMaker(Long documentId, String remarks) {
        return decide(documentId, ApprovalDecision.RETURNED, remarks);
    }

    @Transactional
    public BusinessDocument reject(Long documentId, String remarks) {
        return decide(documentId, ApprovalDecision.REJECTED, remarks);
    }

    @Transactional
    public BusinessDocument decide(Long documentId, ApprovalDecision decision, String remarks) {
        BusinessDocument doc = load(documentId);
        if (doc.getStatus() != BusinessDocumentStatus.SUBMITTED) {
            throw new IllegalStateException("%s is %s, not awaiting approval"
                .formatted(doc.getDocumentNo(), doc.getStatus().label().toLowerCase()));
        }
        if (decision.isRefusal() && (remarks == null || remarks.isBlank())) {
            throw new IllegalArgumentException("Say why it is %s: the maker has no other way to know what to change"
                .formatted(decision == ApprovalDecision.RETURNED ? "returned" : "rejected"));
        }
        ApprovalRequest request = requests.findByDocumentIdAndPendingTrue(documentId)
            .orElseGet(() -> requests.save(enrolLegacy(doc)));

        Approver.Actor actor = actors.current();
        Approver required = requiredApprover(request)
            .orElseThrow(() -> new IllegalStateException(("The approval matrix %s was submitted under no longer defines "
                + "level %d. Correct the matrix, or return the document and submit it again.")
                .formatted(doc.getDocumentNo(), request.getCurrentLevel())));
        if (request.wasRaisedBy(actor)) {
            throw new AccessDeniedException("Segregation of duties: %s submitted %s and cannot decide it"
                .formatted(actor.username(), doc.getDocumentNo()));
        }
        if (!required.isSatisfiedBy(actor)) {
            throw new AccessDeniedException("Level %d of %d on %s is for %s"
                .formatted(request.getCurrentLevel(), request.getTotalLevels(), doc.getDocumentNo(),
                    labels.approver(required, doc.getDocumentType())));
        }

        int level = request.getCurrentLevel();
        boolean last = request.isFinalLevel();
        request.decide(decision);
        requests.save(request);

        BusinessDocumentStatus from = doc.getStatus();
        switch (decision) {
            case APPROVED -> { if (last) doc.transitionTo(BusinessDocumentStatus.APPROVED); }
            case RETURNED -> doc.transitionTo(BusinessDocumentStatus.DRAFT);
            case REJECTED -> doc.transitionTo(BusinessDocumentStatus.REJECTED);
        }
        ApprovalAction action = switch (decision) {
            case APPROVED -> ApprovalAction.APPROVED;
            case RETURNED -> ApprovalAction.RETURNED;
            case REJECTED -> ApprovalAction.REJECTED;
        };
        recordHistory(doc, action, from, remarks, request, level);
        return repository.save(doc);
    }

    // ------------------------------------------------------------------------------ read

    /** Where a document's approval stands, for its screen: who must act next and whether it is you. */
    @Transactional(readOnly = true)
    public ApprovalStateView stateOf(Long documentId) {
        BusinessDocument doc = load(documentId);
        Optional<ApprovalRequest> latest = requests.findFirstByDocumentIdOrderByIdDesc(documentId);
        if (latest.isEmpty() || !latest.get().isPending()) {
            if (doc.getStatus() == BusinessDocumentStatus.SUBMITTED) {
                // Submitted before the engine: show who would decide it, as the first decision will enrol it.
                ApprovalRequest preview = enrolLegacy(doc);
                return state(doc, preview, actors.current());
            }
            return latest.map(r -> ApprovalStateView.settled(r.getId(), r.getOutcome(), r.getTotalLevels(),
                    labels.matrixName(r.getMatrixId()), labels.scope(r)))
                .orElse(ApprovalStateView.NONE);
        }
        return state(doc, latest.get(), actors.current());
    }

    /** What is waiting for the signed-in user: pending, visible to them, their level, and not their own. */
    @Transactional(readOnly = true)
    public List<ApprovalRowView> inbox() {
        Approver.Actor actor = actors.current();
        List<ApprovalRowView> rows = new ArrayList<>();
        for (ApprovalRequest request : requests.findPending(context.requireOrganizationId(), context.requireBusinessUnitId())) {
            Optional<BusinessDocument> doc = visible(request.getDocumentId());
            if (doc.isEmpty() || request.wasRaisedBy(actor)) continue;
            Optional<Approver> required = requiredApprover(request);
            if (required.isEmpty() || !required.get().isSatisfiedBy(actor)) continue;
            rows.add(labels.row(request, doc.get(), required.get()));
        }
        return rows;
    }

    /** Every request in the unit, newest first - pending, settled or both - that the caller may see. */
    @Transactional(readOnly = true)
    public Page<ApprovalRowView> report(Boolean pending, int page, int size) {
        Page<ApprovalRequest> found = requests.report(context.requireOrganizationId(), context.requireBusinessUnitId(),
            pending, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
        return found.map(request -> visible(request.getDocumentId())
            .map(doc -> labels.row(request, doc, request.isPending() ? requiredApprover(request).orElse(null) : null))
            .orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> historyOf(Long documentId) {
        load(documentId);   // scope check
        return historyRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    // ------------------------------------------------------------------------------ internals

    /** A new request for a document being submitted now, routed by its team and amount. */
    private ApprovalRequest open(BusinessDocument doc, Long raisedByUserId, String raisedBy) {
        Long teamId = doc.getMarketingTeam() == null ? null : doc.getMarketingTeam().getId();
        Long unitId = doc.getBusinessUnit() == null ? context.requireBusinessUnitId() : doc.getBusinessUnit().getId();
        BigDecimal amount = doc.getSubtotalAmount();
        Optional<ApprovalMatrix> matrix = resolve(doc.getDocumentType(), unitId, teamId);
        int levels = 1;
        if (matrix.isPresent()) {
            levels = matrix.get().levelsFor(amount).size();
            if (levels == 0) {
                throw new IllegalStateException(("The approval matrix “%s” has no level covering %s %s. Add a level for "
                    + "this amount before submitting.").formatted(matrix.get().getName(),
                        amount == null ? "" : amount.toPlainString(), doc.getCurrencyCode() == null ? "" : doc.getCurrencyCode()));
            }
        }
        return new ApprovalRequest(doc.getOrganizationId(), doc.getId(), doc.getDocumentType(), unitId, teamId,
            matrix.map(ApprovalMatrix::getId).orElse(null), amount, raisedByUserId, raisedBy, levels);
    }

    /**
     * A document submitted before this engine existed has no request. It is enrolled on its first
     * decision as though submitted now, with its creator as the raiser - so four-eyes still holds.
     */
    private ApprovalRequest enrolLegacy(BusinessDocument doc) {
        return open(doc, null, doc.getCreatedBy());
    }

    /** Team-wise first, unit-wide second, both only when active; empty means the default rule. */
    Optional<ApprovalMatrix> resolve(DocumentType type, Long unitId, Long teamId) {
        Long orgId = context.requireOrganizationId();
        Optional<ApprovalMatrix> teamWise = teamId == null ? Optional.empty()
            : matrices.findTeamWise(orgId, unitId, type, teamId).filter(m -> Boolean.TRUE.equals(m.getActive()));
        return teamWise.or(() -> matrices.findUnitWide(orgId, unitId, type).filter(m -> Boolean.TRUE.equals(m.getActive())));
    }

    /** Who must decide the request's current level, under the matrix it was submitted with. */
    Optional<Approver> requiredApprover(ApprovalRequest request) {
        if (request.getMatrixId() == null) {
            return Optional.of(Approver.authority(request.getDocumentType().approveAuthority()));
        }
        return matrices.findScoped(request.getMatrixId(), request.getOrganizationId())
            .flatMap(m -> m.levelFor(request.getAmount(), request.getCurrentLevel()))
            .map(ApprovalLevel::approver);
    }

    private ApprovalStateView state(BusinessDocument doc, ApprovalRequest request, Approver.Actor actor) {
        Optional<Approver> required = requiredApprover(request);
        boolean canAct = required.isPresent() && required.get().isSatisfiedBy(actor) && !request.wasRaisedBy(actor);
        String why = required.isEmpty() ? "The matrix no longer defines this level"
            : request.wasRaisedBy(actor) ? "You submitted it, so another person must decide it"
            : !canAct ? "Waiting for " + labels.approver(required.get(), doc.getDocumentType())
            : null;
        return new ApprovalStateView(request.getId(), true, request.getCurrentLevel(), request.getTotalLevels(),
            labels.matrixName(request.getMatrixId()), labels.scope(request),
            required.map(a -> labels.approver(a, doc.getDocumentType())).orElse(null),
            canAct, why, null);
    }

    private Optional<BusinessDocument> visible(Long documentId) {
        return repository.findScoped(documentId, context.requireOrganizationId())
            .filter(d -> d.isVisibleTo(context.requireRowScope()));
    }

    private BusinessDocument load(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    private void recordHistory(BusinessDocument doc, ApprovalAction action, BusinessDocumentStatus from,
                               String remarks, ApprovalRequest request, Integer level) {
        historyRepository.save(new ApprovalHistory(
            doc.getId(), doc.getDocumentType(), action, from, doc.getStatus(), remarks)
            .forRequest(request == null ? null : request.getId(), level));
    }
}

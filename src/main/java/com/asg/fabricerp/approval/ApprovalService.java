package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentRepository;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The approval engine for every document type - asfl-erp's {@code core} approval, on this system's
 * {@link BusinessDocument}. Every document type is submitted, approved, returned and rejected here,
 * and every approver works from one centralised inbox.
 *
 * <h2>Routing</h2>
 * On submission the document's own marketing team's matrix is used if it has an active one, else
 * the unit-wide matrix for the type (asfl-erp V27). Only the levels whose amount band covers the
 * document's total apply, in order. A type with no matrix at all keeps this system's earlier rule -
 * one level, anyone holding the screen's {@code APPROVE} verb - so configuring matrices narrows who
 * approves without ever stopping a type that nobody has configured yet.
 *
 * <p>The matrix and the number of levels are fixed on the {@link ApprovalRequest} at submission and
 * never re-resolved. Whenever the request moves, who its current level waits for is copied onto it
 * ({@link ApprovalRequest#routeTo}), so the inbox is one indexed query rather than a matrix lookup
 * per pending document.
 *
 * <h2>Who may decide</h2>
 * The actor must satisfy the current level and must not be the one who submitted it (four-eyes,
 * however senior). A level naming one user is that person's wherever the document's team - the
 * matrix named them. A level naming a role, and the default rule, are further limited to documents
 * the actor's row scope lets them see: a team's documents are approved by that team's approvers,
 * never by the holder of the same role in another team. Approving moves to the next level, or
 * approves the document at the last; Return sends it back to the maker as a draft; Reject refuses it.
 * Both refusals need a reason, and either way the maker may correct the document and submit it
 * again.
 *
 * <h2>Why the checks are here and not {@code @PreAuthorize}</h2>
 * The requirement depends on the document - its type, its team, its amount, its current level -
 * which is known only once it is loaded.
 */
@Service
public class ApprovalService {

    /** A JPQL IN list may not be empty; no row has this id. */
    private static final List<Long> NO_IDS = List.of(-1L);

    private final BusinessDocumentRepository repository;
    private final ApprovalHistoryRepository historyRepository;
    private final ApprovalRequestRepository requests;
    private final ApprovalMatrixRepository matrices;
    private final ApprovalActors actors;
    private final ApprovalLabels labels;
    private final OrgContext context;
    private final List<SubmissionCheck> checks;
    private final List<ApprovalListener> listeners;

    public ApprovalService(BusinessDocumentRepository repository,
                           ApprovalHistoryRepository historyRepository,
                           ApprovalRequestRepository requests,
                           ApprovalMatrixRepository matrices,
                           ApprovalActors actors,
                           ApprovalLabels labels,
                           OrgContext context,
                           List<SubmissionCheck> checks,
                           List<ApprovalListener> listeners) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.requests = requests;
        this.matrices = matrices;
        this.actors = actors;
        this.labels = labels;
        this.context = context;
        this.checks = List.copyOf(checks);
        this.listeners = List.copyOf(listeners);
    }

    // ------------------------------------------------------------------------------ submit

    /**
     * Sends a draft for approval - or, after a Reject, sends the corrected document again: a
     * rejected document goes back to draft and is submitted afresh under the matrix in force now.
     */
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
        // The type's own rules - a booking with an unpriced colour is refused here, not signed.
        checks.stream().filter(c -> c.type() == doc.getDocumentType()).forEach(c -> c.check(doc));
        requests.findByDocumentIdAndPendingTrue(documentId).ifPresent(live -> {
            throw new IllegalStateException("%s is already awaiting approval at level %d of %d"
                .formatted(doc.getDocumentNo(), live.getCurrentLevel(), live.getTotalLevels()));
        });

        BusinessDocumentStatus from = doc.getStatus();
        if (from == BusinessDocumentStatus.REJECTED) {
            doc.transitionTo(BusinessDocumentStatus.DRAFT);
        }
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        Approver.Actor actor = actors.current();
        ApprovalRequest opened = open(doc, actor.userId(), actor.username());
        requiredApprover(opened).ifPresent(opened::routeTo);
        ApprovalRequest request = requests.save(opened);
        recordHistory(doc, from == BusinessDocumentStatus.REJECTED ? ApprovalAction.RESUBMITTED : ApprovalAction.SUBMITTED,
            from, null, request, null);
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
        BusinessDocument doc = loadAny(documentId);
        Approver.Actor actor = actors.current();
        Optional<ApprovalRequest> pending = requests.findByDocumentIdAndPendingTrue(documentId);
        // Out of the actor's scope is "not found" - unless the current level names them personally.
        if (!inScope(doc) && !pending.flatMap(this::requiredApprover).map(a -> named(a, actor)).orElse(false)) {
            throw notFound(documentId);
        }
        if (doc.getStatus() != BusinessDocumentStatus.SUBMITTED) {
            throw new IllegalStateException("%s is %s, not awaiting approval"
                .formatted(doc.getDocumentNo(), doc.getStatus().label().toLowerCase()));
        }
        if (decision.isRefusal() && (remarks == null || remarks.isBlank())) {
            throw new IllegalArgumentException("Say why it is %s: the maker has no other way to know what to change"
                .formatted(decision == ApprovalDecision.RETURNED ? "returned" : "rejected"));
        }
        ApprovalRequest request = pending.orElseGet(() -> enrol(doc));

        Approver required = requiredApprover(request)
            .orElseThrow(() -> new IllegalStateException(("The approval matrix %s was submitted under no longer defines "
                + "level %d. Correct the matrix, or return the document and submit it again.")
                .formatted(doc.getDocumentNo(), request.getCurrentLevel())));
        if (request.wasRaisedBy(actor)) {
            throw new AccessDeniedException("Segregation of duties: %s submitted %s and cannot decide it"
                .formatted(actor.username(), doc.getDocumentNo()));
        }
        if (!mayAct(required, actor, doc)) {
            throw new AccessDeniedException("Level %d of %d on %s is for %s"
                .formatted(request.getCurrentLevel(), request.getTotalLevels(), doc.getDocumentNo(),
                    labels.approver(required, doc.getDocumentType())
                        + (required.isSatisfiedBy(actor) ? " in the document's own team" : "")));
        }

        int level = request.getCurrentLevel();
        boolean last = request.isFinalLevel();
        request.decide(decision);
        if (request.isPending()) {
            requiredApprover(request).ifPresentOrElse(request::routeTo, request::clearRoute);
        } else {
            request.clearRoute();
        }
        requests.save(request);

        BusinessDocumentStatus from = doc.getStatus();
        switch (decision) {
            case APPROVED -> {
                if (last) {
                    doc.transitionTo(BusinessDocumentStatus.APPROVED);
                    // The type's own consequences - reserve, start, supersede - inside this transaction.
                    listeners.stream().filter(l -> l.handles(doc.getDocumentType())).forEach(l -> l.onApproved(doc));
                }
            }
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
        BusinessDocument doc = loadForReview(documentId);
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

    /** What the centralised inbox lists: filters on top of "waiting for me". */
    public record InboxFilter(DocumentType type, String q) {
        public static final InboxFilter NONE = new InboxFilter(null, null);
    }

    /**
     * What is waiting for the signed-in user, oldest first, one page at a time: pending, at a level
     * that is theirs (their name; or their role or the default rule within their teams), and not their
     * own. One query for the page of requests, one for their documents, names looked up once each.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalRowView> inbox(InboxFilter filter, Pageable pageable) {
        Approver.Actor actor = actors.current();
        RowScope scope = context.requireRowScope();
        boolean allTeams = !scope.restricts(ScopeDimension.MARKETING_TEAM);
        List<DocumentType> defaultTypes = ApprovalMatrixService.approvableTypes().stream()
            .filter(t -> actor.authorities().contains(t.approveAuthority()))
            .toList();
        InboxFilter f = filter == null ? InboxFilter.NONE : filter;
        Page<ApprovalRequest> page = requests.inbox(
            context.requireOrganizationId(), context.requireBusinessUnitId(),
            actor.userId(), actor.username(), f.type(), blankToNull(f.q()),
            allTeams, scope.idsForQuery(ScopeDimension.MARKETING_TEAM),
            !actor.roleIds().isEmpty(), actor.roleIds().isEmpty() ? NO_IDS : actor.roleIds(),
            !defaultTypes.isEmpty(), defaultTypes.isEmpty() ? List.of(DocumentType.BOOKING) : defaultTypes,
            withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "createdAt", "id")));
        return rows(page);
    }

    /** How many are waiting for the signed-in user - the inbox badge. */
    @Transactional(readOnly = true)
    public long inboxCount() {
        return inbox(InboxFilter.NONE, PageRequest.of(0, 1)).getTotalElements();
    }

    /** Every request in the unit the caller's teams cover, newest first - pending, settled or both. */
    @Transactional(readOnly = true)
    public Page<ApprovalRowView> report(Boolean pending, DocumentType type, String q, Pageable pageable) {
        RowScope scope = context.requireRowScope();
        Page<ApprovalRequest> found = requests.report(context.requireOrganizationId(), context.requireBusinessUnitId(),
            pending, type, blankToNull(q),
            !scope.restricts(ScopeDimension.MARKETING_TEAM), scope.idsForQuery(ScopeDimension.MARKETING_TEAM),
            withDefaultSort(pageable, Sort.by(Sort.Direction.DESC, "id")));
        return rows(found);
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> historyOf(Long documentId) {
        loadForReview(documentId);   // scope check
        return historyRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    /**
     * Whether the signed-in user may read a document as its approver: its current level is theirs
     * to sign, or they have already signed or refused a level of it. A document screen that shows
     * only its owner's documents uses this to let the approvers - and only them - open it.
     */
    @Transactional(readOnly = true)
    public boolean canReview(BusinessDocument doc) {
        Approver.Actor actor = actors.current();
        Optional<ApprovalRequest> pending = requests.findByDocumentIdAndPendingTrue(doc.getId());
        if (pending.isPresent() && !pending.get().wasRaisedBy(actor)
                && requiredApprover(pending.get()).map(a -> mayAct(a, actor, doc)).orElse(false)) {
            return true;
        }
        return actor.username() != null && historyRepository.existsByDocumentIdAndCreatedByIgnoreCase(doc.getId(), actor.username());
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

    private ApprovalRequest enrol(BusinessDocument doc) {
        ApprovalRequest request = enrolLegacy(doc);
        requiredApprover(request).ifPresent(request::routeTo);
        return requests.save(request);
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

    /**
     * Whether the actor satisfies the level for THIS document: a named user always; a role or the
     * default rule only within the actor's row scope - the document's team's approvers, not another
     * team's holders of the same role.
     */
    private boolean mayAct(Approver required, Approver.Actor actor, BusinessDocument doc) {
        if (!required.isSatisfiedBy(actor)) return false;
        return required.kind() == Approver.Kind.USER || inScope(doc);
    }

    private static boolean named(Approver required, Approver.Actor actor) {
        return required.kind() == Approver.Kind.USER && required.isSatisfiedBy(actor);
    }

    private ApprovalStateView state(BusinessDocument doc, ApprovalRequest request, Approver.Actor actor) {
        Optional<Approver> required = requiredApprover(request);
        boolean canAct = required.isPresent() && mayAct(required.get(), actor, doc) && !request.wasRaisedBy(actor);
        String why = required.isEmpty() ? "The matrix no longer defines this level"
            : request.wasRaisedBy(actor) ? "You submitted it, so another person must decide it"
            : !canAct ? "Waiting for " + labels.approver(required.get(), doc.getDocumentType())
            : null;
        return new ApprovalStateView(request.getId(), true, request.getCurrentLevel(), request.getTotalLevels(),
            labels.matrixName(request.getMatrixId()), labels.scope(request),
            required.map(a -> labels.approver(a, doc.getDocumentType())).orElse(null),
            canAct, why, null);
    }

    /** One page of requests as rows: their documents in one query, every name looked up once. */
    private Page<ApprovalRowView> rows(Page<ApprovalRequest> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        Map<Long, BusinessDocument> docs = repository.findScopedWithParty(
                page.getContent().stream().map(ApprovalRequest::getDocumentId).toList(), context.requireOrganizationId())
            .stream().collect(Collectors.toMap(BusinessDocument::getId, Function.identity(), (a, b) -> a));
        ApprovalLabels.Batch batch = new ApprovalLabels.Batch(labels);
        Map<Long, Optional<ApprovalMatrix>> matrixCache = new java.util.HashMap<>();
        List<ApprovalRowView> rows = new ArrayList<>();
        for (ApprovalRequest request : page.getContent()) {
            BusinessDocument doc = docs.get(request.getDocumentId());
            if (doc == null) continue;   // deleted since
            Approver required = !request.isPending() ? null
                : request.getMatrixId() == null ? Approver.authority(request.getDocumentType().approveAuthority())
                : matrixCache.computeIfAbsent(request.getMatrixId(),
                        id -> matrices.findScoped(id, request.getOrganizationId()))
                    .flatMap(m -> m.levelFor(request.getAmount(), request.getCurrentLevel()))
                    .map(ApprovalLevel::approver).orElse(null);
            rows.add(batch.row(request, doc, required));
        }
        return new PageImpl<>(rows, page.getPageable(), page.getTotalElements());
    }

    private boolean inScope(BusinessDocument doc) {
        return doc.isVisibleTo(context.requireRowScope());
    }

    private BusinessDocument loadAny(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .orElseThrow(() -> notFound(id));
    }

    private BusinessDocument load(Long id) {
        BusinessDocument doc = loadAny(id);
        if (!inScope(doc)) throw notFound(id);
        return doc;
    }

    /** In the actor's scope, or theirs to approve (or already approved by them). */
    private BusinessDocument loadForReview(Long id) {
        BusinessDocument doc = loadAny(id);
        if (!inScope(doc) && !canReview(doc)) throw notFound(id);
        return doc;
    }

    private static IllegalArgumentException notFound(Long id) {
        return new IllegalArgumentException("Document not found: " + id);
    }

    private static Pageable withDefaultSort(Pageable pageable, Sort sort) {
        Pageable p = pageable == null ? PageRequest.of(0, 25) : pageable;
        return p.getSort().isSorted() ? p : PageRequest.of(p.getPageNumber(), p.getPageSize(), sort);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Records a workflow event that is not an approval decision - a posting, a cancellation, a
     * short-close - on the same timeline the document's screen shows.
     */
    @Transactional
    public void record(BusinessDocument doc, ApprovalAction action, BusinessDocumentStatus from, String remarks) {
        recordHistory(doc, action, from, remarks, null, null);
    }

    private void recordHistory(BusinessDocument doc, ApprovalAction action, BusinessDocumentStatus from,
                               String remarks, ApprovalRequest request, Integer level) {
        historyRepository.save(new ApprovalHistory(
            doc.getId(), doc.getDocumentType(), action, from, doc.getStatus(), remarks)
            .forRequest(request == null ? null : request.getId(), level));
    }
}

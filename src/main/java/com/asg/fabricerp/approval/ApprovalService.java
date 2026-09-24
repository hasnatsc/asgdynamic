package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentRepository;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Submit / approve / reject for every document type, in one place.
 *
 * <p>Generic over {@link BusinessDocument} rather than duplicated per fabric service —
 * {@code BookingService} and {@code BpoService} used to each carry their own one-line
 * {@code submit()}, and neither had an approve/reject path at all. A third copy of the same
 * three lines was the signal to extract it, same as {@code DocumentRevisionService} before it.
 *
 * <h2>Why the role check is not {@code @PreAuthorize}</h2>
 * Every other authorization decision in this project sits on the controller as a static
 * {@code @PreAuthorize} string, deliberately, so a route's requirement is visible without
 * reading a service. That does not fit here: the required role depends on
 * {@link com.asg.fabricerp.global.documents.DocumentType#makerRole()} /
 * {@code approverRole()}, which is a property of the document being acted on, known only
 * after it is loaded — SpEL can express a bean-method call for this, but a hand-rolled
 * check reading one field is clearer than introducing that indirection for a single caller.
 *
 * <h2>Four-eyes</h2>
 * The approver may not be the document's own creator, regardless of which roles they hold.
 * asgdynamic's client-side {@code *ChangeStatus()} handlers had no such check — anyone who
 * could reach the button could approve their own submission.
 *
 * <p>Two different sources feed the checks below, deliberately: {@link #requireRole} reads
 * {@code GrantedAuthority} straight off {@code SecurityContextHolder} because role checking
 * is a Spring Security concern; {@link #assertNotSelfApproval} reads {@link OrgContext},
 * which is a pure identity/scope abstraction with no framework types in it (see its own
 * javadoc). In production {@link com.asg.fabricerp.security.SecurityOrgContext} resolves
 * both from the same authenticated principal, so they always agree — but folding
 * authorities into {@code OrgContext} to make this one class use a single source would
 * undermine the reason that interface has no Spring Security import at all.
 */
@Service
public class ApprovalService {

    private final BusinessDocumentRepository repository;
    private final ApprovalHistoryRepository historyRepository;
    private final OrgContext context;

    public ApprovalService(BusinessDocumentRepository repository,
                           ApprovalHistoryRepository historyRepository,
                           OrgContext context) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.context = context;
    }

    @Transactional
    public BusinessDocument submit(Long documentId) {
        BusinessDocument doc = load(documentId);
        requireRole(doc.getDocumentType().makerRole());
        if (doc.getLines().isEmpty()) {
            throw new IllegalStateException(
                "%s %s has no lines and cannot be submitted"
                    .formatted(doc.getDocumentType().label(), doc.getDocumentNo()));
        }

        BusinessDocumentStatus from = doc.getStatus();
        doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        recordHistory(doc, ApprovalAction.SUBMITTED, from, null);
        return repository.save(doc);
    }

    @Transactional
    public BusinessDocument approve(Long documentId, String remarks) {
        BusinessDocument doc = load(documentId);
        requireRole(doc.getDocumentType().approverRole());
        assertNotSelfApproval(doc);

        BusinessDocumentStatus from = doc.getStatus();
        doc.transitionTo(BusinessDocumentStatus.APPROVED);
        recordHistory(doc, ApprovalAction.APPROVED, from, remarks);
        return repository.save(doc);
    }

    @Transactional
    public BusinessDocument reject(Long documentId, String remarks) {
        BusinessDocument doc = load(documentId);
        requireRole(doc.getDocumentType().approverRole());

        BusinessDocumentStatus from = doc.getStatus();
        doc.transitionTo(BusinessDocumentStatus.REJECTED);
        recordHistory(doc, ApprovalAction.REJECTED, from, remarks);
        return repository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> historyOf(Long documentId) {
        load(documentId);   // scope check — throws if the document is not in this org
        return historyRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    private BusinessDocument load(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    private void requireRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = auth != null && auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(role::equals);
        if (!granted) {
            throw new AccessDeniedException("This action requires " + role);
        }
    }

    /**
     * The creator of a document may not approve it, regardless of role. Rejection is
     * deliberately exempt: sending your own submission back for correction is not the
     * segregation-of-duties risk that self-approval is.
     */
    private void assertNotSelfApproval(BusinessDocument doc) {
        String current = context.username();
        if (current != null && current.equalsIgnoreCase(doc.getCreatedBy())) {
            throw new AccessDeniedException(
                "Segregation of duties: %s created %s and cannot approve it"
                    .formatted(current, doc.getDocumentNo()));
        }
    }

    private void recordHistory(BusinessDocument doc, ApprovalAction action,
                               BusinessDocumentStatus from, String remarks) {
        historyRepository.save(new ApprovalHistory(
            doc.getId(), doc.getDocumentType(), action, from, doc.getStatus(), remarks));
    }
}

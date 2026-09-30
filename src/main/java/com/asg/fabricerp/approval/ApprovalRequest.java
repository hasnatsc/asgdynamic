package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.global.documents.DocumentType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One submission of one document - asfl-erp's {@code core.approval_request}.
 *
 * <p>Records which matrix it was submitted under ({@code matrixId}, null for the default rule) and
 * how many levels its amount needs, and is decided under exactly those: re-resolving at decision
 * time would let a matrix edited half way through, or the approver's own team, change the rules
 * for a document already on its way.
 */
@Entity
@Table(name = "apr_requests")
public class ApprovalRequest extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40, updatable = false)
    private DocumentType documentType;

    @Column(name = "business_unit_id", updatable = false)
    private Long businessUnitId;

    /** The document's marketing team, copied at submission. */
    @Column(name = "owning_team_id", updatable = false)
    private Long owningTeamId;

    /** The governing matrix; null when the type has none and the default rule applies. */
    @Column(name = "matrix_id", updatable = false)
    private Long matrixId;

    @Column(precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "raised_by_user_id", updatable = false)
    private Long raisedByUserId;

    @Column(name = "raised_by", length = 100, updatable = false)
    private String raisedBy;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Column(name = "total_levels", nullable = false, updatable = false)
    private int totalLevels;

    @Column(nullable = false)
    private boolean pending = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ApprovalDecision outcome;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    /**
     * Who must sign the current level, copied from the matrix whenever the request moves: the role
     * or the named user; both null under the default rule (anyone with the screen's Approve verb)
     * and once settled. Denormalised so the inbox is one indexed query over every pending request
     * rather than a matrix lookup per request - the matrix stays the authority, this is its index.
     */
    @Column(name = "current_role_id")
    private Long currentRoleId;

    @Column(name = "current_user_id")
    private Long currentUserId;

    /*
     * The clock of the level the request is at, restarted whenever it moves to another level. A
     * level without a time limit leaves remind/due null; the deadline job never looks at it.
     */
    @Column(name = "level_started_at")
    private LocalDateTime levelStartedAt;

    /** Three quarters of the way to the limit: the approvers are reminded once. */
    @Column(name = "level_remind_at")
    private LocalDateTime levelRemindAt;

    @Column(name = "level_due_at")
    private LocalDateTime levelDueAt;

    @Column(name = "level_reminded", nullable = false)
    private boolean levelReminded;

    /** The limit ran out and its action was taken; a level times out once. */
    @Column(name = "level_timed_out", nullable = false)
    private boolean levelTimedOut;

    /** The route names the level's escalation target, not the matrix's approver. */
    @Column(nullable = false)
    private boolean escalated;

    protected ApprovalRequest() { }

    public ApprovalRequest(Long organizationId, Long documentId, DocumentType documentType, Long businessUnitId,
                           Long owningTeamId, Long matrixId, BigDecimal amount,
                           Long raisedByUserId, String raisedBy, int totalLevels) {
        if (totalLevels < 1) {
            throw new IllegalArgumentException("An approval needs at least one level");
        }
        this.organizationId = organizationId;
        this.documentId = documentId;
        this.documentType = documentType;
        this.businessUnitId = businessUnitId;
        this.owningTeamId = owningTeamId;
        this.matrixId = matrixId;
        this.amount = amount;
        this.raisedByUserId = raisedByUserId;
        this.raisedBy = raisedBy;
        this.totalLevels = totalLevels;
    }

    /** Whether this person raised it - who may then decide no level of it, however senior (four-eyes). */
    public boolean wasRaisedBy(Approver.Actor actor) {
        return (raisedByUserId != null && raisedByUserId.equals(actor.userId()))
            || (raisedBy != null && raisedBy.equalsIgnoreCase(actor.username()));
    }

    /**
     * Records a decision on the current level and moves the request on. The caller has already
     * checked the actor may decide it; this is the state machine, and refuses to run on a request
     * already settled.
     */
    public void decide(ApprovalDecision decision) {
        if (!pending) {
            throw new IllegalStateException("This approval is already settled");
        }
        if (decision == ApprovalDecision.APPROVED && currentLevel < totalLevels) {
            currentLevel++;
            return;
        }
        pending = false;
        outcome = decision;
        settledAt = LocalDateTime.now();
    }

    /** Points the request at the approver its current level needs. */
    public void routeTo(Approver approver) {
        this.currentRoleId = approver != null && approver.kind() == Approver.Kind.ROLE ? approver.roleId() : null;
        this.currentUserId = approver != null && approver.kind() == Approver.Kind.USER ? approver.userId() : null;
    }

    /** A settled request waits for nobody - and has no clock running. */
    public void clearRoute() {
        this.currentRoleId = null;
        this.currentUserId = null;
        this.levelRemindAt = null;
        this.levelDueAt = null;
        this.escalated = false;
    }

    /**
     * Starts the clock of the level just reached: {@code limitMinutes} from {@code now}, or no limit.
     * The reminder falls at three quarters of the limit - on a one-hour limit, after 45 minutes.
     */
    public void startClock(Integer limitMinutes, LocalDateTime now) {
        this.levelStartedAt = now;
        this.levelReminded = false;
        this.levelTimedOut = false;
        this.escalated = false;
        if (limitMinutes == null) {
            this.levelRemindAt = null;
            this.levelDueAt = null;
        } else {
            this.levelDueAt = now.plusMinutes(limitMinutes);
            this.levelRemindAt = now.plusSeconds(limitMinutes * 45L);
        }
    }

    public void markReminded() {
        this.levelReminded = true;
    }

    /** The limit ran out; whatever the action, it is not taken twice. */
    public void markTimedOut() {
        this.levelReminded = true;
        this.levelTimedOut = true;
    }

    /** The level now waits for {@code target} instead of the matrix's approver. */
    public void escalateTo(Approver target) {
        routeTo(target);
        this.escalated = true;
    }

    /** Who the route names now - the escalation target, once escalated. */
    public Approver currentRoute() {
        if (currentUserId != null) return Approver.user(currentUserId);
        if (currentRoleId != null) return Approver.role(currentRoleId);
        return null;
    }

    public boolean isOverdue(LocalDateTime now) {
        return pending && levelDueAt != null && !now.isBefore(levelDueAt);
    }

    public boolean isFinalLevel()         { return currentLevel >= totalLevels; }
    public Long getCurrentRoleId()        { return currentRoleId; }
    public Long getCurrentUserId()        { return currentUserId; }
    public Long getOrganizationId()       { return organizationId; }
    public Long getDocumentId()           { return documentId; }
    public DocumentType getDocumentType() { return documentType; }
    public Long getBusinessUnitId()       { return businessUnitId; }
    public Long getOwningTeamId()         { return owningTeamId; }
    public Long getMatrixId()             { return matrixId; }
    public BigDecimal getAmount()         { return amount; }
    public Long getRaisedByUserId()       { return raisedByUserId; }
    public String getRaisedBy()           { return raisedBy; }
    public int getCurrentLevel()          { return currentLevel; }
    public int getTotalLevels()           { return totalLevels; }
    public boolean isPending()            { return pending; }
    public ApprovalDecision getOutcome()  { return outcome; }
    public LocalDateTime getSettledAt()   { return settledAt; }
    public LocalDateTime getLevelStartedAt() { return levelStartedAt; }
    public LocalDateTime getLevelRemindAt() { return levelRemindAt; }
    public LocalDateTime getLevelDueAt()  { return levelDueAt; }
    public boolean isLevelReminded()      { return levelReminded; }
    public boolean isLevelTimedOut()      { return levelTimedOut; }
    public boolean isEscalated()          { return escalated; }
}

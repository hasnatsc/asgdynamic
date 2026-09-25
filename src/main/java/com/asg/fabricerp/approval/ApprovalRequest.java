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

    public boolean isFinalLevel()         { return currentLevel >= totalLevels; }
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
}

package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One signature a matrix requires - asfl-erp's {@code core.approval_level}.
 *
 * <p>Names a role OR one user, never both and never neither (the table's check says so too): a
 * role lets whoever holds it sign, which survives people changing jobs; a named user is for the
 * signature that is genuinely one person's. An optional amount band limits the level to documents
 * whose total falls inside it.
 *
 * <p>An optional time limit says how long the level may wait, and {@link TimeoutAction} what then
 * happens - escalating names its own role or user, on the same one-or-the-other rule.
 */
@Entity
@Table(name = "apr_matrix_levels")
public class ApprovalLevel extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_id", nullable = false)
    private ApprovalMatrix matrix;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "min_amount", precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    /** How long the level may wait for a decision; null for no limit. */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeout_action", nullable = false, length = 20)
    private TimeoutAction timeoutAction = TimeoutAction.REMIND;

    @Column(name = "escalate_role_id")
    private Long escalateRoleId;

    @Column(name = "escalate_user_id")
    private Long escalateUserId;

    protected ApprovalLevel() { }

    /** A level with no time limit. */
    public ApprovalLevel(Long roleId, Long userId, BigDecimal minAmount, BigDecimal maxAmount) {
        this(roleId, userId, minAmount, maxAmount, null, TimeoutAction.REMIND, null, null);
    }

    public ApprovalLevel(Long roleId, Long userId, BigDecimal minAmount, BigDecimal maxAmount,
                         Integer timeLimitMinutes, TimeoutAction timeoutAction,
                         Long escalateRoleId, Long escalateUserId) {
        if ((roleId == null) == (userId == null)) {
            throw new IllegalArgumentException("An approval level names a role or one user - never both, never neither");
        }
        if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException(("A level banded from %s to %s covers no amount: an approver no document "
                + "can reach never signs").formatted(minAmount, maxAmount));
        }
        if (timeLimitMinutes != null && timeLimitMinutes < 1) {
            throw new IllegalArgumentException("A time limit is at least one minute");
        }
        TimeoutAction action = timeoutAction == null ? TimeoutAction.REMIND : timeoutAction;
        if (action == TimeoutAction.ESCALATE) {
            if ((escalateRoleId == null) == (escalateUserId == null)) {
                throw new IllegalArgumentException("Escalating hands the level to a role or one person - choose which");
            }
        } else {
            escalateRoleId = null;
            escalateUserId = null;
        }
        this.roleId = roleId;
        this.userId = userId;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.timeLimitMinutes = timeLimitMinutes;
        this.timeoutAction = action;
        this.escalateRoleId = escalateRoleId;
        this.escalateUserId = escalateUserId;
    }

    /** Takes another level's approver and band, keeping this row's matrix and sequence. */
    void copyFrom(ApprovalLevel other) {
        this.roleId = other.roleId;
        this.userId = other.userId;
        this.minAmount = other.minAmount;
        this.maxAmount = other.maxAmount;
        this.timeLimitMinutes = other.timeLimitMinutes;
        this.timeoutAction = other.timeoutAction;
        this.escalateRoleId = other.escalateRoleId;
        this.escalateUserId = other.escalateUserId;
    }

    void attach(ApprovalMatrix matrix, int sequence) {
        this.matrix = matrix;
        this.sequence = sequence;
    }

    /** Whether a document of this amount needs this level. An unbanded level applies to every amount. */
    public boolean appliesTo(BigDecimal amount) {
        if (amount == null) return true;
        if (minAmount != null && amount.compareTo(minAmount) < 0) return false;
        return maxAmount == null || amount.compareTo(maxAmount) <= 0;
    }

    public Approver approver() {
        return roleId != null ? Approver.role(roleId) : Approver.user(userId);
    }

    /** Who the level goes to when its time runs out and it escalates; null otherwise. */
    public Approver escalationApprover() {
        if (timeoutAction != TimeoutAction.ESCALATE) return null;
        return escalateRoleId != null ? Approver.role(escalateRoleId) : Approver.user(escalateUserId);
    }

    public boolean hasTimeLimit()     { return timeLimitMinutes != null; }

    public int getSequence()          { return sequence; }
    public Long getRoleId()           { return roleId; }
    public Long getUserId()           { return userId; }
    public BigDecimal getMinAmount()  { return minAmount; }
    public BigDecimal getMaxAmount()  { return maxAmount; }
    public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
    public TimeoutAction getTimeoutAction() { return timeoutAction; }
    public Long getEscalateRoleId()   { return escalateRoleId; }
    public Long getEscalateUserId()   { return escalateUserId; }
}

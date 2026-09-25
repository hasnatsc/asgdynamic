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

    protected ApprovalLevel() { }

    public ApprovalLevel(Long roleId, Long userId, BigDecimal minAmount, BigDecimal maxAmount) {
        if ((roleId == null) == (userId == null)) {
            throw new IllegalArgumentException("An approval level names a role or one user - never both, never neither");
        }
        if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException(("A level banded from %s to %s covers no amount: an approver no document "
                + "can reach never signs").formatted(minAmount, maxAmount));
        }
        this.roleId = roleId;
        this.userId = userId;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    /** Takes another level's approver and band, keeping this row's matrix and sequence. */
    void copyFrom(ApprovalLevel other) {
        this.roleId = other.roleId;
        this.userId = other.userId;
        this.minAmount = other.minAmount;
        this.maxAmount = other.maxAmount;
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

    public int getSequence()          { return sequence; }
    public Long getRoleId()           { return roleId; }
    public Long getUserId()           { return userId; }
    public BigDecimal getMinAmount()  { return minAmount; }
    public BigDecimal getMaxAmount()  { return maxAmount; }
}

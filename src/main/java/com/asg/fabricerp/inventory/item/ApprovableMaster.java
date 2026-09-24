package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A master record that must be approved before it is trusted — items, brands, models and the
 * yarn masters. SpindleERP repeats the same four approval columns on each of those entities
 * and lets anyone approve anything, including their own entry. Declared once here, and the
 * approval enforces the same four-eyes rule as documents do ({@code ApprovalService}).
 */
@MappedSuperclass
public abstract class ApprovableMaster extends BaseOrgEntity {

    @Column(name = "approved", nullable = false)
    private Boolean approved = Boolean.FALSE;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public Boolean getApproved()          { return approved; }
    public String getApprovedBy()         { return approvedBy; }
    public LocalDateTime getApprovedAt()  { return approvedAt; }

    /**
     * @throws IllegalStateException if already approved, or if {@code approver} created it
     */
    public void approve(String approver, LocalDateTime at) {
        if (Boolean.TRUE.equals(approved)) {
            throw new IllegalStateException("This record is already approved.");
        }
        if (Objects.equals(approver, getCreatedBy())) {
            throw new IllegalStateException("You cannot approve a record you created yourself.");
        }
        this.approved = Boolean.TRUE;
        this.approvedBy = approver;
        this.approvedAt = at;
    }
}

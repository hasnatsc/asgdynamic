package com.asg.fabricerp.marketing;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A person who approves one marketing team's documents - asfl-erp's per-team approval matrix
 * (V27), reduced to the part this system routes on.
 *
 * <p>A team with approvers has its bookings decided by them alone; a team with none falls back to
 * the business-wide rule, anyone holding the screen's {@code APPROVE} verb. See
 * {@link TeamApprovalRule}.
 */
@Entity
@Table(
    name = "org_marketing_team_approvers",
    uniqueConstraints = @UniqueConstraint(name = "uk_mt_approver", columnNames = {"team_id", "user_id"}))
public class MarketingTeamApprover extends BaseOrgEntity {

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    protected MarketingTeamApprover() { }

    public MarketingTeamApprover(Long organizationId, Long teamId, Long userId) {
        setOrganizationId(organizationId);
        this.teamId = teamId;
        this.userId = userId;
    }

    public Long getTeamId() { return teamId; }
    public Long getUserId() { return userId; }
}

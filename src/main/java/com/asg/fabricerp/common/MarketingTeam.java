package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One of the marketing teams a restricted user belongs to exactly one of (ADM-4), and which a
 * Booking and everything raised from it is stamped with (ADM-7). Seeded by
 * {@code V12__admin_controls.sql} from the legacy {@code marketingGroup} list; maintained on the
 * Marketing teams screen since {@code V23}.
 *
 * <p>Membership is not held here. A user's team is their {@code MARKETING_TEAM} data-scope grant,
 * which is effective-dated and limited to one open grant per user; the team screen writes that
 * grant rather than keeping a second list that could disagree with it.
 *
 * <p>asfl-erp's team also carries a booking target and, deliberately, no incentive: SLS-2 pays
 * incentive on realised proceeds, not booking value, so it belongs to Commercial.
 */
@Entity
@Table(
    name = "org_marketing_teams",
    uniqueConstraints = @UniqueConstraint(name = "uk_marketing_team_org_code", columnNames = {"organization_id", "code"}))
public class MarketingTeam extends BaseOrgEntity {

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    /** The team leader, a user. Informational: approval authority is the approver list, not this. */
    @Column(name = "leader_user_id")
    private Long leaderUserId;

    /** Booking target for the current period, in the functional currency. Null before one is set. */
    @DecimalMin("0")
    @Column(name = "booking_target", precision = 18, scale = 2)
    private BigDecimal bookingTarget;

    @Size(max = 500)
    @Column(length = 500)
    private String remarks;

    protected MarketingTeam() { }

    public MarketingTeam(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode()                  { return code; }
    public void setCode(String v)            { this.code = v; }
    public String getName()                  { return name; }
    public void setName(String v)            { this.name = v; }
    public Long getLeaderUserId()            { return leaderUserId; }
    public void setLeaderUserId(Long v)      { this.leaderUserId = v; }
    public BigDecimal getBookingTarget()     { return bookingTarget; }
    public void setBookingTarget(BigDecimal v) { this.bookingTarget = v; }
    public String getRemarks()               { return remarks; }
    public void setRemarks(String v)         { this.remarks = v; }
}

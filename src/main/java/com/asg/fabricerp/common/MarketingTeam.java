package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One of the marketing teams a restricted user belongs to exactly one of (ADM-4), and which a
 * Booking and everything raised from it is stamped with (ADM-7). Seeded by
 * {@code V12__admin_controls.sql} from the legacy {@code marketingGroup} list.
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

    protected MarketingTeam() { }

    public MarketingTeam(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode()       { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName()       { return name; }
    public void setName(String v) { this.name = v; }
}

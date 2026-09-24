package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The tenant every {@link OrgScoped} row's {@code organizationId} points at. Extends
 * {@link AuditableEntity} directly, not {@link BaseOrgEntity} — an organization does not belong
 * to a tenant, it is one.
 *
 * <p>{@code OrgScoped}'s javadoc explains why scoping elsewhere is a plain id, not a
 * {@code @ManyToOne} — this entity existing does not change that. It is not there for other
 * rows to join against; it is there so the id every row already carries resolves to a real,
 * manageable record instead of a bare number nothing points back to (which is what
 * {@code DevUserSeeder}'s hardcoded {@code organizationId = 1L} was until now).
 */
@Entity
@Table(
    name = "org_organizations",
    uniqueConstraints = @UniqueConstraint(name = "uk_org_code", columnNames = "code"))
public class Organization extends AuditableEntity {

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    protected Organization() { }

    public Organization(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode()               { return code; }
    public void setCode(String v)         { this.code = v; }
    public String getName()               { return name; }
    public void setName(String v)         { this.name = v; }
    public Boolean getActive()            { return active; }
    public void setActive(Boolean active) { this.active = active; }
}

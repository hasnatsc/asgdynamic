package com.asg.fabricerp.security;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The catalog of every {@code GrantedAuthority} string a {@code @PreAuthorize} annotation in
 * this codebase actually checks — a row here documents what a hardcoded {@code hasRole(...)}
 * literal means, it does not create new enforcement. {@link #name} must match a literal already
 * compiled into a controller for a row to do anything; see {@code PermissionController}'s
 * javadoc for why the admin screen for this entity is read-only.
 *
 * <p>Global, not tenant-scoped ({@link com.asg.fabricerp.common.BaseOrgEntity} is not used
 * here) — a permission means the same thing for every organization.
 */
@Entity
@Table(
    name = "sec_fabric_permissions",
    uniqueConstraints = @UniqueConstraint(name = "uk_fab_permission_name", columnNames = "name"))
public class Permission extends AuditableEntity {

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String name;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String module;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    protected Permission() { }

    public Permission(String name, String module, String description) {
        this.name = name;
        this.module = module;
        this.description = description;
    }

    public String getName()                { return name; }
    public String getModule()              { return module; }
    public String getDescription()         { return description; }
    public Boolean getActive()             { return active; }
    public void setActive(Boolean active)  { this.active = active; }
}

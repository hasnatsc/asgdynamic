package com.asg.fabricerp.security;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

/**
 * A named, admin-editable bundle of {@link Permission}s, assignable to a {@link FabricUser}.
 * Replaces the flat {@code FabricUser.authorities} set: a user's effective
 * {@code GrantedAuthority} set is derived from the union of their roles' permissions at login
 * (see {@link FabricUserPrincipal}), not stored per-user.
 *
 * <p>Global, not tenant-scoped, same reasoning as {@link Permission}.
 */
@Entity
@Table(
    name = "sec_fabric_roles",
    uniqueConstraints = @UniqueConstraint(name = "uk_fab_role_name", columnNames = "name"))
public class Role extends AuditableEntity {

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String name;

    @Size(max = 255)
    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "sec_fabric_role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() { }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName()                       { return name; }
    public void setName(String v)                 { this.name = v; }
    public String getDescription()                { return description; }
    public void setDescription(String v)          { this.description = v; }
    public Boolean getActive()                    { return active; }
    public void setActive(Boolean active)         { this.active = active; }
    public Set<Permission> getPermissions()       { return permissions; }

    public void grant(Permission permission)      { this.permissions.add(permission); }
    public void revoke(Permission permission)     { this.permissions.remove(permission); }

    /** Replaces the entire permission set — used by the admin screen's save. */
    public void setPermissions(Set<Permission> permissions) {
        this.permissions = new HashSet<>(permissions);
    }
}

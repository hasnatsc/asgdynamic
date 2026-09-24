package com.asg.fabricerp.security;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A named, admin-editable bundle of per-screen verb grants ({@link RoleScreenGrant}),
 * assignable to a {@link FabricUser}. A user's effective {@code GrantedAuthority} set is
 * derived from the union of their roles' grants at login (see {@link FabricUserPrincipal}), not
 * stored per-user.
 *
 * <p>Global, not tenant-scoped — a role means the same thing for every organization.
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

    /** Owned — a grant only means something in the context of the role that holds it. */
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RoleScreenGrant> screenGrants = new HashSet<>();

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
    public Set<RoleScreenGrant> getScreenGrants()  { return screenGrants; }

    /** Grants (or replaces the grant for) one screen. Any verb implies VIEW — see {@link RoleScreenGrant}. */
    public void grant(Screen screen, Verb... verbs) {
        screenGrants.removeIf(g -> g.getScreen() == screen);
        screenGrants.add(new RoleScreenGrant(this, screen, verbs));
    }

    public void revoke(Screen screen) {
        screenGrants.removeIf(g -> g.getScreen() == screen);
    }

    /** Replaces the entire grant set — used by the admin screen's save. */
    public void setGrants(Map<Screen, Set<Verb>> grants) {
        screenGrants.clear();
        for (Map.Entry<Screen, Set<Verb>> entry : grants.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                grant(entry.getKey(), entry.getValue().toArray(new Verb[0]));
            }
        }
    }

    /** {@code Screen -> grantedVerbs()}, for the admin form and for flattening authorities. */
    public Map<Screen, Set<Verb>> grantsByScreen() {
        Map<Screen, Set<Verb>> out = new HashMap<>();
        for (RoleScreenGrant grant : screenGrants) {
            out.put(grant.getScreen(), grant.grantedVerbs());
        }
        return out;
    }
}

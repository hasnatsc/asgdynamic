package com.asg.fabricerp.security;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.EnumSet;
import java.util.Set;

/**
 * What one {@link Role} may do on one {@link Screen} — ported from asfl-erp's admin module's
 * {@code RolePermission}. Five booleans rather than five rows: the permission check is the
 * hottest question in the system and this keeps it one row, and a screen's grant is edited as a
 * unit (an admin ticking boxes states the whole answer for that screen at once).
 *
 * <p>Owned by {@link Role} (not a shared catalog row like the {@code Permission} entity this
 * replaced) — a grant only means something in the context of the role that holds it.
 */
@Entity
@Table(
    name = "sec_fabric_role_screen_grants",
    uniqueConstraints = @UniqueConstraint(name = "uk_fab_grant_role_screen", columnNames = {"role_id", "screen_code"}))
public class RoleScreenGrant extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, updatable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_code", nullable = false, length = 20, updatable = false)
    private Screen screen;

    @Column(name = "can_view", nullable = false)
    private boolean canView;

    @Column(name = "can_create", nullable = false)
    private boolean canCreate;

    @Column(name = "can_amend", nullable = false)
    private boolean canAmend;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete;

    @Column(name = "can_approve", nullable = false)
    private boolean canApprove;

    protected RoleScreenGrant() { }

    RoleScreenGrant(Role role, Screen screen, Verb... verbs) {
        this.role = role;
        this.screen = screen;
        setVerbs(verbs);
    }

    /**
     * Replaces the granted verbs. Any verb implies VIEW — granting APPROVE without VIEW would
     * produce a user who may approve a document they cannot open.
     */
    final void setVerbs(Verb... verbs) {
        Set<Verb> granted = verbs.length == 0 ? EnumSet.noneOf(Verb.class) : EnumSet.copyOf(Set.of(verbs));
        this.canCreate = granted.contains(Verb.CREATE);
        this.canAmend = granted.contains(Verb.AMEND);
        this.canDelete = granted.contains(Verb.DELETE);
        this.canApprove = granted.contains(Verb.APPROVE);
        this.canView = granted.contains(Verb.VIEW) || !granted.isEmpty();
    }

    public boolean permits(Verb verb) {
        return switch (verb) {
            case VIEW -> canView;
            case CREATE -> canCreate;
            case AMEND -> canAmend;
            case DELETE -> canDelete;
            case APPROVE -> canApprove;
        };
    }

    public Set<Verb> grantedVerbs() {
        Set<Verb> verbs = EnumSet.noneOf(Verb.class);
        for (Verb verb : Verb.values()) {
            if (permits(verb)) {
                verbs.add(verb);
            }
        }
        return verbs;
    }

    public Role getRole()          { return role; }
    public Screen getScreen()      { return screen; }
    public boolean isCanView()     { return canView; }
    public boolean isCanCreate()   { return canCreate; }
    public boolean isCanAmend()    { return canAmend; }
    public boolean isCanDelete()   { return canDelete; }
    public boolean isCanApprove()  { return canApprove; }
}

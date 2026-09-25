package com.asg.fabricerp.party;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One capacity a party acts in.
 *
 * <p>A row rather than booleans on the party, because booleans cannot carry the three facts that
 * matter: <em>since when</em>, <em>under what identifier</em>, and <em>is it still current</em>.
 * A supplier that stopped being approved last March is not a supplier that never was, and
 * {@code is_supplier = false} loses the difference - along with the purchase orders that are only
 * explicable if the role once existed.
 *
 * <p>{@code roleCode} is the party's identifier <em>in that capacity</em>: the legacy system gave
 * the same company a customer code and a supplier code, both already printed on documents.
 */
@Entity
@Table(
    name = "pty_party_roles",
    indexes = @Index(name = "ix_pty_role_party", columnList = "party_id"))
public class PartyRole extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20, updatable = false)
    private PartyRoleType roleType;

    /** {@code MARKETING} or {@code COMMERCIAL} on a customer; null on every other role. */
    @Column(length = 20, updatable = false)
    private String qualifier;

    @Column(name = "role_code", length = 40)
    private String roleCode;

    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    @Column(name = "revoked_on")
    private LocalDate revokedOn;

    @Column(name = "is_current", nullable = false)
    private Boolean current = Boolean.TRUE;

    protected PartyRole() { }

    PartyRole(Party party, PartyRoleType roleType, String qualifier, String roleCode, LocalDate grantedOn) {
        if (roleType.isQualified()
                && !PartyRoleType.MARKETING.equals(qualifier) && !PartyRoleType.COMMERCIAL.equals(qualifier)) {
            throw new IllegalArgumentException(roleType + " must be qualified MARKETING or COMMERCIAL");
        }
        if (!roleType.isQualified() && qualifier != null) {
            throw new IllegalArgumentException(roleType + " takes no qualifier, got '" + qualifier + "'");
        }
        this.party = party;
        this.roleType = roleType;
        this.qualifier = qualifier;
        this.roleCode = roleCode;
        this.grantedOn = grantedOn;
        setOrganizationId(party.getOrganizationId());
    }

    public boolean matches(PartyRoleType type, String q) {
        return this.roleType == type && Objects.equals(this.qualifier, q);
    }

    void revoke(LocalDate on) {
        this.current = Boolean.FALSE;
        this.revokedOn = on;
    }

    void restore() {
        this.current = Boolean.TRUE;
        this.revokedOn = null;
    }

    public Party getParty()              { return party; }
    public PartyRoleType getRoleType()   { return roleType; }
    public String getQualifier()         { return qualifier; }
    public String getRoleCode()          { return roleCode; }
    public LocalDate getGrantedOn()      { return grantedOn; }
    public LocalDate getRevokedOn()      { return revokedOn; }
    public boolean isCurrent()           { return Boolean.TRUE.equals(current); }
}

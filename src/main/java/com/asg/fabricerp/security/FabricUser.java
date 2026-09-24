package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

/**
 * A login. Deliberately minimal: username, password hash, and the operating scope
 * ({@link com.asg.fabricerp.common.OrgContext} is built from this) plus a flat set of
 * granted authorities.
 *
 * <h2>What is intentionally not here</h2>
 * SpindleERP models {@code Role} and {@code Permission} as separate entities with a
 * {@code DynamicAuthorizationManager} that matches URL patterns against a permission
 * table at request time. That table's own comment records why it currently fails
 * <b>open</b> on an unmatched route: flipping to deny-by-default risked locking users out
 * of screens whose permission row had not been seeded yet, so it grants and logs a warning
 * instead.
 *
 * <p>This system does not carry that risk forward. Authorization is declared with
 * {@code @PreAuthorize} directly on each controller method (see
 * {@code BookingController}, {@code FabricAttributeController}), so every route is either
 * explicitly guarded or explicitly {@code permitAll} in {@link SecurityConfig} — there is
 * no third, ungoverned state. The cost is that a role name is a plain string on the
 * annotation rather than a row in an editable table; a future {@code Role}/{@code Permission}
 * model can replace the flat {@link #authorities} set below without touching this class's
 * shape.
 *
 * <h2>Business unit / warehouse</h2>
 * These are the user's <b>default</b> operating scope, read by {@link SecurityOrgContext}.
 * The legacy asgdynamic UI allowed switching unit/store mid-session — at the cost of a
 * forced re-login, which the FabricERP migration spec flagged as worth fixing. Runtime
 * override is not implemented yet; every user currently operates in their default scope
 * for the whole session. Revisit when a user needs more than one.
 */
@Entity
@Table(
    name = "sec_fabric_users",
    uniqueConstraints = @UniqueConstraint(name = "uk_fab_user_username", columnNames = "username"))
public class FabricUser extends BaseOrgEntity {

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String username;

    /** BCrypt hash. Never a plaintext value, never logged. */
    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Size(max = 150)
    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "business_unit_id", nullable = false)
    private Long businessUnitId;

    /** Two-letter code embedded in every document number, e.g. "AF". */
    @NotBlank
    @Size(max = 4)
    @Column(name = "business_unit_code", nullable = false, length = 4)
    private String businessUnitCode;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = Boolean.FALSE;

    /**
     * Stored pre-formed as Spring Security authority strings, e.g. {@code "ROLE_BOOKING_MAKER"}.
     * {@code hasRole('BOOKING_MAKER')} compares against this literally — it prepends
     * {@code ROLE_} to the argument, not to what is stored, so the prefix must already be here.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sec_fabric_user_authorities", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "authority", length = 60)
    private Set<String> authorities = new HashSet<>();

    protected FabricUser() { }

    public FabricUser(String username, String passwordHash, Long businessUnitId, String businessUnitCode) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.businessUnitId = businessUnitId;
        this.businessUnitCode = businessUnitCode;
    }

    public String getUsername()               { return username; }
    public String getPasswordHash()            { return passwordHash; }
    public void setPasswordHash(String v)      { this.passwordHash = v; }
    public String getFullName()                { return fullName; }
    public void setFullName(String v)          { this.fullName = v; }
    public Long getBusinessUnitId()            { return businessUnitId; }
    public void setBusinessUnitId(Long v)      { this.businessUnitId = v; }
    public String getBusinessUnitCode()        { return businessUnitCode; }
    public void setBusinessUnitCode(String v)  { this.businessUnitCode = v; }
    public Long getWarehouseId()               { return warehouseId; }
    public void setWarehouseId(Long v)         { this.warehouseId = v; }
    public Boolean getAccountLocked()          { return accountLocked; }
    public Set<String> getAuthorities()        { return authorities; }

    public void grant(String authority) { this.authorities.add(authority); }
}

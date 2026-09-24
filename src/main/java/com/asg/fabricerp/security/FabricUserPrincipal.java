package com.asg.fabricerp.security;

import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The authenticated principal, carrying the operating scope alongside the credentials
 * Spring Security needs.
 *
 * <p>SpindleERP's {@code CustomUserDetailsService} builds Spring Security's own generic
 * {@code org.springframework.security.core.userdetails.User} as the principal, which holds
 * only username/password/authorities — the organization, business unit and warehouse are
 * lost the moment authentication succeeds, and every later access to them goes back through
 * a repository ({@code ContextProvider}'s six static repositories). Carrying them on the
 * principal instead means {@link SecurityOrgContext} reads them for free from
 * {@code SecurityContextHolder} on every call, no extra query.
 *
 * <h2>Rebuilt on every request</h2>
 * {@link SessionPrincipalRefreshFilter} replaces this object at the start of each request
 * rather than trusting the one stored in the session at login. A role revoked this morning
 * has to stop granting this afternoon, and a lock has to end a session that is already
 * open — an authority list captured at login outlives both by however long the session lasts.
 */
public class FabricUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final Long organizationId;
    private final Long businessUnitId;
    private final String businessUnitCode;
    private final Long warehouseId;
    private final boolean locked;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;
    private final RowScope rowScope;
    private final boolean mustChangePassword;

    /** A principal with no scope grants — enough for an unrestricted user, and for tests. */
    public FabricUserPrincipal(FabricUser user) {
        this(user, List.of(), LocalDate.now());
    }

    /**
     * @param scopes every scope grant the user has ever held; only those held {@code on} count
     */
    public FabricUserPrincipal(FabricUser user, List<DataScope> scopes, LocalDate on) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.organizationId = user.getOrganizationId();
        this.businessUnitId = user.getBusinessUnitId();
        this.businessUnitCode = user.getBusinessUnitCode();
        this.warehouseId = user.getWarehouseId();
        this.locked = Boolean.TRUE.equals(user.getAccountLocked());
        this.enabled = Boolean.TRUE.equals(user.getActive());
        this.authorities = user.getRoles().stream()
            .filter(role -> Boolean.TRUE.equals(role.getActive()))
            .flatMap(role -> role.getScreenGrants().stream())
            .flatMap(grant -> grant.grantedVerbs().stream()
                .map(verb -> "SCREEN_" + grant.getScreen() + "_" + verb))
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableSet());
        this.rowScope = resolveScope(user, scopes, on);
        this.mustChangePassword = user.isMustChangePassword();
    }

    /** ADM-3, ADM-4: unrestricted is a flag, not an absence of rows — see {@link RowScope}. */
    private static RowScope resolveScope(FabricUser user, List<DataScope> scopes, LocalDate on) {
        if (user.isUnrestricted()) {
            return RowScope.unrestrictedScope();
        }
        Map<ScopeDimension, Set<Long>> values = new EnumMap<>(ScopeDimension.class);
        for (DataScope scope : scopes) {
            if (scope.isHeldOn(on)) {
                values.computeIfAbsent(scope.getDimension(), key -> new HashSet<>())
                    .add(scope.getScopeValueId());
            }
        }
        return new RowScope(false, values);
    }

    public Long getUserId()           { return userId; }
    public Long getOrganizationId()   { return organizationId; }
    public Long getBusinessUnitId()   { return businessUnitId; }
    public String getBusinessUnitCode() { return businessUnitCode; }
    public Long getWarehouseId()      { return warehouseId; }
    public RowScope getRowScope()     { return rowScope; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    @Override public String getUsername()    { return username; }
    @Override public String getPassword()    { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isEnabled()          { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}

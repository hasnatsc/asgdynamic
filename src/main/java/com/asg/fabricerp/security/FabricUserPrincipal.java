package com.asg.fabricerp.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
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

    public FabricUserPrincipal(FabricUser user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.organizationId = user.getOrganizationId();
        this.businessUnitId = user.getBusinessUnitId();
        this.businessUnitCode = user.getBusinessUnitCode();
        this.warehouseId = user.getWarehouseId();
        this.locked = Boolean.TRUE.equals(user.getAccountLocked());
        this.enabled = Boolean.TRUE.equals(user.getActive());
        this.authorities = user.getAuthorities().stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableSet());
    }

    public Long getUserId()           { return userId; }
    public Long getOrganizationId()   { return organizationId; }
    public Long getBusinessUnitId()   { return businessUnitId; }
    public String getBusinessUnitCode() { return businessUnitCode; }
    public Long getWarehouseId()      { return warehouseId; }

    @Override public String getUsername()    { return username; }
    @Override public String getPassword()    { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isEnabled()          { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}

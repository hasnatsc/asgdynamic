package com.asg.fabricerp.security;

import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * <h2>Organization is the workspace's, not the login's</h2>
 * {@link #getOrganizationId()} and the unit, store and cost centre beside it answer where the user
 * is <em>working</em> - a {@link Workspace} {@link WorkspaceResolver} has checked against their
 * grants - which is the organization their login belongs to only until they switch. Everything
 * that reads {@code OrgContext} follows the switch: lists, lookups, and the stamping of new rows.
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
    private final String fullName;
    private final String passwordHash;
    private final Long homeOrganizationId;
    private final Set<Long> organizationIds;
    private final Workspace workspace;
    private final boolean locked;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;
    private final RowScope rowScope;
    private final boolean mustChangePassword;
    private final Long photoVersion;

    /** A principal with no scope grants — enough for an unrestricted user, and for tests. */
    public FabricUserPrincipal(FabricUser user) {
        this(user, List.of(), LocalDate.now());
    }

    /** Working in the user's administrator-set home. */
    public FabricUserPrincipal(FabricUser user, List<DataScope> scopes, LocalDate on) {
        this(user, scopes, on, null);
    }

    /**
     * @param scopes    every scope grant the user has ever held; only those held {@code on} count
     * @param workspace where the user is working, already checked by {@link WorkspaceResolver};
     *                  null for their home
     */
    public FabricUserPrincipal(FabricUser user, List<DataScope> scopes, LocalDate on, Workspace workspace) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.homeOrganizationId = user.getOrganizationId();
        this.organizationIds = organizationsOf(user, scopes, on);
        this.workspace = workspace == null ? Workspace.home(user) : workspace;
        this.locked = Boolean.TRUE.equals(user.getAccountLocked());
        this.enabled = Boolean.TRUE.equals(user.getActive());
        this.authorities = user.getRoles().stream()
            .filter(role -> Boolean.TRUE.equals(role.getActive()))
            .flatMap(role -> role.getScreenGrants().stream())
            .flatMap(grant -> grant.grantedVerbs().stream()
                .map(verb -> grant.getScreen().authority(verb)))
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableSet());
        this.rowScope = resolveScope(user, scopes, on);
        this.mustChangePassword = user.isMustChangePassword();
        this.photoVersion = user.getPhotoVersion();
    }

    /**
     * ADM-3, ADM-4: unrestricted is a flag, not an absence of rows — see {@link RowScope}.
     * Organization grants are left out: they open a tenant, they do not narrow one, so a restricted
     * user holding nothing else is still unconfigured.
     */
    static RowScope resolveScope(FabricUser user, List<DataScope> scopes, LocalDate on) {
        if (user.isUnrestricted()) {
            return RowScope.unrestrictedScope();
        }
        Map<ScopeDimension, Set<Long>> values = new EnumMap<>(ScopeDimension.class);
        for (DataScope scope : scopes) {
            if (scope.isHeldOn(on) && scope.getDimension().narrowsRows()) {
                values.computeIfAbsent(scope.getDimension(), key -> new HashSet<>())
                    .add(scope.getScopeValueId());
            }
        }
        return new RowScope(false, values);
    }

    /**
     * The login's own organization, then every one granted and held {@code on}. Explicit for
     * unrestricted users too — see {@link ScopeDimension#ORGANIZATION}.
     */
    static Set<Long> organizationsOf(FabricUser user, List<DataScope> scopes, LocalDate on) {
        Set<Long> ids = new LinkedHashSet<>();
        if (user.getOrganizationId() != null) {
            ids.add(user.getOrganizationId());
        }
        for (DataScope scope : scopes) {
            if (scope.isHeldOn(on) && scope.getDimension() == ScopeDimension.ORGANIZATION) {
                ids.add(scope.getScopeValueId());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public Long getUserId()           { return userId; }
    /** Falls back to the username, so the layout never shows a blank. */
    public String getDisplayName()    { return fullName == null || fullName.isBlank() ? username : fullName; }

    /** The header's photo, versioned so a new one is fetched and an unchanged one comes from cache; null without one. */
    public String getPhotoUrl() {
        return photoVersion == null ? null : "/account/photo/" + userId + "?size=thumb&v=" + photoVersion;
    }

    /** Up to two initials, for the avatar when there is no photo: "Abul Hasnat" -> "AH". */
    public String getInitials() {
        String[] parts = getDisplayName().strip().split("\\s+");
        String first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        String last = parts.length > 1 && !parts[parts.length - 1].isEmpty() ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
    }
    /** The organization being worked in - see the class comment. */
    public Long getOrganizationId()   { return workspace.organizationId(); }
    /** The organization the login itself belongs to, whichever one is being worked in. */
    public Long getHomeOrganizationId() { return homeOrganizationId; }
    /** Every organization this user may switch into, their home first. */
    public Set<Long> getOrganizationIds() { return organizationIds; }
    public Long getBusinessUnitId()   { return workspace.businessUnitId(); }
    public String getBusinessUnitCode() { return workspace.businessUnitCode(); }
    public Long getWarehouseId()      { return workspace.warehouseId(); }
    public Long getCostCentreId()     { return workspace.costCentreId(); }
    public Workspace getWorkspace()   { return workspace; }
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

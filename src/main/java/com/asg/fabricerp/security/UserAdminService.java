package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.AccessLogEntry.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRUD for {@link FabricUser} — the "create a real login" half of the admin screens — plus the
 * rules asfl-erp's {@code UserAdministrationService} enforces around it.
 *
 * <h2>Nobody administers their own access</h2>
 * Every method that widens or restores what an account can do refuses when that account is the
 * caller's own ({@link SelfGrantException}): roles, business unit, warehouse, the unrestricted
 * flag, scope grants, lock state, password. Whoever holds {@code SECURITY_ADMIN} can grant
 * anything to anybody else; the one thing that makes a second administrator worth having is
 * that the first cannot quietly do it to themselves.
 *
 * <h2>Administrator-set passwords are temporary</h2>
 * Both at creation and on reset. The administrator typed it and knows it, so it is stamped
 * {@code mustChangePassword} and is good for nothing but choosing a new one at first login.
 */
@Service
public class UserAdminService {

    private final FabricUserRepository repository;
    private final RoleRepository roleRepository;
    private final DataScopeRepository scopes;
    private final BusinessUnitRepository businessUnits;
    private final WarehouseRepository warehouses;
    private final MarketingTeamRepository marketingTeams;
    private final PasswordEncoder passwordEncoder;
    private final AccessLogService accessLog;
    private final OrgContext context;

    public UserAdminService(FabricUserRepository repository, RoleRepository roleRepository,
                            DataScopeRepository scopes, BusinessUnitRepository businessUnits,
                            WarehouseRepository warehouses, MarketingTeamRepository marketingTeams,
                            PasswordEncoder passwordEncoder, AccessLogService accessLog,
                            OrgContext context) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.scopes = scopes;
        this.businessUnits = businessUnits;
        this.warehouses = warehouses;
        this.marketingTeams = marketingTeams;
        this.passwordEncoder = passwordEncoder;
        this.accessLog = accessLog;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<FabricUser> search(String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), query, pageable);
    }

    @Transactional(readOnly = true)
    public FabricUser get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /**
     * {@code password} required and temporary; {@code roleIds} may be empty. A restricted user
     * ({@code unrestricted} false) cannot log in until given at least one scope grant.
     */
    @Transactional
    public FabricUser create(String username, String password, String fullName,
                             Long businessUnitId, String businessUnitCode, Long warehouseId,
                             Set<Long> roleIds, boolean unrestricted) {
        requireUniqueUsername(username);
        PasswordPolicy.requireAcceptable(password);
        FabricUser user = new FabricUser(username, null, businessUnitId, businessUnitCode);
        user.setPassword(passwordEncoder.encode(password), true, LocalDateTime.now());
        user.setOrganizationId(context.requireOrganizationId());
        user.setFullName(fullName);
        user.setWarehouseId(warehouseId);
        user.setRoles(resolveRoles(roleIds));
        user.setUnrestricted(unrestricted);
        return repository.save(user);
    }

    /**
     * Everything except username and password — see {@link #resetPassword} for that.
     *
     * <p>On your own account only the full name may change. Roles, operating unit, warehouse
     * and the unrestricted flag are each a way to widen your own access, so a submission that
     * changes any of them is refused rather than partly applied.
     */
    @Transactional
    public FabricUser update(Long id, String fullName, Long businessUnitId,
                             String businessUnitCode, Long warehouseId, Set<Long> roleIds,
                             boolean unrestricted) {
        FabricUser user = get(id);
        Set<Role> roles = resolveRoles(roleIds);

        if (isSelf(id)) {
            if (!idsOf(roles).equals(idsOf(user.getRoles()))) {
                throw selfGrant(id, "roles");
            }
            if (!Objects.equals(businessUnitId, user.getBusinessUnitId())
                    || !Objects.equals(businessUnitCode, user.getBusinessUnitCode())
                    || !Objects.equals(warehouseId, user.getWarehouseId())) {
                throw selfGrant(id, "the business unit or warehouse");
            }
            if (unrestricted != user.isUnrestricted()) {
                throw selfGrant(id, "the unrestricted flag");
            }
        }

        user.setFullName(fullName);
        user.setBusinessUnitId(businessUnitId);
        user.setBusinessUnitCode(businessUnitCode);
        user.setWarehouseId(warehouseId);
        user.setRoles(roles);
        user.setUnrestricted(unrestricted);
        return repository.save(user);
    }

    /**
     * An administrator's reset: temporary, like every password somebody else chose. Your own
     * goes through the change-password page, which requires the current one — resetting it here
     * would turn a borrowed session into a permanent one.
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        refuseSelf(id, "the password (use Change password instead)");
        PasswordPolicy.requireAcceptable(newPassword);
        FabricUser user = get(id);
        user.setPassword(passwordEncoder.encode(newPassword), true, LocalDateTime.now());
        repository.save(user);
    }

    @Transactional
    public void setLocked(Long id, boolean locked, String reason) {
        refuseSelf(id, "the lock");
        FabricUser user = get(id);
        if (locked) {
            user.lock(reason == null || reason.isBlank() ? "Locked by an administrator" : reason,
                LocalDateTime.now());
        } else {
            user.unlock();
            repository.resetFailedLogins(id);
        }
        repository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        refuseSelf(id, "the account's existence");
        FabricUser user = get(id);
        user.markDeleted();
        user.setActive(false);
        repository.save(user);
    }

    // -----------------------------------------------------------------------------------------
    // Scope — ADM-3, ADM-4
    // -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DataScope> scopesOf(Long userId) {
        get(userId);
        return scopes.findByUserIdOrderByGrantedFromDesc(userId);
    }

    /**
     * Opens a scope grant.
     *
     * <p>Refused on yourself — widening your own visibility is the quiet version of granting
     * yourself a role. Refused for a second open marketing team (ADM-4); the database has a
     * partial unique index that makes that true under concurrency, and this check exists so the
     * administrator reads a sentence rather than a constraint violation.
     */
    @Transactional
    public DataScope grantScope(Long userId, ScopeDimension dimension, Long scopeValueId,
                                LocalDate from, String remarks) {
        refuseSelf(userId, "data scope");
        FabricUser user = get(userId);
        requireScopeValueExists(dimension, scopeValueId);

        List<DataScope> existing = scopes.findByUserIdOrderByGrantedFromDesc(userId);
        boolean duplicate = existing.stream().anyMatch(scope -> scope.isOpen()
            && scope.getDimension() == dimension && scope.getScopeValueId().equals(scopeValueId));
        if (duplicate) {
            throw new IllegalStateException(
                "%s already holds that %s".formatted(user.getUsername(), label(dimension)));
        }
        if (dimension == ScopeDimension.MARKETING_TEAM) {
            existing.stream()
                .filter(scope -> scope.isOpen() && scope.getDimension() == ScopeDimension.MARKETING_TEAM)
                .findFirst()
                .ifPresent(open -> {
                    throw new IllegalStateException(("%s already belongs to a marketing team. A user "
                        + "belongs to exactly one (ADM-4): revoke the existing grant first. "
                        + "Documents already raised keep the team they were raised under (ADM-7).")
                        .formatted(user.getUsername()));
                });
        }

        return scopes.save(new DataScope(userId, dimension, scopeValueId,
            from == null ? LocalDate.now() : from, remarks));
    }

    /**
     * Closes a grant from a date; the row survives. Allowed on yourself — it only narrows — but a
     * restricted account left with no scope at all is signed out on its next request.
     */
    @Transactional
    public void revokeScope(Long scopeId, LocalDate from, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to revoke a scope grant");
        }
        DataScope scope = scopes.findById(scopeId)
            .orElseThrow(() -> new IllegalArgumentException("Scope grant not found: " + scopeId));
        get(scope.getUserId());   // tenancy check: the grant's user must be in this organization
        scope.revokeFrom(from == null ? LocalDate.now() : from, reason);
        scopes.save(scope);
    }

    // -----------------------------------------------------------------------------------------

    private void requireScopeValueExists(ScopeDimension dimension, Long valueId) {
        if (dimension == null || valueId == null) {
            throw new IllegalArgumentException("A scope grant needs a dimension and a value");
        }
        Long orgId = context.requireOrganizationId();
        boolean found = switch (dimension) {
            case BUSINESS_UNIT -> businessUnits.lookup(orgId).stream().anyMatch(b -> b.getId().equals(valueId));
            case WAREHOUSE -> warehouses.lookup(orgId).stream().anyMatch(w -> w.getId().equals(valueId));
            case MARKETING_TEAM -> marketingTeams.lookup(orgId).stream().anyMatch(t -> t.getId().equals(valueId));
        };
        if (!found) {
            throw new IllegalArgumentException("No active %s with id %d".formatted(label(dimension), valueId));
        }
    }

    private static String label(ScopeDimension dimension) {
        return dimension.name().toLowerCase().replace('_', ' ');
    }

    private static boolean isSelf(Long userId) {
        Long current = CurrentUser.id();
        return current != null && current.equals(userId);
    }

    private void refuseSelf(Long userId, String what) {
        if (isSelf(userId)) {
            throw selfGrant(userId, what);
        }
    }

    /**
     * ADM-11: a refused self-grant is exactly the attempt the access log exists to record. It is
     * not a Spring authorization event, so {@link SecurityEventListener} never sees it.
     */
    private SelfGrantException selfGrant(Long userId, String what) {
        accessLog.record(userId, context.username(), Event.ACCESS_DENIED,
            "Self-administration: " + what, "Refused: nobody changes this on their own account");
        return new SelfGrantException(what);
    }

    private static Set<Long> idsOf(Set<Role> roles) {
        return roles.stream().map(Role::getId).collect(Collectors.toSet());
    }

    private Set<Role> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<Role> found = roleRepository.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new IllegalArgumentException("One or more role ids do not exist");
        }
        return Set.copyOf(found);
    }

    private void requireUniqueUsername(String username) {
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username '%s' already exists".formatted(username));
        }
    }
}

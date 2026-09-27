package com.asg.fabricerp.security;

import com.asg.fabricerp.accounts.CostCentre;
import com.asg.fabricerp.accounts.CostCentreRepository;
import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.Organization;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides where a user works: which organization, business unit, store and cost centre.
 *
 * <h2>Permitted, then chosen</h2>
 * What a user may work in is an administrator's grant ({@link DataScope}): their login's own
 * organization plus any {@link ScopeDimension#ORGANIZATION} grants, and inside each one the units,
 * stores and cost centres their scope permits - a dimension with no grant does not narrow, as
 * everywhere else. The home unit and home store an administrator set on the login are always
 * permitted. What they <em>do</em> work in is their own choice among those: for this session from
 * the header switcher, or saved as their default.
 *
 * <h2>Re-checked on every request</h2>
 * A choice is only ever a request. {@link #resolve} runs on every request and takes the first of
 * session choice, saved default and home that is still permitted, repairing what it can on the way
 * (a unit no longer held gives way to one that is, a store no longer held to none). So revoking an
 * organization moves the user out of it on their next click, and nothing they saved can outlive
 * the grant it was made under.
 */
@Service
public class WorkspaceResolver {

    /** One pickable value in the switcher. {@code businessUnitId} ties a store to its unit; null elsewhere. */
    public record Choice(Long id, String code, String name, Long businessUnitId) { }

    /** An organization the user may enter, with what they may pick inside it. */
    public record OrganizationChoices(Long id, String code, String name, List<Choice> businessUnits,
                                      List<Choice> stores, List<Choice> costCentres) { }

    private final OrganizationRepository organizations;
    private final BusinessUnitRepository businessUnits;
    private final WarehouseRepository warehouses;
    private final CostCentreRepository costCentres;
    private final UserWorkspaceRepository defaults;

    public WorkspaceResolver(OrganizationRepository organizations, BusinessUnitRepository businessUnits,
                             WarehouseRepository warehouses, CostCentreRepository costCentres,
                             UserWorkspaceRepository defaults) {
        this.organizations = organizations;
        this.businessUnits = businessUnits;
        this.warehouses = warehouses;
        this.costCentres = costCentres;
        this.defaults = defaults;
    }

    /**
     * Where the user works on this request: the session's choice if still permitted, else their
     * saved default, else their home. Never fails - with nothing permitted at all, the home is
     * returned as it stands, which is what every login worked in before workspaces existed.
     */
    @Transactional(readOnly = true)
    public Workspace resolve(FabricUser user, Set<Long> organizationIds, RowScope scope, WorkspaceSelection chosen) {
        // Each candidate is only read when the one before it has failed: a live session choice
        // costs no lookup of the saved default.
        List<java.util.function.Supplier<WorkspaceSelection>> candidates = List.of(
            () -> chosen,
            () -> savedDefault(user.getId()),
            () -> Workspace.home(user).selection());
        for (var candidate : candidates) {
            WorkspaceSelection selection = candidate.get();
            Optional<Workspace> workspace = selection == null ? Optional.empty()
                : check(user, organizationIds, scope, selection, false);
            if (workspace.isPresent()) {
                return workspace.get();
            }
        }
        return Workspace.home(user);
    }

    /**
     * The switcher's check: exactly what was asked for, or a refusal saying which part is not
     * permitted. Nothing is silently substituted - the user is looking at what they picked.
     */
    @Transactional(readOnly = true)
    public Workspace require(FabricUser user, FabricUserPrincipal principal, WorkspaceSelection chosen) {
        return check(user, principal.getOrganizationIds(), principal.getRowScope(), chosen, true)
            .orElseThrow(() -> new IllegalArgumentException("You have no access to that organization."));
    }

    /** Every organization the user may enter and what they may pick in each - the switcher's lists. */
    @Transactional(readOnly = true)
    public List<OrganizationChoices> options(FabricUser user, FabricUserPrincipal principal) {
        List<OrganizationChoices> result = new ArrayList<>();
        for (Organization org : activeOrganizations(principal.getOrganizationIds())) {
            RowScope scope = principal.getRowScope();
            result.add(new OrganizationChoices(org.getId(), org.getCode(), org.getName(),
                permittedUnits(user, scope, org.getId()).stream()
                    .map(u -> new Choice(u.getId(), u.getCode(), u.getName(), null)).toList(),
                permittedStores(user, scope, org.getId(), null).stream()
                    .map(w -> new Choice(w.getId(), w.getCode(), w.getName(), w.getBusinessUnitId())).toList(),
                permittedCentres(scope, org.getId()).stream()
                    .map(c -> new Choice(c.getId(), c.getCode(), c.getName(), null)).toList()));
        }
        return result;
    }

    /** The user's saved default, as saved - null when they have never chosen one. */
    @Transactional(readOnly = true)
    public WorkspaceSelection savedDefault(Long userId) {
        return defaults.findByUserId(userId).map(UserWorkspace::selection).orElse(null);
    }

    /** Saves a workspace {@link #require} has already approved as the user's default. */
    @Transactional
    public void saveDefault(Long userId, Workspace workspace) {
        UserWorkspace saved = defaults.findByUserId(userId).orElseGet(() -> new UserWorkspace(userId));
        saved.choose(workspace);
        defaults.save(saved);
    }

    // -----------------------------------------------------------------------------------------

    /**
     * @param strict refuse anything not permitted, naming it; otherwise repair what can be
     *               repaired and give up (empty) only when the organization itself is out of reach
     */
    private Optional<Workspace> check(FabricUser user, Set<Long> organizationIds, RowScope scope,
                                      WorkspaceSelection candidate, boolean strict) {
        Long orgId = candidate == null ? null : candidate.organizationId();
        if (orgId == null || !organizationIds.contains(orgId)) {
            return refuse(strict, "You have no access to that organization.");
        }
        Organization org = organizations.findById(orgId).filter(o -> Boolean.TRUE.equals(o.getActive())).orElse(null);
        if (org == null) {
            return refuse(strict, "That organization is not active.");
        }

        List<BusinessUnit> units = permittedUnits(user, scope, orgId);
        BusinessUnit unit = pick(units, BusinessUnit::getId, candidate.businessUnitId());
        if (unit == null) {
            if (strict) {
                throw new IllegalArgumentException(candidate.businessUnitId() == null
                    ? "Choose a business unit."
                    : "You have no access to that business unit in " + org.getName() + ".");
            }
            if (units.isEmpty()) {
                return Optional.empty();
            }
            unit = units.get(0);
        }

        Warehouse store = null;
        if (candidate.warehouseId() != null) {
            store = pick(permittedStores(user, scope, orgId, unit.getId()), Warehouse::getId, candidate.warehouseId());
            if (store == null && strict) {
                throw new IllegalArgumentException("You have no access to that store under " + unit.getName() + ".");
            }
        }

        CostCentre centre = null;
        if (candidate.costCentreId() != null) {
            centre = pick(permittedCentres(scope, orgId), CostCentre::getId, candidate.costCentreId());
            if (centre == null && strict) {
                throw new IllegalArgumentException("You have no access to that cost centre in " + org.getName() + ".");
            }
        }

        return Optional.of(new Workspace(org.getId(), org.getCode(), org.getName(),
            unit.getId(), unit.getCode(),
            store == null ? null : store.getId(),
            centre == null ? null : centre.getId(),
            centre == null ? null : centre.getCode(),
            centre == null ? null : centre.getName()));
    }

    private static Optional<Workspace> refuse(boolean strict, String message) {
        if (strict) {
            throw new IllegalArgumentException(message);
        }
        return Optional.empty();
    }

    private static <T> T pick(List<T> permitted, java.util.function.Function<T, Long> id, Long wanted) {
        if (wanted == null) {
            return null;
        }
        return permitted.stream().filter(item -> wanted.equals(id.apply(item))).findFirst().orElse(null);
    }

    private List<Organization> activeOrganizations(Set<Long> ids) {
        List<Organization> found = organizations.findAllById(ids).stream()
            .filter(o -> Boolean.TRUE.equals(o.getActive()))
            .toList();
        // In the principal's order - the home organization first.
        List<Organization> ordered = new ArrayList<>();
        for (Long id : ids) {
            found.stream().filter(o -> o.getId().equals(id)).findFirst().ifPresent(ordered::add);
        }
        return ordered;
    }

    private List<BusinessUnit> permittedUnits(FabricUser user, RowScope scope, Long orgId) {
        return businessUnits.lookup(orgId).stream()
            .filter(u -> u.getId().equals(user.getBusinessUnitId())
                || scope.permits(ScopeDimension.BUSINESS_UNIT, u.getId()))
            .toList();
    }

    /** @param unitId narrows to that unit's stores and the unit-less ones; null for all of them */
    private List<Warehouse> permittedStores(FabricUser user, RowScope scope, Long orgId, Long unitId) {
        return warehouses.lookup(orgId).stream()
            .filter(w -> w.getId().equals(user.getWarehouseId())
                || scope.permits(ScopeDimension.WAREHOUSE, w.getId()))
            .filter(w -> unitId == null || w.getBusinessUnitId() == null || Objects.equals(w.getBusinessUnitId(), unitId))
            .toList();
    }

    private List<CostCentre> permittedCentres(RowScope scope, Long orgId) {
        return costCentres.all(orgId).stream()
            .filter(c -> Boolean.TRUE.equals(c.getActive()))
            .filter(c -> scope.permits(ScopeDimension.COST_CENTRE, c.getId()))
            .toList();
    }
}

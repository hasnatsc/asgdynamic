package com.asg.fabricerp.security;

import com.asg.fabricerp.accounts.AccountFlags.CostCentreType;
import com.asg.fabricerp.accounts.CostCentre;
import com.asg.fabricerp.accounts.CostCentreRepository;
import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.Organization;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a user works is their choice only among what they are granted, and a choice never
 * outlives its grant: it is re-checked on every request and falls back, never through.
 */
class WorkspaceResolverTest {

    private static final Long HOME = 1L, OTHER = 2L;
    private static final Long UNIT_AF = 10L, UNIT_AX = 11L, UNIT_BX = 30L, UNIT_BY = 31L;
    private static final Long STORE_AF = 20L, STORE_AX = 21L, STORE_AF_2 = 22L;
    private static final Long CENTRE_WEAVING = 40L, CENTRE_ADMIN = 41L;
    private static final LocalDate TODAY = LocalDate.now();

    private UserWorkspaceRepository defaults;
    private WorkspaceResolver resolver;
    private FabricUser user;
    private final List<DataScope> grants = new ArrayList<>();

    @BeforeEach
    void setUp() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        Organization home = org(HOME, "ASG"), other = org(OTHER, "OTH");
        when(organizations.findById(HOME)).thenReturn(Optional.of(home));
        when(organizations.findById(OTHER)).thenReturn(Optional.of(other));
        when(organizations.findAllById(any())).thenReturn(List.of(home, other));

        BusinessUnitRepository units = mock(BusinessUnitRepository.class);
        when(units.lookup(HOME)).thenReturn(List.of(unit(UNIT_AF, "AF"), unit(UNIT_AX, "AX")));
        when(units.lookup(OTHER)).thenReturn(List.of(unit(UNIT_BX, "BX"), unit(UNIT_BY, "BY")));

        WarehouseRepository stores = mock(WarehouseRepository.class);
        when(stores.lookup(HOME)).thenReturn(List.of(
            store(STORE_AF, UNIT_AF), store(STORE_AX, UNIT_AX), store(STORE_AF_2, UNIT_AF)));
        when(stores.lookup(OTHER)).thenReturn(List.of());

        CostCentreRepository centres = mock(CostCentreRepository.class);
        when(centres.all(HOME)).thenReturn(List.of(centre(CENTRE_WEAVING, "WV"), centre(CENTRE_ADMIN, "ADM")));
        when(centres.all(OTHER)).thenReturn(List.of());

        defaults = mock(UserWorkspaceRepository.class);
        when(defaults.findByUserId(any())).thenReturn(Optional.empty());

        resolver = new WorkspaceResolver(organizations, units, stores, centres, defaults);

        user = new FabricUser("merch", "hash", UNIT_AF, "AF");
        user.setId(5L);
        user.setOrganizationId(HOME);
        user.setWarehouseId(STORE_AF);
        user.setUnrestricted(true);
    }

    private Workspace resolve(WorkspaceSelection chosen) {
        return resolver.resolve(user, FabricUserPrincipal.organizationsOf(user, grants, TODAY),
            FabricUserPrincipal.resolveScope(user, grants, TODAY), chosen);
    }

    private Workspace require(WorkspaceSelection chosen) {
        return resolver.require(user, new FabricUserPrincipal(user, grants, TODAY), chosen);
    }

    private void grant(ScopeDimension dimension, Long value) {
        grants.add(new DataScope(user.getId(), dimension, value, TODAY.minusDays(1), null));
    }

    // ---------------------------------------------------------------------------- resolve

    @Test
    void withNothingChosenTheUserWorksInTheirHome() {
        Workspace workspace = resolve(null);

        assertThat(workspace.organizationId()).isEqualTo(HOME);
        assertThat(workspace.organizationCode()).isEqualTo("ASG");
        assertThat(workspace.businessUnitId()).isEqualTo(UNIT_AF);
        assertThat(workspace.warehouseId()).isEqualTo(STORE_AF);
    }

    @Test
    void aSessionChoiceInAGrantedOrganizationIsHonoured() {
        grant(ScopeDimension.ORGANIZATION, OTHER);

        Workspace workspace = resolve(new WorkspaceSelection(OTHER, UNIT_BX, null, null));

        assertThat(workspace.organizationId()).isEqualTo(OTHER);
        assertThat(workspace.businessUnitCode()).isEqualTo("BX");
    }

    @Test
    void aChoiceInAnOrganizationNoLongerGrantedFallsBackToTheSavedDefault() {
        UserWorkspace saved = new UserWorkspace(user.getId());
        saved.choose(new Workspace(HOME, null, null, UNIT_AX, "AX", STORE_AX, CENTRE_ADMIN, null, null));
        when(defaults.findByUserId(user.getId())).thenReturn(Optional.of(saved));

        Workspace workspace = resolve(new WorkspaceSelection(OTHER, UNIT_BX, null, null));

        assertThat(workspace.organizationId()).isEqualTo(HOME);
        assertThat(workspace.businessUnitId()).isEqualTo(UNIT_AX);
        assertThat(workspace.warehouseId()).isEqualTo(STORE_AX);
        assertThat(workspace.costCentreCode()).isEqualTo("ADM");
    }

    @Test
    void aUnitNoLongerHeldGivesWayToOneThatIs_andAStoreNoLongerHeldToNone() {
        user.setUnrestricted(false);
        grant(ScopeDimension.ORGANIZATION, OTHER);
        grant(ScopeDimension.BUSINESS_UNIT, UNIT_BY);
        grant(ScopeDimension.WAREHOUSE, STORE_AF);

        Workspace workspace = resolve(new WorkspaceSelection(OTHER, UNIT_BX, STORE_AX, null));

        assertThat(workspace.businessUnitId()).isEqualTo(UNIT_BY);
        assertThat(workspace.warehouseId()).isNull();
    }

    @Test
    void organizationGrantsAreExplicitEvenForUnrestrictedUsers() {
        Workspace workspace = resolve(new WorkspaceSelection(OTHER, UNIT_BX, null, null));

        assertThat(workspace.organizationId()).isEqualTo(HOME);
    }

    @Test
    void anOrganizationGrantAloneDoesNotConfigureAScopedUser() {
        user.setUnrestricted(false);
        grant(ScopeDimension.ORGANIZATION, OTHER);

        assertThat(new FabricUserPrincipal(user, grants, TODAY).getRowScope().isConfigured()).isFalse();
    }

    // ---------------------------------------------------------------------------- require

    @Test
    void theSwitcherRefusesAnOrganizationNotGranted() {
        assertThatThrownBy(() -> require(new WorkspaceSelection(OTHER, UNIT_BX, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no access to that organization");
    }

    @Test
    void theSwitcherRefusesAStoreNotGranted_ratherThanQuietlyDroppingIt() {
        user.setUnrestricted(false);
        grant(ScopeDimension.WAREHOUSE, STORE_AF);

        assertThatThrownBy(() -> require(new WorkspaceSelection(HOME, UNIT_AF, STORE_AF_2, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("store");
    }

    @Test
    void theSwitcherRefusesACostCentreNotGranted() {
        user.setUnrestricted(false);
        grant(ScopeDimension.COST_CENTRE, CENTRE_WEAVING);

        assertThatThrownBy(() -> require(new WorkspaceSelection(HOME, UNIT_AF, null, CENTRE_ADMIN)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cost centre");
    }

    @Test
    void theHomeUnitIsAlwaysPermitted_evenToAUserScopedToAnother() {
        user.setUnrestricted(false);
        grant(ScopeDimension.BUSINESS_UNIT, UNIT_AX);

        Workspace workspace = require(new WorkspaceSelection(HOME, UNIT_AF, null, CENTRE_WEAVING));

        assertThat(workspace.businessUnitId()).isEqualTo(UNIT_AF);
        assertThat(workspace.costCentreId()).isEqualTo(CENTRE_WEAVING);
    }

    @Test
    void theSwitcherOffersOnlyWhatIsGranted() {
        user.setUnrestricted(false);
        grant(ScopeDimension.ORGANIZATION, OTHER);
        grant(ScopeDimension.BUSINESS_UNIT, UNIT_BY);
        grant(ScopeDimension.COST_CENTRE, CENTRE_ADMIN);

        var options = resolver.options(user, new FabricUserPrincipal(user, grants, TODAY));

        assertThat(options).extracting(WorkspaceResolver.OrganizationChoices::code).containsExactly("ASG", "OTH");
        assertThat(options.get(0).businessUnits()).extracting(WorkspaceResolver.Choice::id).containsExactly(UNIT_AF);
        assertThat(options.get(0).costCentres()).extracting(WorkspaceResolver.Choice::id).containsExactly(CENTRE_ADMIN);
        assertThat(options.get(1).businessUnits()).extracting(WorkspaceResolver.Choice::id).containsExactly(UNIT_BY);
    }

    // ----------------------------------------------------------------------------

    private static Organization org(Long id, String code) {
        Organization org = new Organization(code, code + " Ltd");
        org.setId(id);
        return org;
    }

    private static BusinessUnit unit(Long id, String code) {
        BusinessUnit unit = new BusinessUnit(code, code + " unit");
        unit.setId(id);
        return unit;
    }

    private static Warehouse store(Long id, Long unitId) {
        Warehouse store = new Warehouse("S" + id, "Store " + id);
        store.setId(id);
        store.setBusinessUnitId(unitId);
        return store;
    }

    private static CostCentre centre(Long id, String code) {
        CostCentre centre = new CostCentre(code, code + " centre", CostCentreType.PRODUCTION);
        centre.setId(id);
        return centre;
    }
}

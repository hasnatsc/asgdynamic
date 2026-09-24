package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The rules asfl-erp's admin module enforces and this one now does too: nobody administers
 * their own access, administrator-set passwords are temporary, and ADM-4's one-team rule.
 */
class UserAdminServiceTest {

    private static final Long ORG = 1L;
    private static final Long ADMIN_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long TEAM_LONDON = 3L;
    private static final Long TEAM_TOKYO = 7L;
    private static final Long STORE_WEAVING = 20L;

    private FabricUserRepository users;
    private DataScopeRepository scopes;
    private MarketingTeamRepository teams;
    private AccessLogService accessLog;
    private UserAdminService service;
    private FabricUser admin;
    private FabricUser other;
    private final List<DataScope> savedScopes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        users = mock(FabricUserRepository.class);
        scopes = mock(DataScopeRepository.class);
        teams = mock(MarketingTeamRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        accessLog = mock(AccessLogService.class);

        admin = user(ADMIN_ID, "admin");
        other = user(OTHER_ID, "merchandiser");
        when(users.findScoped(ADMIN_ID, ORG)).thenReturn(Optional.of(admin));
        when(users.findScoped(OTHER_ID, ORG)).thenReturn(Optional.of(other));
        when(users.save(any(FabricUser.class))).thenAnswer(i -> i.getArgument(0));
        when(scopes.save(any(DataScope.class))).thenAnswer(i -> {
            savedScopes.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(scopes.findByUserIdOrderByGrantedFromDesc(any())).thenAnswer(i -> savedScopes.stream()
            .filter(s -> s.getUserId().equals(i.getArgument(0))).toList());
        when(teams.lookup(ORG)).thenReturn(List.of(team(TEAM_LONDON, "London"), team(TEAM_TOKYO, "Tokyo")));

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return null; }
            @Override public String username()         { return "admin"; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };

        BusinessUnitRepository units = mock(BusinessUnitRepository.class);
        when(units.lookup(ORG)).thenReturn(List.of(unit(10L, "AF"), unit(11L, "AX")));
        WarehouseRepository stores = mock(WarehouseRepository.class);
        when(stores.lookup(ORG)).thenReturn(List.of(store(STORE_WEAVING)));

        service = new UserAdminService(users, roles, scopes, units, stores, teams,
            NoOpPasswordEncoder.getInstance(), accessLog, context);

        signInAs(admin);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static FabricUser user(Long id, String username) {
        FabricUser user = new FabricUser(username, "unused-hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(ORG);
        return user;
    }

    private static BusinessUnit unit(Long id, String code) {
        BusinessUnit unit = new BusinessUnit(code, code + " unit");
        unit.setId(id);
        return unit;
    }

    private static Warehouse store(Long id) {
        Warehouse store = new Warehouse("WS", "Weaving store");
        store.setId(id);
        return store;
    }

    private static MarketingTeam team(Long id, String name) {
        MarketingTeam team = new MarketingTeam(String.valueOf(id), name);
        team.setId(id);
        return team;
    }

    private static void signInAs(FabricUser user) {
        user.setUnrestricted(true);
        var principal = new FabricUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    // --- nobody administers their own access ---------------------------------------------------

    @Test
    void anAdministratorCannotMakeThemselvesUnrestricted() {
        admin.setUnrestricted(false);

        assertThatThrownBy(() -> service.update(ADMIN_ID, "Admin", 10L, null, Set.of(), true))
            .isInstanceOf(SelfGrantException.class)
            .hasMessageContaining("unrestricted");
    }

    @Test
    void anAdministratorCannotMoveThemselvesToAnotherBusinessUnit() {
        assertThatThrownBy(() -> service.update(ADMIN_ID, "Admin", 11L, null, Set.of(), true))
            .isInstanceOf(SelfGrantException.class);
    }

    @Test
    void anAdministratorMayStillCorrectTheirOwnName() {
        FabricUser saved = service.update(ADMIN_ID, "Corrected Name", 10L, null, Set.of(), true);

        assertThat(saved.getFullName()).isEqualTo("Corrected Name");
    }

    @Test
    void anAdministratorCannotGrantThemselvesScope() {
        assertThatThrownBy(() -> service.grantScope(ADMIN_ID, ScopeDimension.MARKETING_TEAM, TEAM_LONDON, null, null))
            .isInstanceOf(SelfGrantException.class);
        verify(scopes, never()).save(any());
        verify(accessLog).record(eq(ADMIN_ID), eq("admin"), eq(AccessLogEntry.Event.ACCESS_DENIED), any(), any());
    }

    @Test
    void anAdministratorCannotResetOrUnlockTheirOwnAccount() {
        assertThatThrownBy(() -> service.resetPassword(ADMIN_ID, "a-long-enough-password"))
            .isInstanceOf(SelfGrantException.class);
        assertThatThrownBy(() -> service.setLocked(ADMIN_ID, false, null))
            .isInstanceOf(SelfGrantException.class);
        assertThatThrownBy(() -> service.delete(ADMIN_ID))
            .isInstanceOf(SelfGrantException.class);
    }

    @Test
    void theSameChangesToSomebodyElseAreAllowed() {
        FabricUser saved = service.update(OTHER_ID, "Merch", 10L, null, Set.of(), true);

        assertThat(saved.isUnrestricted()).isTrue();
    }

    // --- business unit and store ---------------------------------------------------------------

    @Test
    void movingAUserToAnotherUnitTakesThatUnitsCode() {
        FabricUser saved = service.update(OTHER_ID, "Merch", 11L, null, Set.of(), false);

        assertThat(saved.getBusinessUnitId()).isEqualTo(11L);
        assertThat(saved.getBusinessUnitCode()).isEqualTo("AX");
    }

    @Test
    void aBusinessUnitOutsideTheOrganizationIsRefused() {
        assertThatThrownBy(() -> service.update(OTHER_ID, "Merch", 99L, null, Set.of(), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("business unit");
        assertThat(other.getBusinessUnitId()).isEqualTo(10L);
    }

    @Test
    void aStoreOutsideTheOrganizationIsRefused() {
        assertThatThrownBy(() -> service.update(OTHER_ID, "Merch", 10L, 99L, Set.of(), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("warehouse");

        FabricUser saved = service.update(OTHER_ID, "Merch", 10L, STORE_WEAVING, Set.of(), false);
        assertThat(saved.getWarehouseId()).isEqualTo(STORE_WEAVING);
    }

    @Test
    void creatingAUserDerivesTheUnitCodeAndTrimsTheUsername() {
        FabricUser created = service.create("  new.merch ", "a-long-enough-password", "New Merch",
            11L, null, Set.of(), false);

        assertThat(created.getUsername()).isEqualTo("new.merch");
        assertThat(created.getBusinessUnitCode()).isEqualTo("AX");
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.getOrganizationId()).isEqualTo(ORG);
    }

    @Test
    void creatingAUserNeedsARealBusinessUnit() {
        assertThatThrownBy(() -> service.create("new.merch", "a-long-enough-password", null,
                null, null, Set.of(), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("business unit");
        verify(users, never()).save(any());
    }

    // --- passwords ------------------------------------------------------------------------------

    @Test
    void anAdministratorResetIsTemporary() {
        service.resetPassword(OTHER_ID, "correct horse battery");

        assertThat(other.isMustChangePassword()).isTrue();
        assertThat(other.getPasswordHash()).isEqualTo("correct horse battery");
    }

    @Test
    void aShortPasswordIsRefused() {
        assertThatThrownBy(() -> service.resetPassword(OTHER_ID, "Password1!"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("12");
    }

    @Test
    void unlockingClearsTheFailureRunToo() {
        other.lock("too many failures", java.time.LocalDateTime.now());

        service.setLocked(OTHER_ID, false, null);

        assertThat(other.getAccountLocked()).isFalse();
        assertThat(other.getLockedReason()).isNull();
        verify(users).resetFailedLogins(OTHER_ID);
    }

    // --- ADM-4 ----------------------------------------------------------------------------------

    @Test
    void aUserBelongsToExactlyOneMarketingTeam() {
        service.grantScope(OTHER_ID, ScopeDimension.MARKETING_TEAM, TEAM_LONDON, null, null);

        assertThatThrownBy(() -> service.grantScope(OTHER_ID, ScopeDimension.MARKETING_TEAM, TEAM_TOKYO, null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exactly one");
    }

    @Test
    void aRevokedTeamMakesRoomForTheNextOne() {
        DataScope london = service.grantScope(OTHER_ID, ScopeDimension.MARKETING_TEAM, TEAM_LONDON,
            LocalDate.now().minusDays(30), null);
        london.revokeFrom(LocalDate.now(), "Moved to Tokyo");

        DataScope tokyo = service.grantScope(OTHER_ID, ScopeDimension.MARKETING_TEAM, TEAM_TOKYO, null, null);

        assertThat(tokyo.getScopeValueId()).isEqualTo(TEAM_TOKYO);
    }

    @Test
    void aScopeValueMustExist() {
        assertThatThrownBy(() -> service.grantScope(OTHER_ID, ScopeDimension.MARKETING_TEAM, 999L, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

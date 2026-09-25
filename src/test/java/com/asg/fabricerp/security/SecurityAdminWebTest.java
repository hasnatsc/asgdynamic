package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.AccessLogEntry.Event;
import com.asg.fabricerp.security.SecurityOverviewService.Overview;
import com.asg.fabricerp.security.SecurityOverviewService.RoleCounts;
import com.asg.fabricerp.web.DashboardService;
import com.asg.fabricerp.web.DashboardService.DashboardStats;
import com.asg.fabricerp.web.HomeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The security administration screens end to end through the web layer: the real
 * {@link SecurityConfig} (deny-by-default, method security, the per-request principal refresh),
 * every page actually rendered through Thymeleaf and the layout, and the JSON error contract the
 * screens' scripts rely on. Services and repositories are mocked — this is about the wiring and
 * the templates, which the service tests cannot reach.
 */
@WebMvcTest(controllers = {HomeController.class, UserAdminController.class, RoleController.class,
                           SecurityOverviewController.class, AccessLogController.class})
@Import(SecurityConfig.class)
class SecurityAdminWebTest {

    private static final Long ORG = 1L;

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private UserAdminService userAdminService;
    @MockitoBean private RoleService roleService;
    @MockitoBean private SecurityOverviewService overviewService;
    @MockitoBean private RoleRepository roleRepository;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private DashboardService dashboardService;

    private FabricUser admin;
    private FabricUser clerk;

    @BeforeEach
    void setUp() {
        admin = user(1L, "admin", "Asha Admin", role("Security Admin", Screen.SECURITY_ADMIN, Verb.values()));
        clerk = user(2L, "clerk", null, role("Booking Clerk", Screen.BOOKING, Verb.VIEW, Verb.CREATE));
        // SessionPrincipalRefreshFilter reloads the principal on every request.
        when(userDetailsService.reload("admin")).thenAnswer(i -> Optional.of(new FabricUserPrincipal(admin)));
        when(userDetailsService.reload("clerk")).thenAnswer(i -> Optional.of(new FabricUserPrincipal(clerk)));
        when(orgContext.requireOrganizationId()).thenReturn(ORG);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(dashboardService.stats()).thenReturn(new DashboardStats(0, 0, 0, 0, 0, 0, 2, 0));

        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
        when(businessUnits.lookup(ORG)).thenReturn(List.of(unit));
    }

    // ---------------------------------------------------------------------------- pages render

    @Test
    void visitorsGetTheCompanyPageWithLogin_andNothingElseOpensUp() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Amanat Shah Fabrics")))
            .andExpect(content().string(containsString("href=\"/login\"")))
            .andExpect(content().string(not(containsString("id=\"sidebar\""))));   // not the app shell

        // Opening "/" opens exactly "/": every screen still sends a visitor to sign in.
        mvc.perform(get("/setup/users")).andExpect(status().is3xxRedirection());
    }

    @Test
    void homeShowsOnlyTheScreensTheUserMayView() throws Exception {
        mvc.perform(get("/").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Asha Admin")))
            .andExpect(content().string(containsString("Weaving Unit")))       // operating context
            .andExpect(content().string(containsString("href=\"/setup/users\"")))
            .andExpect(content().string(not(containsString("href=\"/booking\""))));

        mvc.perform(get("/").with(signedIn(clerk)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/booking\"")))
            .andExpect(content().string(not(containsString("href=\"/setup/users\""))))
            .andExpect(content().string(not(containsString("href=\"/setup/security\"")))); // KPI user-count tile
    }

    @Test
    void theUsersScreenRenders() throws Exception {
        when(roleRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(role("Security Admin", Screen.SECURITY_ADMIN, Verb.VIEW)));

        mvc.perform(get("/setup/users").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"userTable\"")))
            .andExpect(content().string(containsString("New user")))
            .andExpect(content().string(containsString("AF - Weaving Unit")))
            .andExpect(content().string(containsString("nav-sublink is-active")));
    }

    @Test
    void theRolesScreenRendersTheMatrixWithScreenNames() throws Exception {
        mvc.perform(get("/setup/roles").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Bulk production order")))
            .andExpect(content().string(containsString("data-screen=\"SECURITY_ADMIN\"")));
    }

    @Test
    void theOverviewRendersItsNumbersAndLists() throws Exception {
        FabricUser locked = user(3L, "locked.user", "Locked User", role("R", Screen.BOOKING, Verb.VIEW));
        locked.lock("Locked after 5 consecutive failed logins", LocalDateTime.now());
        AccessLogEntry denied = new AccessLogEntry(2L, "clerk", Event.ACCESS_DENIED, "UserAdminController.page",
            "denied", "127.0.0.1", LocalDateTime.now());
        when(overviewService.overview()).thenReturn(new Overview(
            new UserCounts(12L, 1L, 2L, 3L), new RoleCounts(73, 72, 70),
            Map.of(Event.LOGIN_FAILED, 4L, Event.ACCESS_DENIED, 9L),
            List.of(locked), 1, List.of(clerk), List.of(denied)));

        mvc.perform(get("/setup/security").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Locked after 5 consecutive failed logins")))
            .andExpect(content().string(containsString("Access denied")))
            .andExpect(content().string(containsString("/setup/users?edit=2&amp;tab=scope")));
    }

    @Test
    void theAccessLogScreenRenders() throws Exception {
        mvc.perform(get("/setup/access-log").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Login failed")))
            .andExpect(content().string(containsString("id=\"logTable\"")));
    }

    // ---------------------------------------------------------------------------- refusals

    @Test
    void aUserWithoutTheSecurityScreenIsRefused() throws Exception {
        mvc.perform(get("/setup/users").with(signedIn(clerk)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anApiRefusalExplainsItselfAsJson() throws Exception {
        mvc.perform(get("/api/setup/users").with(signedIn(clerk)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void anonymousApiCallsGet401NotALoginPage() throws Exception {
        mvc.perform(get("/api/setup/users"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/setup/users"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    // ---------------------------------------------------------------------------- JSON contract

    @Test
    void aServiceRefusalReachesTheScreenAsASentence() throws Exception {
        when(userAdminService.create(any(), any(), any(), any(), any(), any(), anyBoolean()))
            .thenThrow(new IllegalArgumentException("Username 'asha' already exists"));

        mvc.perform(post("/api/setup/users").with(signedIn(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"asha","password":"a-long-enough-password","businessUnitId":10,"unrestricted":false}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Username 'asha' already exists"));
    }

    @Test
    void aSelfGrantIsForbiddenWithItsReason() throws Exception {
        when(userAdminService.update(eq(1L), any(), any(), any(), any(), anyBoolean()))
            .thenThrow(new SelfGrantException("roles"));

        mvc.perform(post("/api/setup/users/1").with(signedIn(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessUnitId\":10,\"roleIds\":[5],\"unrestricted\":true}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", containsString("own account")));
    }

    @Test
    void aMissingRequiredFieldIsNamed() throws Exception {
        mvc.perform(post("/api/setup/users").with(signedIn(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"asha\",\"password\":\"a-long-enough-password\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.businessUnitId").exists());
    }

    @Test
    void theGridFlagsScopedUsersWhoCannotSignIn() throws Exception {
        clerk.setUnrestricted(false);
        when(userAdminService.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(admin, clerk)));
        when(userAdminService.idsHoldingScopeToday(any())).thenReturn(Set.of());

        mvc.perform(get("/api/setup/users").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].cannotSignIn").value(false))   // admin: unrestricted
            .andExpect(jsonPath("$.data[1].cannotSignIn").value(true));   // clerk: scoped, nothing held
    }

    // ---------------------------------------------------------------------------- fixtures

    private static RequestPostProcessor signedIn(FabricUser user) {
        var principal = new FabricUserPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private static FabricUser user(Long id, String username, String fullName, Role role) {
        FabricUser user = new FabricUser(username, "hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(ORG);
        user.setFullName(fullName);
        user.setUnrestricted(true);
        user.addRole(role);
        return user;
    }

    private static long nextRoleId = 100;

    private static Role role(String name, Screen screen, Verb... verbs) {
        Role role = new Role(name, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

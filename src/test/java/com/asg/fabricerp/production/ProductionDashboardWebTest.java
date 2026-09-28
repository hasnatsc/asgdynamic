package com.asg.fabricerp.production;

import com.asg.fabricerp.accounts.CostCentreRepository;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The production dashboard through the web layer: who may open it, and what its API accepts. */
@WebMvcTest(controllers = ProductionDashboardController.class)
@Import(SecurityConfig.class)
class ProductionDashboardWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductionDashboardService dashboard;
    @MockitoBean private ProductionBoardService boards;
    @MockitoBean private NamedParameterJdbcTemplate jdbc;
    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private RoleRepository roleRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrganizationRepository organizations;
    @MockitoBean private CostCentreRepository costCentres;

    private FabricUser manager;
    private FabricUser clerk;

    @BeforeEach
    void setUp() {
        manager = user(1L, "manager", role("Management", Screen.PROD_DASHBOARD, Verb.VIEW));
        clerk = user(2L, "clerk", role("Booking clerk", Screen.BOOKING, Verb.VIEW));
        when(userDetailsService.reload(eq("manager"), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(manager)));
        when(userDetailsService.reload(eq("clerk"), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(clerk)));
        when(orgContext.requireOrganizationId()).thenReturn(1L);
    }

    @Test
    void thePageRendersWithItsFiltersTabsAndScripts_forWhoeverHoldsTheScreen() throws Exception {
        mvc.perform(get("/production/dashboard").with(signedIn(manager)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Production dashboard")))
            .andExpect(content().string(containsString("data-pd-filters")))
            .andExpect(content().string(containsString("data-tab=\"deliveries\"")))
            .andExpect(content().string(containsString("data-tab=\"alerts\"")))
            .andExpect(content().string(containsString("/js/analytics-charts.js")))
            .andExpect(content().string(containsString("/js/production-dashboard.js")))
            .andExpect(content().string(containsString("href=\"/production/dashboard\"")));   // in the menu

        mvc.perform(get("/production/dashboard").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/production/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void theApiPassesTheFiltersOn_andRefusesOthers() throws Exception {
        when(dashboard.dashboard(any(), any())).thenReturn(Map.of("orders", List.of()));

        mvc.perform(get("/api/production/dashboard").with(signedIn(manager))
                .param("from", "2026-01-01").param("to", "2026-09-30").param("fabricType", "Solid Dyed")
                .param("color", "Navy").param("buyerId", "7").param("bpoId", "9").param("status", "OPEN").param("month", "2026-10"))
            .andExpect(status().isOk());
        verify(dashboard).dashboard(argThat(f -> "Solid Dyed".equals(f.fabricType()) && "Navy".equals(f.color())
            && f.buyerId() == 7L && f.bpoId() == 9L && "OPEN".equals(f.status())), eq(java.time.YearMonth.of(2026, 10)));

        mvc.perform(get("/api/production/dashboard").with(signedIn(clerk))).andExpect(status().isForbidden());
    }

    @Test
    void aBackwardsDateRangeOrABadMonthOrStepIsA400() throws Exception {
        mvc.perform(get("/api/production/dashboard").with(signedIn(manager)).param("from", "2026-09-30").param("to", "2026-01-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("The date range ends before it starts"));
        mvc.perform(get("/api/production/dashboard").with(signedIn(manager)).param("month", "Sept"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/production/dashboard/documents").with(signedIn(manager)).param("step", "NOPE"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theDrillDownAsksForTheStepsDocuments_andBookingsHaveTheirOwn() throws Exception {
        when(dashboard.documents(any(), eq(ChainStep.GR), eq("open"))).thenReturn(List.of(Map.of("id", 5, "documentNo", "GR-1", "slug", "greige-receive")));
        mvc.perform(get("/api/production/dashboard/documents").with(signedIn(manager)).param("step", "GR").param("group", "open"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documentNo").value("GR-1"));

        mvc.perform(get("/api/production/dashboard/documents").with(signedIn(manager)).param("step", "BOOKING"))
            .andExpect(status().isOk());
        verify(dashboard).bookingDocuments(any());
    }

    private static RequestPostProcessor signedIn(FabricUser user) {
        var principal = new FabricUserPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private static FabricUser user(Long id, String username, Role role) {
        FabricUser user = new FabricUser(username, "hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(1L);
        user.setUnrestricted(true);
        user.addRole(role);
        return user;
    }

    private static long nextRoleId = 500;

    private static Role role(String name, Screen screen, Verb... verbs) {
        Role role = new Role(name, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

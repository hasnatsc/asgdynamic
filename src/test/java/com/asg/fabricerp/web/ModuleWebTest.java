package com.asg.fabricerp.web;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The home page's apps and attention cards, and an app's own page: who sees which app. */
@WebMvcTest(controllers = {HomeController.class, ModuleController.class})
@Import(SecurityConfig.class)
class ModuleWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ModuleDashboardService modules;
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

    private FabricUser planner;

    @BeforeEach
    void setUp() {
        Role role = new Role("Planner", null);
        role.setId(900L);
        role.grant(Screen.BPO, Verb.VIEW, Verb.CREATE);
        role.grant(Screen.PROD_BOARD, Verb.VIEW);
        planner = new FabricUser("planner", "hash", 10L, "AF");
        planner.setId(3L);
        planner.setOrganizationId(1L);
        planner.setUnrestricted(true);
        planner.addRole(role);
        when(userDetailsService.reload(eq("planner"), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(planner)));
        when(orgContext.requireOrganizationId()).thenReturn(1L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(planner));
        when(modules.home(any())).thenReturn(Map.of(
            "attention", Map.of("awaitingYou", 2L, "returned", 1L, "rejected", 0L, "drafts", 4L),
            "apps", List.of(Map.of("key", "production", "label", "Production", "icon", "production",
                "description", "Production orders, weaving and dyeing work orders", "screens", 2, "awaitingYou", 2L, "yours", 1L))));
    }

    @Test
    void homeShowsWhatNeedsTheUser_andTheirAppsLeadingToEachAppsPage() throws Exception {
        mvc.perform(get("/").with(signedIn()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Needs your attention")))
            .andExpect(content().string(containsString("Pending your approval")))
            .andExpect(content().string(containsString("data-my-work=\"returned\"")))
            .andExpect(content().string(containsString("href=\"/module/production\"")))
            .andExpect(content().string(containsString("1 of yours to act on")))
            .andExpect(content().string(containsString("/js/apps.js")))
            // No approvals screen: the approval card opens the drawer rather than a page they cannot open.
            .andExpect(content().string(not(containsString("href=\"/approvals\" class=\"attn-card\""))));
    }

    @Test
    void anAppOpensWithItsOwnMenuAndDashboard() throws Exception {
        when(modules.module(eq(Screen.Section.PRODUCTION), any())).thenReturn(Map.of(
            "key", "production", "label", "Production", "icon", "production", "description", "Production orders",
            "documents", List.of(Map.ofEntries(Map.entry("key", "BPO"), Map.entry("label", "Production order"), Map.entry("path", "/bpo"),
                Map.entry("icon", "planning"), Map.entry("draft", 1L), Map.entry("submitted", 2L), Map.entry("open", 3L),
                Map.entry("done", 4L), Map.entry("rejected", 0L), Map.entry("thisMonth", 5L), Map.entry("total", 10L),
                Map.entry("newPath", "/bpo?new=1"))),
            "tools", List.of(), "related", List.of(), "recent", List.of(), "activity", List.of(),
            "attention", Map.of("awaitingYou", 0L, "returned", 0L, "rejected", 0L, "drafts", 1L)));

        mvc.perform(get("/module/production").with(signedIn()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-module=\"production\"")))
            .andExpect(content().string(containsString("href=\"/bpo?new=1\"")))
            .andExpect(content().string(containsString("href=\"/production/board\"")))   // its own menu
            .andExpect(content().string(containsString("All apps")));
    }

    @Test
    void anAppWithNothingInTheUsersRolesIsRefused_andAnUnknownOneToo() throws Exception {
        mvc.perform(get("/module/commercial").with(signedIn())).andExpect(status().isForbidden());
        mvc.perform(get("/module/nothing").with(signedIn())).andExpect(status().isForbidden());
        mvc.perform(get("/module/production")).andExpect(status().is3xxRedirection());
        verify(modules, never()).module(eq(Screen.Section.COMMERCIAL), any());
    }

    @Test
    void theWorkListsTakeAKnownKindOnly() throws Exception {
        when(modules.myWork(any(), eq("production"), eq("draft"))).thenReturn(List.of(Map.of("documentNo", "BPO-1")));
        mvc.perform(get("/api/home/my-work").with(signedIn()).param("module", "production").param("kind", "draft"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documentNo").value("BPO-1"));
        mvc.perform(get("/api/home/my-work").with(signedIn()).param("kind", "salaries"))
            .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor signedIn() {
        var principal = new FabricUserPrincipal(planner);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}

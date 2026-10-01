package com.asg.fabricerp.search;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The search page and API through the real security config and layout. */
@WebMvcTest(controllers = SearchController.class)
@Import(SecurityConfig.class)
class SearchWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private SearchService service;
    @MockitoBean private SearchIndexer indexer;

    private FabricUser clerk;
    private FabricUser admin;

    @BeforeEach
    void setUp() {
        clerk = user(1L, "clerk", role(Screen.BPO, Verb.VIEW));
        admin = user(2L, "admin", role(Screen.SECURITY_ADMIN, Verb.VIEW));
        for (FabricUser u : List.of(clerk, admin)) {
            when(userDetailsService.reload(eq(u.getUsername()), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(1L);
        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
    }

    @Test
    void anySignedInUser_getsThePage_withTheirQuery() throws Exception {
        mvc.perform(get("/search").param("q", "BPO-2026").with(signedIn(clerk)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"searchPage\"")))
            .andExpect(content().string(containsString("value=\"BPO-2026\"")))
            // Chips for what a production-order clerk can see, and nothing else.
            .andExpect(content().string(containsString("data-kind=\"DOCUMENT\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-kind=\"VOUCHER\""))))
            // The rebuild button is for administrators only.
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Rebuild index"))));
    }

    @Test
    void theApi_returnsHitsWithTheirLinks() throws Exception {
        when(service.search("acme", null, 0, 20)).thenReturn(new SearchResult("database", 1, List.of(
            SearchResult.Hit.of(SearchKind.DOCUMENT, 12L, "BULK_PRODUCTION_ORDER", "BPO-2026-000012", "Acme Ltd",
                null, "APPROVED", LocalDate.of(2026, 9, 30), new BigDecimal("10"), "USD"))));

        mvc.perform(get("/api/search").param("q", "acme").with(signedIn(clerk)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("database"))
            .andExpect(jsonPath("$.hits[0].code").value("BPO-2026-000012"))
            .andExpect(jsonPath("$.hits[0].url").value("/bpo?open=12"));
    }

    @Test
    void signedOut_isRefused() throws Exception {
        mvc.perform(get("/api/search").param("q", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdministrator_mayRebuildTheIndex() throws Exception {
        mvc.perform(post("/api/search/reindex").with(csrf()).with(signedIn(clerk))).andExpect(status().isForbidden());
        verify(indexer, never()).rebuild();

        when(indexer.status()).thenReturn(new SearchIndexer.Status(true, true, "fabricerp-search", null, null, 5));
        when(indexer.rebuild()).thenReturn(5L);
        mvc.perform(post("/api/search/reindex").with(csrf()).with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indexed").value(5));
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

    private static long nextRoleId = 900;

    private static Role role(Screen screen, Verb... verbs) {
        Role role = new Role(screen.name() + " " + nextRoleId, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

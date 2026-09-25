package com.asg.fabricerp.global.numbering;

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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The numbering setup screen through the real {@link SecurityConfig} and layout. */
@WebMvcTest(controllers = NumberingSetupController.class)
@Import(SecurityConfig.class)
class NumberingSetupWebTest {

    private static final Long ORG = 1L;

    @Autowired private MockMvc mvc;

    // Security and layout collaborators, as in SecurityAdminWebTest.
    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrgContext orgContext;

    @MockitoBean private NumberingSetupService service;

    private FabricUser admin;    // view + amend
    private FabricUser auditor;  // view only
    private FabricUser clerk;    // no grant

    @BeforeEach
    void setUp() {
        admin = user(1L, "admin", role(Screen.NUMBERING, Verb.VIEW, Verb.AMEND));
        auditor = user(2L, "auditor", role(Screen.NUMBERING, Verb.VIEW));
        clerk = user(3L, "clerk", role(Screen.BOOKING, Verb.VIEW));
        for (FabricUser u : List.of(admin, auditor, clerk)) {
            when(userDetailsService.reload(u.getUsername())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(ORG);
        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
    }

    @Test
    void thePageRenders_andTheSidebarListsIt() throws Exception {
        mvc.perform(get("/setup/numbering").with(signedIn(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"seriesTable\"")))
            .andExpect(content().string(containsString("href=\"/setup/numbering\"")))
            .andExpect(content().string(containsString("const CAN_AMEND = true")));
    }

    @Test
    void aViewerSeesNoEditControls_andCannotChangeAnything() throws Exception {
        mvc.perform(get("/setup/numbering").with(signedIn(auditor)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("const CAN_AMEND = false")));
        mvc.perform(put("/api/setup/numbering/series/BOOKING").with(signedIn(auditor)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"prefix\":\"SO\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/setup/numbering/fiscal-year").with(signedIn(auditor)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startMonth\":7}"))
            .andExpect(status().isForbidden());
        verify(service, never()).update(any(), any());
        verify(service, never()).updateFiscalYear(anyInt());
    }

    @Test
    void usersWithoutTheGrantAreRefused() throws Exception {
        mvc.perform(get("/setup/numbering").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/api/setup/numbering").with(signedIn(clerk))).andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorCanReconfigureASeries() throws Exception {
        when(service.update(eq("BOOKING"), any())).thenReturn(Map.of("seriesCode", "BOOKING", "nextNumber", "SO-2026-000001"));

        mvc.perform(put("/api/setup/numbering/series/BOOKING").with(signedIn(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prefix\":\"SO\",\"pattern\":\"{PREFIX}-{FY}-{SEQ}\",\"sequenceWidth\":6,"
                       + "\"resetPolicy\":\"FINANCIAL_YEAR\",\"counterScope\":\"ORGANIZATION\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextNumber").value("SO-2026-000001"));
        verify(service).update(eq("BOOKING"), eq(new NumberingSetupService.SchemeRequest(
            "SO", "{PREFIX}-{FY}-{SEQ}", 6, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION, null)));
    }

    @Test
    void anUnsafeConfigurationComesBackAsTheServicesSentence() throws Exception {
        when(service.update(eq("BOOKING"), any())).thenThrow(new IllegalArgumentException(
            "A per-branch counter needs {BRANCH} in the pattern, or two branches would issue the same number."));

        mvc.perform(put("/api/setup/numbering/series/BOOKING").with(signedIn(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"counterScope\":\"BRANCH\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("{BRANCH}")));
    }

    private static RequestPostProcessor signedIn(FabricUser user) {
        var principal = new FabricUserPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private static FabricUser user(Long id, String username, Role... roles) {
        FabricUser user = new FabricUser(username, "hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(ORG);
        user.setUnrestricted(true);
        for (Role role : roles) user.addRole(role);
        return user;
    }

    private static long nextRoleId = 300;

    private static Role role(Screen screen, Verb... verbs) {
        Role role = new Role(screen.name() + " role " + nextRoleId, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

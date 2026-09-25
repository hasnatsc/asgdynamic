package com.asg.fabricerp.party;

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
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The party screen through the real security config and layout. */
@WebMvcTest(controllers = {PartyAdminController.class, PartyController.class})
@Import(SecurityConfig.class)
class PartyWebTest {

    private static final Long ORG = 1L;

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private PartyAdminService adminService;
    @MockitoBean private PartyService partyService;

    private FabricUser maintainer;
    private FabricUser viewer;
    private FabricUser clerk;

    @BeforeEach
    void setUp() {
        maintainer = user(1L, "maintainer", role(Screen.PARTY, Verb.values()));
        viewer = user(2L, "viewer", role(Screen.PARTY, Verb.VIEW));
        clerk = user(3L, "clerk", role(Screen.BOOKING, Verb.VIEW));
        for (FabricUser u : List.of(maintainer, viewer, clerk)) {
            when(userDetailsService.reload(u.getUsername())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(ORG);
        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
    }

    @Test
    void theDirectoryRenders_withTheEditorForMaintainers() throws Exception {
        mvc.perform(get("/setup/parties").with(signedIn(maintainer)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"partyTable\"")))
            .andExpect(content().string(containsString("id=\"partyNewBtn\"")))
            .andExpect(content().string(containsString("id=\"partyDeleteBtn\"")))
            .andExpect(content().string(containsString("href=\"/setup/parties\"")));   // in the sidebar
    }

    @Test
    void aViewerSeesNoCreateOrDelete() throws Exception {
        mvc.perform(get("/setup/parties").with(signedIn(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"partyNewBtn\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"partyDeleteBtn\""))));

        mvc.perform(post("/api/setup/parties").with(signedIn(viewer)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
            .andExpect(status().isForbidden());
        verify(adminService, never()).save(any());
    }

    @Test
    void maintenanceNeedsTheGrant_butThePickerDoesNot() throws Exception {
        mvc.perform(get("/setup/parties").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/api/setup/parties").with(signedIn(clerk))).andExpect(status().isForbidden());

        when(partyService.directory(eq(PartyRoleType.CUSTOMER), isNull(), eq(true), eq(0), eq(20)))
            .thenReturn(new PartyService.DirectoryPage(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/parties/directory").param("role", "CUSTOMER").with(signedIn(clerk)))
            .andExpect(status().isOk());
    }

    @Test
    void theGridPassesFiltersThrough() throws Exception {
        when(adminService.search(eq(PartyRoleType.BANK), eq(true), isNull(), eq("brac"), any()))
            .thenReturn(new PageImpl<>(List.of(Map.of("id", 1, "code", "BRAC", "roles", List.of("BANK")))));

        mvc.perform(get("/api/setup/parties").with(signedIn(viewer))
                .param("role", "BANK").param("active", "true").param("search[value]", "brac"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("BRAC"));
    }

    @Test
    void theExportIsCsv() throws Exception {
        when(adminService.search(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(Map.of("code", "CUS001", "name", "Acme", "roles", List.of("CUSTOMER:MARKETING"),
                "active", true))));

        mvc.perform(get("/api/setup/parties/export.csv").with(signedIn(viewer)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("parties-")))
            .andExpect(content().string(containsString("\"CUS001\",\"Acme\"")));
    }

    private static RequestPostProcessor signedIn(FabricUser user) {
        var principal = new FabricUserPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private static FabricUser user(Long id, String username, Role role) {
        FabricUser user = new FabricUser(username, "hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(ORG);
        user.setUnrestricted(true);
        user.addRole(role);
        return user;
    }

    private static long nextRoleId = 300;

    private static Role role(Screen screen, Verb... verbs) {
        Role role = new Role(screen.name() + " " + nextRoleId, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

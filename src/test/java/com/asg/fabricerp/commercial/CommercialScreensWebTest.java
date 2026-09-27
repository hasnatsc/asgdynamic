package com.asg.fabricerp.commercial;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every commercial screen through the real {@link SecurityConfig} and layout, each guarded by its own verbs. */
@WebMvcTest(controllers = {CommercialDocumentController.class, CommercialScreensController.class})
@Import({SecurityConfig.class, CommercialAccess.class})
class CommercialScreensWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private CommercialDocumentService documents;
    @MockitoBean private CommercialPostingService posting;
    @MockitoBean private CommercialRecordsService records;
    @MockitoBean private CommercialViews views;
    @MockitoBean private CostHeadRepository costHeads;
    @MockitoBean private DocumentNameRepository documentNames;
    @MockitoBean private NamedParameterJdbcTemplate jdbc;
    @MockitoBean private CommercialRegisterQueries register;
    @MockitoBean private CommercialSetupService setup;

    private FabricUser viewer;   // may view every screen
    private FabricUser exporter; // export PI and LC only

    @BeforeEach
    void setUp() {
        Role role = new Role("Viewer", null);
        role.setId(100L);
        Arrays.stream(Screen.values()).forEach(screen -> role.grant(screen, Verb.VIEW));
        viewer = user(1L, "viewer", role);
        Role export = new Role("Export desk", null);
        export.setId(101L);
        for (Screen s : List.of(Screen.EPI, Screen.ELC)) {
            export.grant(s, Verb.VIEW);
            export.grant(s, Verb.CREATE);
            export.grant(s, Verb.AMEND);
        }
        exporter = user(2L, "export", export);
        for (FabricUser u : List.of(viewer, exporter)) {
            when(userDetailsService.reload(eq(u.getUsername()), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(1L);
        when(documents.selfPartyId()).thenReturn(9L);
    }

    @ParameterizedTest
    @CsvSource({
        "/export-pi, export-pi, Export PIs",
        "/export-lc, export-lc, Export LCs",
        "/export-ci, export-ci, Export CIs",
        "/import-pi, import-pi, Import PIs",
        "/import-lc, import-lc, Import LCs"
    })
    void documentScreensRenderInTheLayoutWithTheirStepWired(String path, String slug, String title) throws Exception {
        mvc.perform(get(path).with(signedIn(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("id=\"commercialTable\"")))
            .andExpect(content().string(containsString("\"slug\":\"" + slug + "\"")))
            .andExpect(content().string(containsString("/js/commercial-docs.js")))
            .andExpect(content().string(containsString(title)))
            .andExpect(content().string(not(containsString("data-com-new"))));
    }

    @ParameterizedTest
    @CsvSource({"/commercial/register, register", "/commercial/setup, setup"})
    void registerAndSetupRender(String path, String screen) throws Exception {
        mvc.perform(get(path).with(signedIn(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-commercial-screen=\"" + screen + "\"")))
            .andExpect(content().string(containsString("/js/commercial-screens.js")))
            // The menu has a Commercial section.
            .andExpect(content().string(containsString("href=\"/export-lc\"")));
    }

    @Test
    void eachScreenIsGuardedByItsOwnVerbs() throws Exception {
        mvc.perform(get("/export-lc").with(signedIn(exporter)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-com-new")));
        mvc.perform(get("/export-ci").with(signedIn(exporter))).andExpect(status().isForbidden());
        mvc.perform(get("/import-pi").with(signedIn(exporter))).andExpect(status().isForbidden());
        mvc.perform(post("/api/export-ci/5/realization").param("step", "DOC_SUBMISSION").param("date", "2026-01-01")
            .with(signedIn(exporter)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/import-pi/5/milestones").param("milestone", "CNF").with(signedIn(exporter)).with(csrf()))
            .andExpect(status().isForbidden());
        // Viewing is not raising.
        mvc.perform(post("/api/export-pi").with(signedIn(viewer)).with(csrf()).contentType("application/json").content("{\"lines\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/export-lc/5/records").with(signedIn(viewer)).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/commercial/setup/cost-heads").with(signedIn(viewer)).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(posting, records, setup);
    }

    private static FabricUser user(Long id, String name, Role role) {
        FabricUser user = new FabricUser(name, "hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(1L);
        user.setUnrestricted(true);
        user.addRole(role);
        return user;
    }

    private static RequestPostProcessor signedIn(FabricUser user) {
        var principal = new FabricUserPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}

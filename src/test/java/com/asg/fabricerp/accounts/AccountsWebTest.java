package com.asg.fabricerp.accounts;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.security.AccessLogService;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserDetailsService;
import com.asg.fabricerp.security.FabricUserPrincipal;
import com.asg.fabricerp.security.Role;
import com.asg.fabricerp.security.Screen;
import com.asg.fabricerp.security.SecurityConfig;
import com.asg.fabricerp.security.Verb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Accounts screens render in the layout, list in the sidebar, and answer only to their grants. */
@WebMvcTest(controllers = AccountsController.class)
@Import(SecurityConfig.class)
class AccountsWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private AccountsSetupService setup;
    @MockitoBean private GeneralLedgerService ledger;
    @MockitoBean private LedgerReportService reports;
    @MockitoBean private CreditService credit;
    @MockitoBean private GlEntryRepository entries;
    @MockitoBean private AccountingPeriodRepository periods;
    @MockitoBean private PartyRepository parties;

    private FabricUser accountant;
    private FabricUser clerk;

    @BeforeEach
    void setUp() {
        accountant = user(1L, "accountant", role(Verb.values(), Screen.ACC_CHART, Screen.ACC_JOURNAL, Screen.ACC_REPORTS,
            Screen.ACC_CREDIT, Screen.ACC_SETUP));
        clerk = user(2L, "clerk", role(new Verb[] {Verb.VIEW}, Screen.ACC_JOURNAL));
        when(userDetailsService.reload("accountant")).thenAnswer(i -> Optional.of(new FabricUserPrincipal(accountant)));
        when(userDetailsService.reload("clerk")).thenAnswer(i -> Optional.of(new FabricUserPrincipal(clerk)));
        when(orgContext.requireOrganizationId()).thenReturn(1L);
        when(setup.fiscalStartMonth()).thenReturn(7);
        when(setup.chart()).thenReturn(List.of());
    }

    @ParameterizedTest
    @CsvSource({
        "/accounts/chart,    accTable",
        "/accounts/journals, jvTable",
        "/accounts/reports,  tbTable",
        "/accounts/credit,   clTable",
        "/accounts/setup,    pTable"
    })
    void everyAccountsScreenRendersInTheLayout(String path, String table) throws Exception {
        mvc.perform(get(path).with(signedIn(accountant)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString(">Accounts<")))                 // the sidebar section
            .andExpect(content().string(containsString("href=\"/accounts/journals\"")))
            .andExpect(content().string(containsString("id=\"" + table + "\"")));
    }

    @Test
    void aViewOnlyUserSeesTheLedgerButCannotPostOrReverse() throws Exception {
        mvc.perform(get("/accounts/journals").with(signedIn(clerk)))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"jvNewBtn\""))));
        mvc.perform(get("/accounts/chart").with(signedIn(clerk))).andExpect(status().isForbidden());

        mvc.perform(post("/api/accounts/journals").with(signedIn(clerk)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"narration\":\"x\",\"lines\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts/entries/5/reversal").param("reason", "x").with(signedIn(clerk)).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void aRefusedPostingComesBackAsTheLedgersOwnSentence() throws Exception {
        when(ledger.postManualJournal(any(), any(), any()))
            .thenThrow(ControlAccountException.manualJournal("1110"));
        mvc.perform(post("/api/accounts/journals").with(signedIn(accountant)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postingDate\":\"2026-09-15\",\"narration\":\"x\",\"lines\":[]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("control account")));
    }

    // ---------------------------------------------------------------------------- fixtures

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

    private static long nextRoleId = 300;

    private static Role role(Verb[] verbs, Screen... screens) {
        Role role = new Role("R" + nextRoleId, null);
        role.setId(nextRoleId++);
        for (Screen screen : screens) role.grant(screen, verbs);
        return role;
    }
}

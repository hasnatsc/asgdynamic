package com.asg.fabricerp.production;

import com.asg.fabricerp.accounts.CostCentreRepository;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.report.ReportService;
import com.asg.fabricerp.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The Fabrics production report through the web layer: who may open it, what its API passes on,
 * and that its export really prints - the Jasper template is filled and exported here, not mocked.
 */
@WebMvcTest(controllers = ProductionReportController.class)
@Import({SecurityConfig.class, ReportService.class})
class ProductionReportWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductionReportService report;
    @MockitoBean private JdbcTemplate jdbcTemplate;
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
        manager = user(1L, "manager", role("Management", Screen.PROD_REPORT, Verb.VIEW));
        clerk = user(2L, "clerk", role("Booking clerk", Screen.BOOKING, Verb.VIEW));
        when(userDetailsService.reload(eq("manager"), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(manager)));
        when(userDetailsService.reload(eq("clerk"), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(clerk)));
        when(orgContext.requireOrganizationId()).thenReturn(1L);
        when(report.marketingPersons(any(), any())).thenReturn(LookupPage.of(List.of(new LookupPage.Option(4L, null, "Raihan", null)), false));
        when(report.garments(any(), any())).thenReturn(LookupPage.of(List.of(), false));
    }

    @Test
    void thePageRendersForWhoeverHoldsTheScreen_andNobodyElse() throws Exception {
        mvc.perform(get("/production/report").with(signedIn(manager)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Fabrics production report")))
            .andExpect(content().string(containsString("data-production-report")))
            .andExpect(content().string(containsString("/js/production-report.js")))
            .andExpect(content().string(containsString("href=\"/production/report\"")));   // in the menu

        mvc.perform(get("/production/report").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/api/production/report").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/production/report/export").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/production/report")).andExpect(status().is3xxRedirection());
    }

    @Test
    void theApiPassesTheFiltersSortAndPageOn() throws Exception {
        when(report.report(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new ProductionReportService.Page(List.of(), 0, Map.of("orders", 0)));
        mvc.perform(get("/api/production/report").with(signedIn(manager))
                .param("q", "BPO-7").param("personId", "4").param("garmentsId", "9")
                .param("from", "2026-01-01").param("to", "2026-09-30").param("sort", "dueDate").param("dir", "asc")
                .param("page", "2").param("size", "25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
        verify(report).report(argThat(f -> "BPO-7".equals(f.q()) && f.marketingPersonId() == 4L && f.garmentsId() == 9L
            && LocalDate.of(2026, 1, 1).equals(f.from()) && LocalDate.of(2026, 9, 30).equals(f.to()) && f.bpoId() == null),
            eq("dueDate"), eq("asc"), eq(2), eq(25));
    }

    @Test
    void theExportPrintsThePdfAndTheWorkbook_throughTheJasperTemplate() throws Exception {
        when(report.all(any(), any(), any())).thenReturn(new ProductionReportService.Page(List.of(row()), 1, totals()));

        byte[] pdf = mvc.perform(get("/production/report/export").with(signedIn(manager)).param("personId", "4"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        byte[] xlsx = mvc.perform(get("/production/report/export").with(signedIn(manager)).param("format", "xlsx").param("bpoId", "12"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", ReportService.XLSX.toString()))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        verify(report).all(argThat(f -> f.bpoId() != null && f.bpoId() == 12L), any(), any());
    }

    private static Map<String, Object> row() {
        Map<String, Object> r = new HashMap<>();
        r.put("bpoId", 12L);
        r.put("bpoNo", "BPO-2026-000012");
        r.put("bpoDate", LocalDate.of(2026, 6, 3));
        r.put("dueDate", LocalDate.of(2026, 9, 24));
        r.put("dueState", "OVERDUE");
        r.put("dueText", "6 days overdue");
        r.put("marketingPerson", "Raihan");
        for (String s : List.of("lc", "weaving", "processing", "greigeReceived", "greigeIssued", "finished", "delivery")) {
            r.put(s + "Pct", 50);
            r.put(s + "Done", new BigDecimal("500"));
            r.put(s + "Of", new BigDecimal("1000"));
        }
        return r;
    }

    private static Map<String, Object> totals() {
        return Map.of("orders", 1, "quantity", 1000, "lcQuantity", 500, "inProduction", 600, "finished", 400,
            "delivered", 200, "pending", 800);
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

    private static long nextRoleId = 700;

    private static Role role(String name, Screen screen, Verb... verbs) {
        Role role = new Role(name, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

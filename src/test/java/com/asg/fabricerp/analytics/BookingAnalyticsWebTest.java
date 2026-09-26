package com.asg.fabricerp.analytics;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Booking analytics through the real {@link SecurityConfig} and layout. */
@WebMvcTest(controllers = BookingAnalyticsController.class)
@Import(SecurityConfig.class)
class BookingAnalyticsWebTest {

    private static final Long ORG = 1L;

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private FabricUserRepository userRepository;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private MarketingTeamRepository marketingTeams;
    @MockitoBean private OrgContext orgContext;

    @MockitoBean private BookingAnalyticsService service;

    private FabricUser analyst;   // may open the analytics
    private FabricUser clerk;     // works with bookings, but was not granted the analytics

    @BeforeEach
    void setUp() {
        analyst = user(1L, "analyst", role(10L, Screen.BOOKING_ANALYTICS));
        clerk = user(2L, "clerk", role(11L, Screen.BOOKING));
        for (FabricUser u : List.of(analyst, clerk)) {
            when(userDetailsService.reload(u.getUsername())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(ORG);
        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
    }

    @Test
    void thePageRendersInTheLayout_andTheSidebarListsItUnderAnalytics() throws Exception {
        mvc.perform(get("/analytics/booking").with(signedIn(analyst)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("data-analytics")))
            .andExpect(content().string(containsString("/js/booking-analytics.js")))
            .andExpect(content().string(containsString("href=\"/analytics/booking\"")))
            .andExpect(content().string(containsString("Booking analytics")))
            .andExpect(content().string(containsString("Pending approvals")));
    }

    @Test
    void theOverviewPassesTheFiltersToTheService() throws Exception {
        when(service.overview(any())).thenReturn(Map.of("currency", "USD"));
        mvc.perform(get("/api/analytics/booking/overview").with(signedIn(analyst))
                .param("from", "2026-01-01").param("to", "2026-03-31").param("view", "TEAM").param("teamId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("USD"));
        verify(service).overview(new BookingAnalyticsService.Request(java.time.LocalDate.of(2026, 1, 1),
            java.time.LocalDate.of(2026, 3, 31), AnalyticsView.TEAM, 3L, null, null, null, null, null, null));
    }

    @Test
    void anUnknownReportIsABadRequest() throws Exception {
        mvc.perform(get("/api/analytics/booking/reports/salaries").with(signedIn(analyst)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void usersWithoutTheGrantAreRefused() throws Exception {
        mvc.perform(get("/analytics/booking").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/booking/overview").with(signedIn(clerk))).andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/booking/register.csv").with(signedIn(clerk))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private static Role role(Long id, Screen screen) {
        Role role = new Role("Role " + id, null);
        role.setId(id);
        role.grant(screen, Verb.VIEW);
        return role;
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
}

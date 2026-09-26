package com.asg.fabricerp.fabric;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.fabric.booking.BookingController;
import com.asg.fabricerp.fabric.booking.BookingService;
import com.asg.fabricerp.fabric.setup.FabricAttributeController;
import com.asg.fabricerp.fabric.setup.FabricAttributeService;
import com.asg.fabricerp.security.AccessLogService;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserDetailsService;
import com.asg.fabricerp.security.FabricUserPrincipal;
import com.asg.fabricerp.security.Role;
import com.asg.fabricerp.security.Screen;
import com.asg.fabricerp.security.SecurityConfig;
import com.asg.fabricerp.security.Verb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The fabric document screens and the fabric setup lists render inside the application layout.
 * They used to return their templates directly, which served each page without the stylesheet,
 * the sidebar or the header - nothing failed, the page was just unstyled.
 */
import com.asg.fabricerp.production.ChainAccess;
import com.asg.fabricerp.production.ChainDocumentController;
import com.asg.fabricerp.production.ChainDocumentService;
import com.asg.fabricerp.production.ChainPostingService;
import com.asg.fabricerp.production.ChainViews;
import com.asg.fabricerp.production.FabricStockQueries;
import com.asg.fabricerp.production.ProcessRouteService;
import com.asg.fabricerp.production.ProductionBoardService;
import com.asg.fabricerp.production.ProductionScreensController;
import org.junit.jupiter.api.Test;
/**
 * Every fabric screen through the real {@link SecurityConfig} and layout: Booking, the nine
 * production-chain screens on {@link ChainDocumentController}, the boards, stock and routes.
 */
@WebMvcTest(controllers = {BookingController.class, ChainDocumentController.class, ProductionScreensController.class,
                           FabricAttributeController.class})
@Import({SecurityConfig.class, ChainAccess.class})
class FabricScreensWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private BookingService bookings;
    @MockitoBean private ChainDocumentService chainDocuments;
    @MockitoBean private ChainPostingService chainPosting;
    @MockitoBean private ChainViews chainViews;
    @MockitoBean private ProductionBoardService boards;
    @MockitoBean private FabricStockQueries stock;
    @MockitoBean private ProcessRouteService routes;
    @MockitoBean private FabricAttributeService attributes;

    private FabricUser planner;     // may view every screen
    private FabricUser storeKeeper; // greige receive only

    @BeforeEach
    void setUp() {
        Role role = new Role("Planner", null);
        role.setId(100L);
        Arrays.stream(Screen.values()).forEach(screen -> role.grant(screen, Verb.VIEW));
        planner = user(1L, "planner", role);
        Role store = new Role("Greige store", null);
        store.setId(101L);
        store.grant(Screen.GR, Verb.VIEW);
        store.grant(Screen.GR, Verb.CREATE);
        storeKeeper = user(2L, "store", store);
        for (FabricUser u : java.util.List.of(planner, storeKeeper)) {
            when(userDetailsService.reload(u.getUsername())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(1L);
    }

    @ParameterizedTest
    @CsvSource({
        "/bpo,              bpo,              Production orders",
        "/weaving-wo,       weaving-wo,       Weaving work orders",
        "/processing-wo,    processing-wo,    Dyeing work orders",
        "/greige-receive,   greige-receive,   Greige receipts",
        "/greige-issue,     greige-issue,     Greige issues",
        "/finished-receive, finished-receive, Finished receipts",
        "/requestforpi,     requestforpi,     Delivery schedules",
        "/delivery-order,   delivery-order,   Delivery orders",
        "/fabrics-delivery, fabrics-delivery, Fabrics deliveries"
    })
    void chainScreensRenderInTheLayoutWithTheirStepWired(String path, String slug, String title) throws Exception {
        mvc.perform(get(path).with(signedIn(planner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("id=\"chainTable\"")))
            .andExpect(content().string(containsString("\"slug\":\"" + slug + "\"")))
            .andExpect(content().string(containsString("/js/production-docs.js")))
            .andExpect(content().string(containsString(title)));
    }

    @Test
    void theBookingScreenStillRenders() throws Exception {
        mvc.perform(get("/booking").with(signedIn(planner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"bookingTable\"")));
    }

    @ParameterizedTest
    @CsvSource({
        "/production/board,          production-boards.js",
        "/production/delivery-board, production-boards.js",
        "/stock/fabric,              production-boards.js",
        "/setup/process-routes,      production-boards.js"
    })
    void boardsStockAndRoutesRender(String path, String script) throws Exception {
        mvc.perform(get(path).with(signedIn(planner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/" + script)))
            // The new menu section lists the store screens.
            .andExpect(content().string(containsString("Stores")))
            .andExpect(content().string(containsString("href=\"/greige-issue\"")));
    }

    @Test
    void eachChainScreenIsGuardedByItsOwnVerbs() throws Exception {
        mvc.perform(get("/greige-receive").with(signedIn(storeKeeper))).andExpect(status().isOk());
        mvc.perform(get("/greige-issue").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        mvc.perform(get("/api/greige-issue/7").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        mvc.perform(get("/production/board").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        // Viewing is not raising: the planner holds VIEW everywhere but CREATE nowhere.
        mvc.perform(post("/api/weaving-wo").with(signedIn(planner)).with(csrf())
                .contentType("application/json").content("{\"lines\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/greige-receive/5/post").with(signedIn(planner)).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(chainDocuments, chainPosting);
    }

    @Test
    void unknownPathsAreNotChainScreens() throws Exception {
        mvc.perform(get("/api/booking-lines").with(signedIn(planner))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"/setup/fabric/weave-type", "/setup/fabric/finish-type"})
    void fabricSetupListsRenderInTheLayout(String path) throws Exception {
        mvc.perform(get(path).with(signedIn(planner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("id=\"attributeTable\"")));
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

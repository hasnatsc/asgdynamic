package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentStatusTotal;
import com.asg.fabricerp.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every purchase and store screen through the real {@link SecurityConfig} and layout: the thirteen
 * documents on {@link SupplyDocumentController}, item stock and inventory periods - and each one
 * guarded by its own screen's verbs.
 */
@WebMvcTest(controllers = {SupplyDocumentController.class, ItemStockController.class})
@Import({SecurityConfig.class, SupplyAccess.class})
class SupplyScreensWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private SupplyDocumentService documents;
    @MockitoBean private SupplyPostingService posting;
    @MockitoBean private SupplyViews views;
    @MockitoBean private SupplyQueries queries;
    @MockitoBean private ItemStockQueries stock;
    @MockitoBean private InventoryPeriodService periods;

    private FabricUser viewer;     // may view every screen
    private FabricUser storeKeeper; // material issue and item stock only

    @BeforeEach
    void setUp() {
        Role role = new Role("Viewer", null);
        role.setId(100L);
        Arrays.stream(Screen.values()).forEach(screen -> role.grant(screen, Verb.VIEW));
        viewer = user(1L, "viewer", role);
        Role store = new Role("General store", null);
        store.setId(101L);
        store.grant(Screen.MI, Verb.VIEW);
        store.grant(Screen.MI, Verb.CREATE);
        store.grant(Screen.ITEM_STOCK, Verb.VIEW);
        storeKeeper = user(2L, "store", store);
        for (FabricUser u : List.of(viewer, storeKeeper)) {
            when(userDetailsService.reload(eq(u.getUsername()), any())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(1L);
    }

    @ParameterizedTest
    @CsvSource({
        "/store-requisition,       store-requisition,       Store requisitions",
        "/purchase-requisition,    purchase-requisition,    Purchase requisitions",
        "/purchase-order,          purchase-order,          Purchase orders",
        "/mrr,                     mrr,                     Material receipts (MRR)",
        "/purchase-return,         purchase-return,         Purchase returns",
        "/material-issue,          material-issue,          Material issues",
        "/direct-receive,          direct-receive,          Direct receives",
        "/transfer-request,        transfer-request,        Transfer requests",
        "/transfer-issue,          transfer-issue,          Transfer issues",
        "/transfer-receive,        transfer-receive,        Transfer receives",
        "/stock-adjustment,        stock-adjustment,        Stock adjustments",
        "/fabric-transfer-issue,   fabric-transfer-issue,   Fabric transfer issues",
        "/fabric-transfer-receive, fabric-transfer-receive, Fabric transfer receives"
    })
    void documentScreensRenderInTheLayoutWithTheirStepWired(String path, String slug, String title) throws Exception {
        mvc.perform(get(path).with(signedIn(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("id=\"supplyTable\"")))
            .andExpect(content().string(containsString("\"slug\":\"" + slug + "\"")))
            .andExpect(content().string(containsString("/js/supply-docs.js")))
            .andExpect(content().string(containsString(title)))
            // The viewer holds VIEW only: no New button.
            .andExpect(content().string(not(containsString("data-supply-new"))));
    }

    @ParameterizedTest
    @CsvSource({"/stock/items, data-stock-page=\"items\"", "/stock/periods, data-stock-page=\"periods\""})
    void stockScreensRender(String path, String marker) throws Exception {
        mvc.perform(get(path).with(signedIn(viewer)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(marker)))
            .andExpect(content().string(containsString("/js/supply-stock.js")))
            // The menu has a Purchase section.
            .andExpect(content().string(containsString("href=\"/purchase-order\"")));
    }

    @Test
    void eachScreenIsGuardedByItsOwnVerbs() throws Exception {
        mvc.perform(get("/material-issue").with(signedIn(storeKeeper)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-supply-new")));
        mvc.perform(get("/stock/items").with(signedIn(storeKeeper))).andExpect(status().isOk());
        mvc.perform(get("/purchase-order").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        mvc.perform(get("/api/mrr/7").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        mvc.perform(get("/stock/periods").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        mvc.perform(post("/api/stock/periods/2026-01/close").with(signedIn(storeKeeper)).with(csrf())).andExpect(status().isForbidden());
        // Viewing is not raising: the viewer holds VIEW everywhere but CREATE nowhere.
        mvc.perform(post("/api/purchase-order").with(signedIn(viewer)).with(csrf())
                .contentType("application/json").content("{\"lines\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/mrr/5/post").with(signedIn(viewer)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/stock/periods/2026-01/reopen").with(signedIn(viewer)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(documents, posting, periods);
    }

    @Test
    void summaryCountsTheListByStatusUnderTheSameFiltersAndItsScreensGuard() throws Exception {
        when(documents.statusTotals(SupplyStep.PO, LocalDate.of(2026, 9, 1), null, "PO-2026"))
            .thenReturn(List.of(
                new DocumentStatusTotal(BusinessDocumentStatus.APPROVED, 3L, new BigDecimal("140"), new BigDecimal("5800.00"), 1L),
                new DocumentStatusTotal(BusinessDocumentStatus.DRAFT, 2L, null, null, null)));

        mvc.perform(get("/api/purchase-order/summary").with(signedIn(viewer))
                .param("search[value]", "  PO-2026 ").param("from", "2026-09-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("APPROVED"))
            .andExpect(jsonPath("$[0].documents").value(3))
            .andExpect(jsonPath("$[0].value").value(5800.00))
            .andExpect(jsonPath("$[0].overdue").value(1))
            // A sum over no values reads as zero, not null.
            .andExpect(jsonPath("$[1].quantity").value(0))
            .andExpect(jsonPath("$[1].overdue").value(0));

        mvc.perform(get("/api/purchase-order/summary").with(signedIn(storeKeeper))).andExpect(status().isForbidden());
        mvc.perform(get("/api/material-issue/summary").with(signedIn(storeKeeper))).andExpect(status().isOk());
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

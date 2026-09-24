package com.asg.fabricerp.inventory.item;

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

import com.asg.fabricerp.web.Navigation;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The item screens through the real {@link SecurityConfig} and layout: every page renders, the
 * sidebar gains the Inventory section only for users granted it, and the create/amend split on
 * the shared save endpoints holds.
 */
@WebMvcTest(controllers = {InventoryItemController.class, ItemSetupController.class, ItemCategoryController.class,
                           YarnBlendController.class, InventoryLookupController.class})
@Import(SecurityConfig.class)
class InventoryWebTest {

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

    @MockitoBean private InventoryItemService items;
    @MockitoBean private ItemCategoryService categories;
    @MockitoBean private UnitOfMeasureService uoms;
    @MockitoBean private HsCodeService hsCodes;
    @MockitoBean private ItemBrandService brands;
    @MockitoBean private ItemModelService models;
    @MockitoBean private YarnTypeService yarnTypes;
    @MockitoBean private YarnCountService yarnCounts;
    @MockitoBean private YarnPlyService yarnPlies;
    @MockitoBean private YarnBlendService yarnBlends;

    private FabricUser storekeeper;   // full rights on both item screens
    private FabricUser amender;       // may amend setup, not create
    private FabricUser clerk;         // no inventory grant at all

    @BeforeEach
    void setUp() {
        storekeeper = user(1L, "store", role(Screen.ITEM, Verb.values()), role(Screen.ITEM_SETUP, Verb.values()));
        amender = user(2L, "amender", role(Screen.ITEM_SETUP, Verb.VIEW, Verb.AMEND));
        clerk = user(3L, "clerk", role(Screen.BOOKING, Verb.VIEW));
        for (FabricUser u : List.of(storekeeper, amender, clerk)) {
            when(userDetailsService.reload(u.getUsername())).thenAnswer(i -> Optional.of(new FabricUserPrincipal(u)));
        }
        when(orgContext.requireOrganizationId()).thenReturn(ORG);
        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
    }

    // ---------------------------------------------------------------------------- pages render

    @Test
    void theItemScreenRendersWithEveryItemType() throws Exception {
        mvc.perform(get("/inventory/items").with(signedIn(storekeeper)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"itemTable\"")))
            .andExpect(content().string(containsString("Dyes &amp; Chemicals")))
            .andExpect(content().string(containsString("New item")))
            // The sidebar gains the Inventory section and the setup group.
            .andExpect(content().string(containsString("href=\"/inventory/uoms\"")))
            .andExpect(content().string(containsString("href=\"/inventory/yarn-blends\"")));
    }

    @Test
    void everySetupPageRenders() throws Exception {
        for (ItemSetupMenu.Page page : ItemSetupMenu.PAGES) {
            mvc.perform(get(page.path()).with(signedIn(storekeeper)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nav-sublink is-active")));
        }
    }

    @Test
    void theGenericListCarriesItsSpecIntoThePage() throws Exception {
        mvc.perform(get("/inventory/hs-codes").with(signedIn(storekeeper)))
            .andExpect(status().isOk())
            // Thymeleaf's JavaScript serializer escapes "/" as "\/".
            .andExpect(content().string(containsString("\"api\":\"\\/api\\/inventory\\/hs-codes\"")))
            .andExpect(content().string(containsString("Customs duty %")));
    }

    @Test
    void usersWithoutTheGrantSeeNoInventoryMenu_andAreRefused() throws Exception {
        mvc.perform(get("/inventory/items").with(signedIn(clerk)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/items").with(signedIn(clerk)))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------- api

    @Test
    void theGridReturnsTheServicesRows() throws Exception {
        when(uoms.search(any(), any())).thenReturn(new PageImpl<>(List.of(Map.of("id", 1, "code", "U111"))));

        mvc.perform(get("/api/inventory/uoms").with(signedIn(storekeeper)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("U111"))
            .andExpect(jsonPath("$.recordsTotal").value(1));
    }

    @Test
    void amendRightsDoNotAllowCreating() throws Exception {
        mvc.perform(post("/api/inventory/brands").with(signedIn(amender)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New brand\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(containsString("SCREEN_ITEM_SETUP_CREATE")));
        verify(brands, never()).save(any());

        when(brands.save(any())).thenReturn(Map.of("id", 5, "code", "IBAF0001"));
        mvc.perform(post("/api/inventory/brands").with(signedIn(amender)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":5,\"name\":\"Renamed\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void aRefusedRuleReachesTheScreenAsASentence() throws Exception {
        when(items.save(any())).thenThrow(new IllegalArgumentException("An item named 'Cotton' already exists."));

        mvc.perform(post("/api/inventory/items").with(signedIn(storekeeper)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"itemType\":\"FIBER\",\"name\":\"Cotton\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("An item named 'Cotton' already exists."));
    }

    @Test
    void lookupsAreOpenToAnySignedInUser() throws Exception {
        when(uoms.lookup()).thenReturn(List.of(new Masters.Option(1L, "U111", "Kilogram")));

        mvc.perform(get("/api/lookup/inventory/uoms").with(signedIn(clerk)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].text").value("Kilogram"));
    }

    @Test
    void theSidebarShowsOnlyWhatIsGranted() throws Exception {
        mvc.perform(get("/inventory/uoms").with(signedIn(storekeeper)))
            .andExpect(content().string(containsString(">Inventory<")))
            .andExpect(content().string(not(containsString("href=\"/booking\""))));

        // The clerk cannot open an inventory page at all, so ask the menu builder directly.
        Set<String> clerkAuthorities = new FabricUserPrincipal(clerk).getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(Navigation.build(clerkAuthorities, "/"))
            .extracting(Navigation.Section::label)
            .containsExactly("Sales");
    }

    // ---------------------------------------------------------------------------- fixtures

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

    private static long nextRoleId = 200;

    private static Role role(Screen screen, Verb... verbs) {
        Role role = new Role(screen.name() + " role " + nextRoleId, null);
        role.setId(nextRoleId++);
        role.grant(screen, verbs);
        return role;
    }
}

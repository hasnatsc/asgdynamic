package com.asg.fabricerp.fabric;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.fabric.booking.BookingController;
import com.asg.fabricerp.fabric.booking.BookingService;
import com.asg.fabricerp.fabric.deliveryorder.DeliveryOrderController;
import com.asg.fabricerp.fabric.deliveryorder.DeliveryOrderService;
import com.asg.fabricerp.fabric.fabricsdelivery.FabricsDeliveryController;
import com.asg.fabricerp.fabric.fabricsdelivery.FabricsDeliveryService;
import com.asg.fabricerp.fabric.greigereceive.GreigeReceiveController;
import com.asg.fabricerp.fabric.greigereceive.GreigeReceiveService;
import com.asg.fabricerp.fabric.processingworkorder.ProcessingWorkOrderController;
import com.asg.fabricerp.fabric.processingworkorder.ProcessingWorkOrderService;
import com.asg.fabricerp.fabric.productionorder.BpoController;
import com.asg.fabricerp.fabric.productionorder.BpoService;
import com.asg.fabricerp.fabric.requestforpi.RequestForPiController;
import com.asg.fabricerp.fabric.requestforpi.RequestForPiService;
import com.asg.fabricerp.fabric.setup.FabricAttributeController;
import com.asg.fabricerp.fabric.setup.FabricAttributeService;
import com.asg.fabricerp.fabric.weavingworkorder.WeavingWorkOrderController;
import com.asg.fabricerp.fabric.weavingworkorder.WeavingWorkOrderService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The fabric document screens and the fabric setup lists render inside the application layout.
 * They used to return their templates directly, which served each page without the stylesheet,
 * the sidebar or the header - nothing failed, the page was just unstyled.
 */
@WebMvcTest(controllers = {BookingController.class, BpoController.class, RequestForPiController.class,
                           DeliveryOrderController.class, FabricsDeliveryController.class,
                           GreigeReceiveController.class, ProcessingWorkOrderController.class,
                           WeavingWorkOrderController.class, FabricAttributeController.class})
@Import(SecurityConfig.class)
class FabricScreensWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private FabricUserDetailsService userDetailsService;
    @MockitoBean private AccessLogService accessLogService;
    @MockitoBean private BusinessUnitRepository businessUnits;
    @MockitoBean private WarehouseRepository warehouses;
    @MockitoBean private OrgContext orgContext;
    @MockitoBean private BookingService bookings;
    @MockitoBean private BpoService bpos;
    @MockitoBean private RequestForPiService rpis;
    @MockitoBean private DeliveryOrderService deliveryOrders;
    @MockitoBean private FabricsDeliveryService fabricsDeliveries;
    @MockitoBean private GreigeReceiveService greigeReceipts;
    @MockitoBean private ProcessingWorkOrderService processingOrders;
    @MockitoBean private WeavingWorkOrderService weavingOrders;
    @MockitoBean private FabricAttributeService attributes;

    private FabricUser planner;

    @BeforeEach
    void setUp() {
        Role role = new Role("Planner", null);
        role.setId(100L);
        Arrays.stream(Screen.values()).forEach(screen -> role.grant(screen, Verb.VIEW));
        planner = new FabricUser("planner", "hash", 10L, "AF");
        planner.setId(1L);
        planner.setOrganizationId(1L);
        planner.setUnrestricted(true);
        planner.addRole(role);
        when(userDetailsService.reload("planner")).thenAnswer(i -> Optional.of(new FabricUserPrincipal(planner)));
    }

    @ParameterizedTest
    @CsvSource({
        "/booking,          bookingTable, /api/booking",
        "/bpo,              bpoTable,     /api/bpo",
        "/requestforpi,     rpiTable,     /api/requestforpi",
        "/delivery-order,   dloTable,     /api/delivery-order",
        "/fabrics-delivery, fdTable,      /api/fabrics-delivery",
        "/greige-receive,   receiptTable, /api/greige-receive",
        "/processing-wo,    woTable,      /api/processing-wo",
        "/weaving-wo,       woTable,      /api/weaving-wo"
    })
    void documentScreensRenderInTheLayoutWithTheirListWired(String path, String table, String api) throws Exception {
        mvc.perform(get(path).with(signedIn(planner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("/css/app.css")))
            .andExpect(content().string(containsString("id=\"" + table + "\"")))
            .andExpect(content().string(containsString("api: '" + api + "'")))
            // The editor's workflow rail comes from fragments/ui :: workflow, starting at Draft.
            .andExpect(content().string(containsString("aria-label=\"Document workflow\"")))
            .andExpect(content().string(containsString("class=\"step is-current\"")));
    }

    @ParameterizedTest
    @CsvSource({"/setup/fabric/weave-type", "/setup/fabric/finish-type"})
    void fabricSetupListsRenderInTheLayout(String path) throws Exception {
        mvc.perform(get(path).with(signedIn(planner)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sidebar\"")))
            .andExpect(content().string(containsString("id=\"attributeTable\"")));
    }

    private static RequestPostProcessor signedIn(FabricUser user) {
        var principal = new FabricUserPrincipal(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}

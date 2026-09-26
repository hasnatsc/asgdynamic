package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.DeliverStage;
import com.asg.fabricerp.global.documents.GreigeKey;
import com.asg.fabricerp.global.documents.ProcessKind;
import com.asg.fabricerp.global.documents.RouteCode;
import com.asg.fabricerp.global.documents.YarnPrep;
import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * The boards the chain is run from, the fabric stock screen, and the process routes master.
 */
@Controller
public class ProductionScreensController {

    private final ProductionBoardService boards;
    private final FabricStockQueries stock;
    private final ProcessRouteService routes;
    private final WarehouseRepository warehouses;
    private final OrgContext context;

    public ProductionScreensController(ProductionBoardService boards, FabricStockQueries stock, ProcessRouteService routes,
                                       WarehouseRepository warehouses, OrgContext context) {
        this.boards = boards;
        this.stock = stock;
        this.routes = routes;
        this.warehouses = warehouses;
        this.context = context;
    }

    // ---------------------------------------------------------------------- production board

    @GetMapping("/production/board")
    @PreAuthorize("hasAuthority('SCREEN_PROD_BOARD_VIEW')")
    public String productionBoard(Model model) {
        model.addAttribute("title", "Production board");
        model.addAttribute("canWeave", AuthorityChecks.holds("SCREEN_WWO_CREATE"));
        model.addAttribute("canDye", AuthorityChecks.holds("SCREEN_PWO_CREATE"));
        model.addAttribute("canSchedule", AuthorityChecks.holds("SCREEN_RPI_CREATE"));
        model.addAttribute("lateDays", ProductionBoardService.LATE_WITHIN_DAYS);
        model.addAttribute("content", "production/board :: content");
        return "layout/main";
    }

    @GetMapping("/api/production/board")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROD_BOARD_VIEW')")
    public ProductionBoardService.Page productionBoardData(
            @RequestParam(required = false) Long teamId, @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) String fabricType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBy,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean includeCompleted,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return boards.productionBoard(new ProductionBoardService.BoardFilter(teamId, buyerId, fabricType, dueBy, q, includeCompleted),
            page, Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/production/delivery-board")
    @PreAuthorize("hasAuthority('SCREEN_DELIVERY_BOARD_VIEW')")
    public String deliveryBoard(Model model) {
        model.addAttribute("title", "Ready to deliver");
        model.addAttribute("canOrder", AuthorityChecks.holds("SCREEN_DO_CREATE"));
        model.addAttribute("content", "production/delivery-board :: content");
        return "layout/main";
    }

    @GetMapping("/api/production/delivery-board")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DELIVERY_BOARD_VIEW')")
    public ProductionBoardService.Page deliveryBoardData(@RequestParam(required = false) Long buyerId,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "false") boolean deliverableOnly,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "50") int size) {
        return boards.readyToDeliver(buyerId, q, deliverableOnly, page, Math.min(Math.max(size, 1), 200));
    }

    // ---------------------------------------------------------------------------- fabric stock

    @GetMapping("/stock/fabric")
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_STOCK_VIEW')")
    public String fabricStock(Model model) {
        model.addAttribute("title", "Fabric stock");
        model.addAttribute("stores", warehouses.lookup(context.requireOrganizationId()));
        model.addAttribute("canSetRoles", AuthorityChecks.holds("SCREEN_FABRIC_STOCK_AMEND"));
        model.addAttribute("content", "production/stock :: content");
        return "layout/main";
    }

    @GetMapping("/api/stock/fabric")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_STOCK_VIEW')")
    public Map<String, Object> balances(@RequestParam(required = false) Long warehouseId,
                                        @RequestParam(required = false) String stage,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "false") boolean withZero,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return stock.balances(warehouseId, stage, q, withZero, page, Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/api/stock/fabric/lots/{lotId:\\d+}/moves")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_STOCK_VIEW')")
    public List<Map<String, Object>> moves(@PathVariable Long lotId, @RequestParam(required = false) Long warehouseId) {
        return stock.moves(lotId, warehouseId);
    }

    @PostMapping("/api/stock/fabric/stores/{warehouseId:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_FABRIC_STOCK_AMEND')")
    public Map<String, Object> storeRoles(@PathVariable Long warehouseId, @RequestBody Map<String, Boolean> roles) {
        return ChainDocumentController.store(routes.setStoreRoles(warehouseId,
            Boolean.TRUE.equals(roles.get("holdsGreige")), Boolean.TRUE.equals(roles.get("holdsFinished"))));
    }

    // -------------------------------------------------------------------------- process routes

    @GetMapping("/setup/process-routes")
    @PreAuthorize("hasAuthority('SCREEN_PROCESS_ROUTE_VIEW')")
    public String routesPage(Model model) {
        model.addAttribute("title", "Process routes");
        model.addAttribute("canAmend", AuthorityChecks.holds("SCREEN_PROCESS_ROUTE_AMEND")
            || AuthorityChecks.holds("SCREEN_PROCESS_ROUTE_CREATE"));
        model.addAttribute("options", Map.of(
            "routeCodes", Arrays.stream(RouteCode.values()).map(v -> Map.of("value", v.name(), "label", v.label())).toList(),
            "processKinds", Arrays.stream(ProcessKind.values()).map(v -> Map.of("value", v.name(), "label", v.label())).toList(),
            "yarnPreps", Arrays.stream(YarnPrep.values()).map(v -> Map.of("value", v.name(), "label", v.label())).toList(),
            "greigeKeys", List.of(Map.of("value", GreigeKey.CONSTRUCTION.name(), "label", "Per fabric line (one greige, dyed into colours)"),
                                  Map.of("value", GreigeKey.COLOUR.name(), "label", "Per colour (colour is in the yarn)")),
            "deliverStages", List.of(Map.of("value", DeliverStage.GREIGE.name(), "label", "Greige store"),
                                     Map.of("value", DeliverStage.FINISHED.name(), "label", "Finished store (weaving, then dyeing)"))));
        model.addAttribute("content", "production/routes :: content");
        return "layout/main";
    }

    @GetMapping("/api/process-routes")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROCESS_ROUTE_VIEW')")
    public List<Map<String, Object>> routeList() {
        return routes.list().stream().map(ProductionScreensController::route).toList();
    }

    @PostMapping("/api/process-routes")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROCESS_ROUTE_CREATE') or hasAuthority('SCREEN_PROCESS_ROUTE_AMEND')")
    public Map<String, Object> routeSave(@RequestParam(required = false) Long id, @RequestBody ProcessRouteService.RouteRequest request) {
        AuthorityChecks.require(id == null ? "SCREEN_PROCESS_ROUTE_CREATE" : "SCREEN_PROCESS_ROUTE_AMEND");
        return route(routes.save(id, request));
    }

    @DeleteMapping("/api/process-routes/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PROCESS_ROUTE_DELETE')")
    public Map<String, Object> routeDelete(@PathVariable Long id) {
        routes.delete(id);
        return Map.of("deleted", id);
    }

    private static Map<String, Object> route(ProcessRoute r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("fabricType", r.getFabricType());
        m.put("routeCode", r.getRouteCode());
        m.put("routeLabel", r.getRouteCode().label());
        m.put("needsProcessing", r.isNeedsProcessing());
        m.put("processKind", r.getProcessKind());
        m.put("yarnPrep", r.getYarnPrep());
        m.put("greigeKey", r.getGreigeKey());
        m.put("deliverStage", r.getDeliverStage());
        m.put("greigeAllowancePct", r.getGreigeAllowancePct());
        m.put("receiveTolerancePct", r.getReceiveTolerancePct());
        m.put("deliveryTolerancePct", r.getDeliveryTolerancePct());
        m.put("remarks", r.getRemarks());
        m.put("active", r.getActive());
        return m;
    }
}

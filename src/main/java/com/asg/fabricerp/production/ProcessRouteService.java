package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.DeliverStage;
import com.asg.fabricerp.global.documents.GreigeKey;
import com.asg.fabricerp.global.documents.ProcessKind;
import com.asg.fabricerp.global.documents.RouteCode;
import com.asg.fabricerp.global.documents.YarnPrep;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** The process routes master, and the store roles that decide which store may take which document. */
@Service
public class ProcessRouteService {

    public record RouteRequest(String fabricType, RouteCode routeCode, ProcessKind processKind, YarnPrep yarnPrep,
                               GreigeKey greigeKey, DeliverStage deliverStage, BigDecimal greigeAllowancePct,
                               BigDecimal receiveTolerancePct, BigDecimal deliveryTolerancePct, String remarks, Boolean active) { }

    private final ProcessRouteRepository routes;
    private final WarehouseRepository warehouses;
    private final OrgContext context;

    public ProcessRouteService(ProcessRouteRepository routes, WarehouseRepository warehouses, OrgContext context) {
        this.routes = routes;
        this.warehouses = warehouses;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public List<ProcessRoute> list() {
        return routes.findLive(context.requireOrganizationId());
    }

    @Transactional
    public ProcessRoute save(Long id, RouteRequest r) {
        Long orgId = context.requireOrganizationId();
        if (r.fabricType() == null || r.fabricType().isBlank()) throw new IllegalArgumentException("Name the fabric type");
        if (r.routeCode() == null || r.greigeKey() == null || r.deliverStage() == null) {
            throw new IllegalArgumentException("Choose the route, how greige is woven, and the store it is delivered from");
        }
        ProcessRoute route = id == null ? new ProcessRoute()
            : routes.findScoped(id, orgId).orElseThrow(() -> new IllegalArgumentException("Route not found: " + id));
        routes.findForFabricType(orgId, r.fabricType()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new IllegalArgumentException("'%s' already has a route".formatted(other.getFabricType()));
        });
        if (route.getId() == null) route.setOrganizationId(orgId);
        route.setFabricType(r.fabricType());
        route.setRouteCode(r.routeCode());
        route.setDeliverStage(r.deliverStage());
        route.setProcessKind(r.deliverStage() == DeliverStage.FINISHED
            ? (r.processKind() == null ? ProcessKind.DYE : r.processKind()) : null);
        route.setYarnPrep(r.yarnPrep());
        route.setGreigeKey(r.greigeKey());
        route.setGreigeAllowancePct(pct(r.greigeAllowancePct(), BigDecimal.ZERO, "Greige allowance"));
        route.setReceiveTolerancePct(pct(r.receiveTolerancePct(), BigDecimal.valueOf(5), "Receive tolerance"));
        route.setDeliveryTolerancePct(pct(r.deliveryTolerancePct(), BigDecimal.valueOf(3), "Delivery tolerance"));
        route.setRemarks(r.remarks() == null || r.remarks().isBlank() ? null : r.remarks().strip());
        route.setActive(r.active() == null || r.active());
        return routes.save(route);
    }

    /** Retired, not deleted: orders already carry their own copy of the route. */
    @Transactional
    public void delete(Long id) {
        ProcessRoute route = routes.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Route not found: " + id));
        route.markDeleted();
        routes.save(route);
    }

    @Transactional
    public Warehouse setStoreRoles(Long warehouseId, boolean greige, boolean finished) {
        Warehouse store = warehouses.findScoped(warehouseId, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Store not found: " + warehouseId));
        store.setHoldsGreige(greige);
        store.setHoldsFinished(finished);
        return warehouses.save(store);
    }

    private static BigDecimal pct(BigDecimal v, BigDecimal fallback, String name) {
        BigDecimal x = v == null ? fallback : v;
        if (x.signum() < 0 || x.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException(name + " is between 0 and 100 %");
        return x;
    }
}

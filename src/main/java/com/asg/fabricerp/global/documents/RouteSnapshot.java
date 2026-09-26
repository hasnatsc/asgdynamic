package com.asg.fabricerp.global.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A fabric type's process route as it was when the production order was saved - copied from
 * fab_process_routes onto the order's fabric line, and carried down to every document raised
 * against it, so a later change to the master never rewrites an order in progress.
 *
 * <p>Empty ({@link #isSet()} false) on Bookings and on any line whose type has no route.
 */
@Embeddable
public class RouteSnapshot {

    @Enumerated(EnumType.STRING)
    @Column(name = "route_code", length = 30)
    private RouteCode routeCode;

    @Column(name = "needs_processing")
    private Boolean needsProcessing;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_process_kind", length = 20)
    private ProcessKind processKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "yarn_prep", length = 20)
    private YarnPrep yarnPrep;

    @Enumerated(EnumType.STRING)
    @Column(name = "greige_key", length = 20)
    private GreigeKey greigeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "deliver_stage", length = 20)
    private DeliverStage deliverStage;

    @Column(name = "greige_allowance_pct", precision = 6, scale = 3)
    private BigDecimal greigeAllowancePct;

    @Column(name = "receive_tolerance_pct", precision = 6, scale = 3)
    private BigDecimal receiveTolerancePct;

    @Column(name = "delivery_tolerance_pct", precision = 6, scale = 3)
    private BigDecimal deliveryTolerancePct;

    public RouteSnapshot() { }

    public RouteSnapshot(RouteCode routeCode, boolean needsProcessing, ProcessKind processKind, YarnPrep yarnPrep,
                         GreigeKey greigeKey, DeliverStage deliverStage, BigDecimal greigeAllowancePct,
                         BigDecimal receiveTolerancePct, BigDecimal deliveryTolerancePct) {
        this.routeCode = routeCode;
        this.needsProcessing = needsProcessing;
        this.processKind = processKind;
        this.yarnPrep = yarnPrep;
        this.greigeKey = greigeKey;
        this.deliverStage = deliverStage;
        this.greigeAllowancePct = greigeAllowancePct;
        this.receiveTolerancePct = receiveTolerancePct;
        this.deliveryTolerancePct = deliveryTolerancePct;
    }

    public RouteSnapshot copy() {
        return new RouteSnapshot(routeCode, Boolean.TRUE.equals(needsProcessing), processKind, yarnPrep, greigeKey,
            deliverStage, greigeAllowancePct, receiveTolerancePct, deliveryTolerancePct);
    }

    public boolean isSet()                     { return routeCode != null; }
    public RouteCode getRouteCode()            { return routeCode; }
    public boolean isNeedsProcessing()         { return Boolean.TRUE.equals(needsProcessing); }
    public ProcessKind getProcessKind()        { return processKind; }
    public YarnPrep getYarnPrep()              { return yarnPrep == null ? YarnPrep.NONE : yarnPrep; }
    public GreigeKey getGreigeKey()            { return greigeKey == null ? GreigeKey.COLOUR : greigeKey; }
    public DeliverStage getDeliverStage()      { return deliverStage == null ? DeliverStage.GREIGE : deliverStage; }
    public BigDecimal getGreigeAllowancePct()  { return nz(greigeAllowancePct); }
    public BigDecimal getReceiveTolerancePct() { return nz(receiveTolerancePct); }
    public BigDecimal getDeliveryTolerancePct() { return nz(deliveryTolerancePct); }

    /** The planner may change the allowance per line; the rest of the route is the master's. */
    public void setGreigeAllowancePct(BigDecimal v) {
        if (v != null && (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("A greige allowance is between 0 and 100 %");
        }
        this.greigeAllowancePct = v;
    }

    /** {@code quantity} grossed up by {@code pct} percent, to six places. */
    public static BigDecimal plus(BigDecimal quantity, BigDecimal pct) {
        BigDecimal factor = BigDecimal.ONE.add(nz(pct).movePointLeft(2));
        return nz(quantity).multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }

    /** The greige to weave for {@code finished}: finished × (1 + allowance). */
    public BigDecimal greigeFor(BigDecimal finished) {
        return plus(finished, getGreigeAllowancePct());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

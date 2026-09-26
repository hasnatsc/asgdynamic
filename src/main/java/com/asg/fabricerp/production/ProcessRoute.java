package com.asg.fabricerp.production;

import com.asg.fabricerp.common.BaseOrgEntity;
import com.asg.fabricerp.global.documents.DeliverStage;
import com.asg.fabricerp.global.documents.GreigeKey;
import com.asg.fabricerp.global.documents.ProcessKind;
import com.asg.fabricerp.global.documents.RouteCode;
import com.asg.fabricerp.global.documents.RouteSnapshot;
import com.asg.fabricerp.global.documents.YarnPrep;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One fabric type's process route - the master behind {@link RouteSnapshot}. Kept in data, not
 * code, so production can change a type's route or its allowances without a release.
 */
@Entity
@Table(name = "fab_process_routes")
public class ProcessRoute extends BaseOrgEntity {

    @Column(name = "fabric_type", nullable = false, length = 60)
    private String fabricType;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_code", nullable = false, length = 30)
    private RouteCode routeCode;

    @Column(name = "needs_processing", nullable = false)
    private Boolean needsProcessing = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_kind", length = 20)
    private ProcessKind processKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "yarn_prep", nullable = false, length = 20)
    private YarnPrep yarnPrep = YarnPrep.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "greige_key", nullable = false, length = 20)
    private GreigeKey greigeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "deliver_stage", nullable = false, length = 20)
    private DeliverStage deliverStage;

    @Column(name = "greige_allowance_pct", nullable = false, precision = 6, scale = 3)
    private BigDecimal greigeAllowancePct = BigDecimal.ZERO;

    @Column(name = "receive_tolerance_pct", nullable = false, precision = 6, scale = 3)
    private BigDecimal receiveTolerancePct = BigDecimal.valueOf(5);

    @Column(name = "delivery_tolerance_pct", nullable = false, precision = 6, scale = 3)
    private BigDecimal deliveryTolerancePct = BigDecimal.valueOf(3);

    @Column(length = 300)
    private String remarks;

    public ProcessRoute() { }

    public String getFabricType()                 { return fabricType; }
    public void setFabricType(String v)           { this.fabricType = v == null ? null : v.strip(); }
    public RouteCode getRouteCode()               { return routeCode; }
    public void setRouteCode(RouteCode v)         { this.routeCode = v; }
    public boolean isNeedsProcessing()            { return Boolean.TRUE.equals(needsProcessing); }
    public ProcessKind getProcessKind()           { return processKind; }
    public void setProcessKind(ProcessKind v)     { this.processKind = v; }
    public YarnPrep getYarnPrep()                 { return yarnPrep; }
    public void setYarnPrep(YarnPrep v)           { this.yarnPrep = v == null ? YarnPrep.NONE : v; }
    public GreigeKey getGreigeKey()               { return greigeKey; }
    public void setGreigeKey(GreigeKey v)         { this.greigeKey = v; }
    public DeliverStage getDeliverStage()         { return deliverStage; }
    public BigDecimal getGreigeAllowancePct()     { return greigeAllowancePct; }
    public void setGreigeAllowancePct(BigDecimal v) { this.greigeAllowancePct = v; }
    public BigDecimal getReceiveTolerancePct()    { return receiveTolerancePct; }
    public void setReceiveTolerancePct(BigDecimal v) { this.receiveTolerancePct = v; }
    public BigDecimal getDeliveryTolerancePct()   { return deliveryTolerancePct; }
    public void setDeliveryTolerancePct(BigDecimal v) { this.deliveryTolerancePct = v; }
    public String getRemarks()                    { return remarks; }
    public void setRemarks(String v)              { this.remarks = v; }

    /** A route that dyes or finishes is delivered finished; one that does not, as greige. */
    public void setDeliverStage(DeliverStage v) {
        this.deliverStage = v;
        this.needsProcessing = v == DeliverStage.FINISHED;
    }

    public RouteSnapshot snapshot() {
        return new RouteSnapshot(routeCode, isNeedsProcessing(), processKind, yarnPrep, greigeKey, deliverStage,
            greigeAllowancePct, receiveTolerancePct, deliveryTolerancePct);
    }
}

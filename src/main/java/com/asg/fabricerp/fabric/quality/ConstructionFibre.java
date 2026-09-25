package com.asg.fabricerp.fabric.quality;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import com.asg.fabricerp.inventory.item.FiberType;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** One fibre's share of a {@link Construction}'s composition. The shares total 100. */
@Entity
@Table(name = "fab_construction_fibres")
public class ConstructionFibre extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "construction_id", nullable = false, updatable = false)
    private Construction construction;

    @Column(nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiber_type", nullable = false, length = 30)
    private FiberType fiberType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    protected ConstructionFibre() { }

    public ConstructionFibre(FiberType fiberType, BigDecimal percentage) {
        this.fiberType = fiberType;
        this.percentage = percentage;
    }

    void attachTo(Construction owner, int order) {
        this.construction = owner;
        this.sequence = order;
        setOrganizationId(owner.getOrganizationId());
    }

    public Integer getSequence()      { return sequence; }
    public FiberType getFiberType()   { return fiberType; }
    public BigDecimal getPercentage() { return percentage; }
}

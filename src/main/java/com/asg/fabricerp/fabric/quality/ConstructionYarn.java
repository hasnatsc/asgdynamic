package com.asg.fabricerp.fabric.quality;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import com.asg.fabricerp.inventory.item.YarnBlend;
import com.asg.fabricerp.inventory.item.YarnCount;
import com.asg.fabricerp.inventory.item.YarnType;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One warp or weft yarn of a {@link Construction}, in loom order - the legacy "Warp Count 1 /
 * Ratio 1" boxes as a row. Count is required; type and blend are the rest of what identifies a
 * YARN item, so planning can resolve the row to a stocked yarn.
 */
@Entity
@Table(name = "fab_construction_yarns")
public class ConstructionYarn extends BaseOrgLineEntity {

    public enum Direction { WARP, WEFT }

    /** The legacy grid's three boxes per direction. */
    public static final int MAX_PER_DIRECTION = 3;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "construction_id", nullable = false, updatable = false)
    private Construction construction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(nullable = false)
    private Integer sequence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "yarn_count_id", nullable = false)
    private YarnCount yarnCount;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "yarn_type_id")
    private YarnType yarnType;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "yarn_blend_id")
    private YarnBlend yarnBlend;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal ratio = BigDecimal.ONE;

    @Column(length = 300)
    private String remarks;

    protected ConstructionYarn() { }

    public ConstructionYarn(Direction direction, int sequence, YarnCount yarnCount, YarnType yarnType,
                            YarnBlend yarnBlend, BigDecimal ratio, String remarks) {
        this.direction = direction;
        this.sequence = sequence;
        this.yarnCount = yarnCount;
        this.yarnType = yarnType;
        this.yarnBlend = yarnBlend;
        this.ratio = ratio == null ? BigDecimal.ONE : ratio;
        this.remarks = remarks;
    }

    void attachTo(Construction owner) {
        this.construction = owner;
        setOrganizationId(owner.getOrganizationId());
    }

    public Direction getDirection()  { return direction; }
    public Integer getSequence()     { return sequence; }
    public YarnCount getYarnCount()  { return yarnCount; }
    public YarnType getYarnType()    { return yarnType; }
    public YarnBlend getYarnBlend()  { return yarnBlend; }
    public BigDecimal getRatio()     { return ratio; }
    public String getRemarks()       { return remarks; }
}

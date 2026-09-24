package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One fiber's share of a {@link YarnBlend}. The fiber is an {@link InventoryItem} of type
 * {@link ItemType#FIBER}, so the same record that is bought and stocked is the one blended.
 *
 * <p>SpindleERP also maps a {@code raw_material_item_id} here; nothing ever writes it, so it
 * was not carried over.
 */
@Entity
@Table(
    name = "yrn_blend_components",
    indexes = {
        @Index(name = "ix_yrn_bc_blend", columnList = "blend_id"),
        @Index(name = "ix_yrn_bc_fiber", columnList = "fiber_item_id")
    })
public class YarnBlendComponent extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blend_id", nullable = false)
    private YarnBlend blend;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiber_item_id", nullable = false)
    private InventoryItem fiber;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Certification certification;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    protected YarnBlendComponent() { }

    public YarnBlendComponent(InventoryItem fiber, BigDecimal percentage,
                              Certification certification, String remarks) {
        this.fiber = fiber;
        this.percentage = percentage;
        this.certification = certification;
        this.remarks = remarks;
    }

    void attachTo(YarnBlend owner, int order) {
        this.blend = owner;
        this.displayOrder = order;
        setOrganizationId(owner.getOrganizationId());
    }

    public YarnBlend getBlend()               { return blend; }
    public InventoryItem getFiber()           { return fiber; }
    public BigDecimal getPercentage()         { return percentage; }
    public Certification getCertification()   { return certification; }
    public Integer getDisplayOrder()          { return displayOrder; }
    public String getRemarks()                { return remarks; }
}

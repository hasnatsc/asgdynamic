package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;

/**
 * A node in the three-level item category tree: ROOT, then GROUP, then ITEM. Items may only be
 * filed under an ITEM-layer category.
 *
 * <p>The code encodes the position: a prefix, then two digits per level -
 * {@code CAF110000} (root 11), {@code CAF111100} (its group 11), {@code CAF111111} (that
 * group's first item category). The legacy system's own root codes (CAF110000 to CAF160000)
 * follow exactly this shape; see {@link ItemCategoryService#nextCode}.
 */
@Entity
@Table(
    name = "inv_item_categories",
    uniqueConstraints = @UniqueConstraint(name = "uk_inv_cat_org_code", columnNames = {"organization_id", "code"}),
    indexes = @Index(name = "ix_inv_cat_parent", columnList = "parent_id"))
public class ItemCategory extends BaseOrgEntity {

    public enum Layer {
        ROOT, GROUP, ITEM;

        /** The layer a child of this layer sits on. */
        public Layer child() {
            return switch (this) {
                case ROOT -> GROUP;
                case GROUP -> ITEM;
                case ITEM -> throw new IllegalStateException("An item category cannot have children.");
            };
        }
    }

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 20)
    private String prefix;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Layer layer;

    /** Required on ITEM-layer categories; decides which item type may be filed there. */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 30)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ItemCategory parent;

    @Column(columnDefinition = "TEXT")
    private String description;

    public ItemCategory() { }

    /** Places this category under {@code newParent} (null = root) and derives its layer. */
    public void placeUnder(ItemCategory newParent) {
        this.parent = newParent;
        this.layer = newParent == null ? Layer.ROOT : newParent.getLayer().child();
    }

    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    public String getCode()                 { return code; }
    public void setCode(String v)           { this.code = v; }
    public String getPrefix()               { return prefix; }
    public void setPrefix(String v)         { this.prefix = v; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public Layer getLayer()                 { return layer; }
    public ItemType getItemType()           { return itemType; }
    public void setItemType(ItemType v)     { this.itemType = v; }
    public ItemCategory getParent()         { return parent; }
    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }
}

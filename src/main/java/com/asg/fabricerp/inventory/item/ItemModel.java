package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

@Entity
@Table(
    name = "inv_item_models",
    uniqueConstraints = @UniqueConstraint(name = "uk_inv_model_org_code", columnNames = {"organization_id", "code"}),
    indexes = @Index(name = "ix_inv_model_brand", columnList = "brand_id"))
public class ItemModel extends ApprovableMaster {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private ItemBrand brand;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(columnDefinition = "TEXT")
    private String description;

    public ItemModel() { }

    public ItemBrand getBrand()             { return brand; }
    public void setBrand(ItemBrand v)       { this.brand = v; }
    public String getCode()                 { return code; }
    public void setCode(String v)           { this.code = v; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getShortName()            { return shortName; }
    public void setShortName(String v)      { this.shortName = v; }
    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }
}

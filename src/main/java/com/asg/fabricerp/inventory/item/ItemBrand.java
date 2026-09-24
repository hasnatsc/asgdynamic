package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

@Entity
@Table(
    name = "inv_item_brands",
    uniqueConstraints = @UniqueConstraint(name = "uk_inv_brand_org_code", columnNames = {"organization_id", "code"}))
public class ItemBrand extends ApprovableMaster {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", length = 50)
    private String shortName;

    /**
     * Free text. SpindleERP links a {@code Country} row; there is no country master here yet,
     * so the name is kept as entered.
     */
    @Column(name = "country_of_origin", length = 100)
    private String countryOfOrigin;

    @Column(columnDefinition = "TEXT")
    private String description;

    public ItemBrand() { }

    public String getCode()                    { return code; }
    public void setCode(String v)              { this.code = v; }
    public String getName()                    { return name; }
    public void setName(String v)              { this.name = v; }
    public String getShortName()               { return shortName; }
    public void setShortName(String v)         { this.shortName = v; }
    public String getCountryOfOrigin()         { return countryOfOrigin; }
    public void setCountryOfOrigin(String v)   { this.countryOfOrigin = v; }
    public String getDescription()             { return description; }
    public void setDescription(String v)       { this.description = v; }
}

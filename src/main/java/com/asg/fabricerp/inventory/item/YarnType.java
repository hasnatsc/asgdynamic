package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

/** How a yarn is spun or processed: Carded, Combed, Open-End, Slub, Compact. */
@Entity
@Table(
    name = "yrn_types",
    uniqueConstraints = @UniqueConstraint(name = "uk_yrn_type_org_code", columnNames = {"organization_id", "code"}))
public class YarnType extends ApprovableMaster {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Goes into the generated yarn item name, e.g. "CD" in "30/1 CD 100% Cotton". */
    @Column(name = "short_name", length = 30)
    private String shortName;

    @Column(columnDefinition = "TEXT")
    private String description;

    public YarnType() { }

    public String getCode()                 { return code; }
    public void setCode(String v)           { this.code = v; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getShortName()            { return shortName; }
    public void setShortName(String v)      { this.shortName = v; }
    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }
}

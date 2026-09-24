package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

/** Number of strands twisted together: 1 (Single), 2 (Double), 3 ... */
@Entity
@Table(
    name = "yrn_plies",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_yrn_ply_org_code", columnNames = {"organization_id", "code"}),
        @UniqueConstraint(name = "uk_yrn_ply_org_number", columnNames = {"organization_id", "ply_number"})
    })
public class YarnPly extends ApprovableMaster {

    @Column(name = "ply_number", nullable = false)
    private Integer plyNumber;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    public YarnPly() { }

    public String getDisplayName() {
        return plyNumber + " Ply - " + name;
    }

    public Integer getPlyNumber()           { return plyNumber; }
    public void setPlyNumber(Integer v)     { this.plyNumber = v; }
    public String getCode()                 { return code; }
    public void setCode(String v)           { this.code = v; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }
}

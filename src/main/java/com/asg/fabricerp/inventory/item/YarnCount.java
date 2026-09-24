package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

/** A yarn count, e.g. "30" or "20 English". */
@Entity
@Table(
    name = "yrn_counts",
    uniqueConstraints = @UniqueConstraint(name = "uk_yrn_count_org_code", columnNames = {"organization_id", "code"}))
public class YarnCount extends ApprovableMaster {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    public YarnCount() { }

    public String getCode()                 { return code; }
    public void setCode(String v)           { this.code = v; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }
}

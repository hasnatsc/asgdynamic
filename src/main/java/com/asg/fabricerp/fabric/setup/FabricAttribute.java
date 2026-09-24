package com.asg.fabricerp.fabric.setup;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One row of a fabric reference list. See {@link AttributeType} for why these share a table.
 */
@Entity
@Table(
    name = "fab_attributes",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_fab_attr_org_type_code",
        columnNames = {"organization_id", "attribute_type", "code"}),
    indexes = {
        @Index(name = "ix_fab_attr_lookup", columnList = "organization_id,attribute_type,active"),
        @Index(name = "ix_fab_attr_org",    columnList = "organization_id")
    })
public class FabricAttribute extends BaseOrgEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false, length = 30)
    private AttributeType attributeType;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @Size(max = 300)
    @Column(length = 300)
    private String description;

    /** Controls order in dropdowns; ties break on name. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    protected FabricAttribute() { }

    public FabricAttribute(AttributeType attributeType, String code, String name) {
        this.attributeType = attributeType;
        this.code = code;
        this.name = name;
    }

    public AttributeType getAttributeType()        { return attributeType; }
    public void setAttributeType(AttributeType v)  { this.attributeType = v; }
    public String getCode()                        { return code; }
    public void setCode(String v)                  { this.code = v; }
    public String getName()                        { return name; }
    public void setName(String v)                  { this.name = v; }
    public String getDescription()                 { return description; }
    public void setDescription(String v)           { this.description = v; }
    public Integer getDisplayOrder()               { return displayOrder; }
    public void setDisplayOrder(Integer v)         { this.displayOrder = v == null ? 0 : v; }
}

package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The store a {@link com.asg.fabricerp.security.FabricUser} defaults into
 * ({@code FabricUser.warehouseId}, optional — not every user has one). {@code businessUnitId}
 * here is a plain scalar, same reasoning as {@link OrgScoped} — a warehouse's business unit is
 * a filter predicate, not a navigation.
 */
@Entity
@Table(
    name = "org_warehouses",
    uniqueConstraints = @UniqueConstraint(name = "uk_warehouse_org_code", columnNames = {"organization_id", "code"}))
public class Warehouse extends BaseOrgEntity {

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "business_unit_id")
    private Long businessUnitId;

    protected Warehouse() { }

    public Warehouse(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode()                   { return code; }
    public void setCode(String v)             { this.code = v; }
    public String getName()                   { return name; }
    public void setName(String v)             { this.name = v; }
    public Long getBusinessUnitId()           { return businessUnitId; }
    public void setBusinessUnitId(Long v)     { this.businessUnitId = v; }
}

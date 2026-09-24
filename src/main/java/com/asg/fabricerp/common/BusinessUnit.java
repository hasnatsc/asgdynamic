package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The unit a {@link com.asg.fabricerp.security.FabricUser} defaults into — the same
 * two-to-four-letter code embedded in every document number (see
 * {@code FabricUser.businessUnitCode}'s javadoc). {@code FabricUser} still carries
 * {@code businessUnitId}/{@code businessUnitCode} as plain scalars, not a relation to this
 * table — same reasoning as {@link OrgScoped}, applied one level down. This entity exists so
 * that id resolves to a real, manageable record.
 */
@Entity
@Table(
    name = "org_business_units",
    uniqueConstraints = @UniqueConstraint(name = "uk_bu_org_code", columnNames = {"organization_id", "code"}))
public class BusinessUnit extends BaseOrgEntity {

    @NotBlank
    @Size(max = 4)
    @Column(nullable = false, length = 4)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    protected BusinessUnit() { }

    public BusinessUnit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode()       { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName()       { return name; }
    public void setName(String v) { this.name = v; }
}

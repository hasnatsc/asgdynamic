package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** A customs tariff heading (e.g. 5208.11.00) with the duty rates that apply to it. */
@Entity
@Table(
    name = "inv_hs_codes",
    uniqueConstraints = @UniqueConstraint(name = "uk_inv_hs_org_code", columnNames = {"organization_id", "hs_code"}))
public class HsCode extends BaseOrgEntity {

    public enum HsType { EXPORT, IMPORT, BOTH }

    @Column(name = "hs_code", nullable = false, length = 20)
    private String hsCode;

    /** Optional: the legacy system carried the codes alone, with no descriptions. */
    @Column(length = 500)
    private String description;

    @Column(name = "short_description", length = 200)
    private String shortDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "hs_type", nullable = false, length = 20)
    private HsType hsType = HsType.BOTH;

    @Column(name = "customs_duty_percent", precision = 6, scale = 2)
    private BigDecimal customsDutyPercent;

    @Column(name = "vat_percent", precision = 6, scale = 2)
    private BigDecimal vatPercent;

    @Column(name = "supplementary_duty_percent", precision = 6, scale = 2)
    private BigDecimal supplementaryDutyPercent;

    /** Advance Income Tax. */
    @Column(name = "ait_percent", precision = 6, scale = 2)
    private BigDecimal aitPercent;

    @Column(name = "bonded_allowed", nullable = false)
    private Boolean bondedAllowed = Boolean.TRUE;

    @Column(name = "requires_export_permit", nullable = false)
    private Boolean requiresExportPermit = Boolean.FALSE;

    @Column(name = "requires_import_permit", nullable = false)
    private Boolean requiresImportPermit = Boolean.FALSE;

    public HsCode() { }

    public String getHsCode()                              { return hsCode; }
    public void setHsCode(String v)                        { this.hsCode = v; }
    public String getDescription()                         { return description; }
    public void setDescription(String v)                   { this.description = v; }
    public String getShortDescription()                    { return shortDescription; }
    public void setShortDescription(String v)              { this.shortDescription = v; }
    public HsType getHsType()                              { return hsType; }
    public void setHsType(HsType v)                        { this.hsType = v; }
    public BigDecimal getCustomsDutyPercent()              { return customsDutyPercent; }
    public void setCustomsDutyPercent(BigDecimal v)        { this.customsDutyPercent = v; }
    public BigDecimal getVatPercent()                      { return vatPercent; }
    public void setVatPercent(BigDecimal v)                { this.vatPercent = v; }
    public BigDecimal getSupplementaryDutyPercent()        { return supplementaryDutyPercent; }
    public void setSupplementaryDutyPercent(BigDecimal v)  { this.supplementaryDutyPercent = v; }
    public BigDecimal getAitPercent()                      { return aitPercent; }
    public void setAitPercent(BigDecimal v)                { this.aitPercent = v; }
    public Boolean getBondedAllowed()                      { return bondedAllowed; }
    public void setBondedAllowed(Boolean v)                { this.bondedAllowed = !Boolean.FALSE.equals(v); }
    public Boolean getRequiresExportPermit()               { return requiresExportPermit; }
    public void setRequiresExportPermit(Boolean v)         { this.requiresExportPermit = Boolean.TRUE.equals(v); }
    public Boolean getRequiresImportPermit()               { return requiresImportPermit; }
    public void setRequiresImportPermit(Boolean v)         { this.requiresImportPermit = Boolean.TRUE.equals(v); }
}

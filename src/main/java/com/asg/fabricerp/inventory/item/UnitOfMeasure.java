package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A unit an item is counted, weighed or measured in. {@code conversionFactor} is relative to
 * the base unit of the same {@link UomCategory}: Kilogram (base) 1, Gram 0.001, Pound 0.45359237.
 */
@Entity
@Table(
    name = "inv_uoms",
    uniqueConstraints = @UniqueConstraint(name = "uk_inv_uom_org_code", columnNames = {"organization_id", "code"}),
    indexes = @Index(name = "ix_inv_uom_lookup", columnList = "organization_id,category,active"))
public class UnitOfMeasure extends BaseOrgEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UomCategory category;

    @Column(name = "base_unit", nullable = false)
    private Boolean baseUnit = Boolean.FALSE;

    @Column(name = "conversion_factor", nullable = false, precision = 18, scale = 10)
    private BigDecimal conversionFactor = BigDecimal.ONE;

    public UnitOfMeasure() { }

    /** Quantity in this unit to quantity in the category's base unit. */
    public BigDecimal toBase(BigDecimal quantity) {
        return quantity == null ? BigDecimal.ZERO : quantity.multiply(conversionFactor);
    }

    /** Quantity in the category's base unit to quantity in this unit. */
    public BigDecimal fromBase(BigDecimal baseQuantity) {
        if (baseQuantity == null) return BigDecimal.ZERO;
        return baseQuantity.divide(conversionFactor, 6, RoundingMode.HALF_UP);
    }

    public String getCode()                        { return code; }
    public void setCode(String v)                  { this.code = v; }
    public String getName()                        { return name; }
    public void setName(String v)                  { this.name = v; }
    public String getSymbol()                      { return symbol; }
    public void setSymbol(String v)                { this.symbol = v; }
    public UomCategory getCategory()               { return category; }
    public void setCategory(UomCategory v)         { this.category = v; }
    public Boolean getBaseUnit()                   { return baseUnit; }
    public void setBaseUnit(Boolean v)             { this.baseUnit = Boolean.TRUE.equals(v); }
    public BigDecimal getConversionFactor()        { return conversionFactor; }
    public void setConversionFactor(BigDecimal v)  { this.conversionFactor = v; }
}

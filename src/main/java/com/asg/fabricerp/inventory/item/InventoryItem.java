package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * The item master: anything bought, stocked, produced or sold.
 *
 * <h2>What changed from SpindleERP</h2>
 * <ul>
 *   <li><b>Yarn attributes live on the item.</b> SpindleERP splits them into a one-to-one
 *       {@code yarn_items} table and keeps it in step by hand on every save, including deleting
 *       it when the type changes. They are four nullable columns here, and the
 *       {@code ck_inv_item_yarn_spec} constraint makes "YARN if and only if all four are set"
 *       a database fact.</li>
 *   <li><b>No snapshot columns.</b> {@code base_unit_code}/{@code base_unit_name},
 *       {@code blend_name} and {@code yarn_display_name} copied data the item already
 *       references, and went stale when the source was edited. For a yarn the generated
 *       display name <i>is</i> the item name.</li>
 *   <li><b>Origin is text.</b> There is no country master here yet.</li>
 * </ul>
 */
@Entity
@Table(
    name = "inv_items",
    uniqueConstraints = @UniqueConstraint(name = "uk_inv_item_org_code", columnNames = {"organization_id", "item_code"}),
    indexes = {
        @Index(name = "ix_inv_item_lookup",   columnList = "organization_id,item_type,active"),
        @Index(name = "ix_inv_item_category", columnList = "category_id"),
        @Index(name = "ix_inv_item_uom",      columnList = "base_uom_id"),
        @Index(name = "ix_inv_item_hs",       columnList = "hs_code_id"),
        @Index(name = "ix_inv_item_brand",    columnList = "brand_id"),
        @Index(name = "ix_inv_item_model",    columnList = "model_id"),
        @Index(name = "ix_inv_item_yarn_type",  columnList = "yarn_type_id"),
        @Index(name = "ix_inv_item_yarn_count", columnList = "yarn_count_id"),
        @Index(name = "ix_inv_item_yarn_ply",   columnList = "yarn_ply_id"),
        @Index(name = "ix_inv_item_yarn_blend", columnList = "yarn_blend_id")
    })
public class InventoryItem extends ApprovableMaster {

    // ---- Core -------------------------------------------------------------------------------

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 200)
    private String name;

    @Column(name = "item_name_bn", length = 200)
    private String nameBn;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private ItemType itemType = ItemType.GENERAL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ItemCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_uom_id", nullable = false)
    private UnitOfMeasure baseUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hs_code_id")
    private HsCode hsCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private ItemBrand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private ItemModel model;

    @Column(length = 100)
    private String barcode;

    @Column(length = 100)
    private String sku;

    // ---- Stock control & pricing -------------------------------------------------------------

    @Column(name = "reorder_level", precision = 12, scale = 3)
    private BigDecimal reorderLevel;

    @Column(name = "minimum_stock", precision = 12, scale = 3)
    private BigDecimal minimumStock;

    @Column(name = "maximum_stock", precision = 12, scale = 3)
    private BigDecimal maximumStock;

    @Column(name = "unit_price", precision = 12, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", precision = 12, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    // ---- Fiber (FIBER) -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "fiber_type", length = 30)
    private FiberType fiberType;

    @Column(name = "origin_name", length = 100)
    private String originName;

    @Column(length = 50)
    private String grade;

    /** mm */
    @Column(name = "staple_length", precision = 8, scale = 2)
    private BigDecimal stapleLength;

    @Column(precision = 8, scale = 2)
    private BigDecimal micronaire;

    /** g/tex */
    @Column(precision = 8, scale = 2)
    private BigDecimal strength;

    /** % */
    @Column(precision = 8, scale = 2)
    private BigDecimal moisture;

    /** % */
    @Column(precision = 5, scale = 2)
    private BigDecimal trash;

    /** % */
    @Column(precision = 5, scale = 2)
    private BigDecimal purity;

    // ---- Yarn (YARN) ---------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yarn_type_id")
    private YarnType yarnType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yarn_count_id")
    private YarnCount yarnCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yarn_ply_id")
    private YarnPly yarnPly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yarn_blend_id")
    private YarnBlend yarnBlend;

    @Column(name = "quality_grade", length = 100)
    private String qualityGrade;

    // ---- Dyes & chemicals (CHEMICALS) ----------------------------------------------------------

    @Column(name = "chemical_formula", length = 50)
    private String chemicalFormula;

    /** Chemical Abstracts Service registry number. */
    @Column(name = "cas_number", length = 50)
    private String casNumber;

    @Column(nullable = false)
    private Boolean hazardous = Boolean.FALSE;

    @Column(name = "safety_data_sheet", length = 100)
    private String safetyDataSheet;

    /** % */
    @Column(precision = 8, scale = 2)
    private BigDecimal concentration;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // ---- Fixed asset (FIXED_ASSET) -------------------------------------------------------------

    @Column(length = 100)
    private String manufacturer;

    @Column(name = "model_number", length = 100)
    private String modelNumber;

    @Column(name = "serial_number", length = 50)
    private String serialNumber;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "asset_value", precision = 15, scale = 2)
    private BigDecimal assetValue;

    @Column(name = "depreciation_rate", precision = 5, scale = 2)
    private BigDecimal depreciationRate;

    // ---- Production & costing ------------------------------------------------------------------

    @Column(name = "process_loss_percent", precision = 5, scale = 2)
    private BigDecimal processLossPercent;

    @Column(name = "yield_percent", precision = 5, scale = 2)
    private BigDecimal yieldPercent;

    @Column(name = "standard_cost_per_kg", precision = 12, scale = 2)
    private BigDecimal standardCostPerKg;

    @Column(name = "selling_price_per_kg", precision = 12, scale = 2)
    private BigDecimal sellingPricePerKg;

    public InventoryItem() { }

    // ---- Behaviour -----------------------------------------------------------------------------

    public boolean isYarn() {
        return itemType == ItemType.YARN;
    }

    /** Sets or clears all four yarn attributes together - the constraint allows nothing else. */
    public void specifyYarn(YarnType type, YarnCount count, YarnPly ply, YarnBlend blend) {
        this.yarnType = type;
        this.yarnCount = count;
        this.yarnPly = ply;
        this.yarnBlend = blend;
    }

    public void clearYarn() {
        specifyYarn(null, null, null, null);
        this.qualityGrade = null;
    }

    /**
     * The generated yarn name: count/ply, type short name, blend - "30/1 CD 60% Cotton 40% Viscose".
     * Uses the blend's own name, falling back to its composition only when it has none.
     */
    public String yarnDisplayName() {
        StringBuilder name = new StringBuilder();
        String count = yarnCount == null ? null : yarnCount.getName();
        Integer ply = yarnPly == null ? null : yarnPly.getPlyNumber();
        if (count != null && ply != null) {
            name.append(count).append('/').append(ply);
        } else if (count != null) {
            name.append(count);
        } else if (ply != null) {
            name.append(ply);
        }
        appendWord(name, yarnType == null ? null : yarnType.getShortName());
        if (yarnBlend != null) {
            String blend = yarnBlend.getName();
            appendWord(name, blend == null || blend.isBlank() ? yarnBlend.composition() : blend);
        }
        return name.toString().trim();
    }

    private static void appendWord(StringBuilder into, String word) {
        if (word == null || word.isBlank()) return;
        if (!into.isEmpty()) into.append(' ');
        into.append(word.trim());
    }

    public BigDecimal sellingPriceWithTax() {
        if (unitPrice == null) return BigDecimal.ZERO;
        if (taxRate == null || taxRate.signum() == 0) return unitPrice;
        return unitPrice.add(unitPrice.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    // ---- Accessors -----------------------------------------------------------------------------

    public String getItemCode()                        { return itemCode; }
    public void setItemCode(String v)                  { this.itemCode = v; }
    public String getName()                            { return name; }
    public void setName(String v)                      { this.name = v; }
    public String getNameBn()                          { return nameBn; }
    public void setNameBn(String v)                    { this.nameBn = v; }
    public String getDescription()                     { return description; }
    public void setDescription(String v)               { this.description = v; }
    public ItemType getItemType()                      { return itemType; }
    public void setItemType(ItemType v)                { this.itemType = v; }
    public ItemCategory getCategory()                  { return category; }
    public void setCategory(ItemCategory v)            { this.category = v; }
    public UnitOfMeasure getBaseUnit()                 { return baseUnit; }
    public void setBaseUnit(UnitOfMeasure v)           { this.baseUnit = v; }
    public HsCode getHsCode()                          { return hsCode; }
    public void setHsCode(HsCode v)                    { this.hsCode = v; }
    public ItemBrand getBrand()                        { return brand; }
    public void setBrand(ItemBrand v)                  { this.brand = v; }
    public ItemModel getModel()                        { return model; }
    public void setModel(ItemModel v)                  { this.model = v; }
    public String getBarcode()                         { return barcode; }
    public void setBarcode(String v)                   { this.barcode = v; }
    public String getSku()                             { return sku; }
    public void setSku(String v)                       { this.sku = v; }

    public BigDecimal getReorderLevel()                { return reorderLevel; }
    public void setReorderLevel(BigDecimal v)          { this.reorderLevel = v; }
    public BigDecimal getMinimumStock()                { return minimumStock; }
    public void setMinimumStock(BigDecimal v)          { this.minimumStock = v; }
    public BigDecimal getMaximumStock()                { return maximumStock; }
    public void setMaximumStock(BigDecimal v)          { this.maximumStock = v; }
    public BigDecimal getUnitPrice()                   { return unitPrice; }
    public void setUnitPrice(BigDecimal v)             { this.unitPrice = v; }
    public BigDecimal getCostPrice()                   { return costPrice; }
    public void setCostPrice(BigDecimal v)             { this.costPrice = v; }
    public BigDecimal getTaxRate()                     { return taxRate; }
    public void setTaxRate(BigDecimal v)               { this.taxRate = v; }

    public FiberType getFiberType()                    { return fiberType; }
    public void setFiberType(FiberType v)              { this.fiberType = v; }
    public String getOriginName()                      { return originName; }
    public void setOriginName(String v)                { this.originName = v; }
    public String getGrade()                           { return grade; }
    public void setGrade(String v)                     { this.grade = v; }
    public BigDecimal getStapleLength()                { return stapleLength; }
    public void setStapleLength(BigDecimal v)          { this.stapleLength = v; }
    public BigDecimal getMicronaire()                  { return micronaire; }
    public void setMicronaire(BigDecimal v)            { this.micronaire = v; }
    public BigDecimal getStrength()                    { return strength; }
    public void setStrength(BigDecimal v)              { this.strength = v; }
    public BigDecimal getMoisture()                    { return moisture; }
    public void setMoisture(BigDecimal v)              { this.moisture = v; }
    public BigDecimal getTrash()                       { return trash; }
    public void setTrash(BigDecimal v)                 { this.trash = v; }
    public BigDecimal getPurity()                      { return purity; }
    public void setPurity(BigDecimal v)                { this.purity = v; }

    public YarnType getYarnType()                      { return yarnType; }
    public YarnCount getYarnCount()                    { return yarnCount; }
    public YarnPly getYarnPly()                        { return yarnPly; }
    public YarnBlend getYarnBlend()                    { return yarnBlend; }
    public String getQualityGrade()                    { return qualityGrade; }
    public void setQualityGrade(String v)              { this.qualityGrade = v; }

    public String getChemicalFormula()                 { return chemicalFormula; }
    public void setChemicalFormula(String v)           { this.chemicalFormula = v; }
    public String getCasNumber()                       { return casNumber; }
    public void setCasNumber(String v)                 { this.casNumber = v; }
    public Boolean getHazardous()                      { return hazardous; }
    public void setHazardous(Boolean v)                { this.hazardous = Boolean.TRUE.equals(v); }
    public String getSafetyDataSheet()                 { return safetyDataSheet; }
    public void setSafetyDataSheet(String v)           { this.safetyDataSheet = v; }
    public BigDecimal getConcentration()               { return concentration; }
    public void setConcentration(BigDecimal v)         { this.concentration = v; }
    public LocalDate getExpiryDate()                   { return expiryDate; }
    public void setExpiryDate(LocalDate v)             { this.expiryDate = v; }

    public String getManufacturer()                    { return manufacturer; }
    public void setManufacturer(String v)              { this.manufacturer = v; }
    public String getModelNumber()                     { return modelNumber; }
    public void setModelNumber(String v)               { this.modelNumber = v; }
    public String getSerialNumber()                    { return serialNumber; }
    public void setSerialNumber(String v)              { this.serialNumber = v; }
    public Integer getWarrantyMonths()                 { return warrantyMonths; }
    public void setWarrantyMonths(Integer v)           { this.warrantyMonths = v; }
    public BigDecimal getAssetValue()                  { return assetValue; }
    public void setAssetValue(BigDecimal v)            { this.assetValue = v; }
    public BigDecimal getDepreciationRate()            { return depreciationRate; }
    public void setDepreciationRate(BigDecimal v)      { this.depreciationRate = v; }

    public BigDecimal getProcessLossPercent()          { return processLossPercent; }
    public void setProcessLossPercent(BigDecimal v)    { this.processLossPercent = v; }
    public BigDecimal getYieldPercent()                { return yieldPercent; }
    public void setYieldPercent(BigDecimal v)          { this.yieldPercent = v; }
    public BigDecimal getStandardCostPerKg()           { return standardCostPerKg; }
    public void setStandardCostPerKg(BigDecimal v)     { this.standardCostPerKg = v; }
    public BigDecimal getSellingPricePerKg()           { return sellingPricePerKg; }
    public void setSellingPricePerKg(BigDecimal v)     { this.sellingPricePerKg = v; }
}

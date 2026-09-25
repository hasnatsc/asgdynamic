package com.asg.fabricerp.global.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * The fabric identity of a {@link BusinessDocumentLineGroup} — one construction, entered
 * once, shared by every colour drawn against it.
 *
 * <h2>Corrected after a real production Booking payload showed the shape was wrong</h2>
 * An earlier version of this class was embedded directly on the document line and carried
 * {@code colourCode}/{@code colourName}, on the reasoning that asgdynamic's {@code dtlSet}
 * level only existed to avoid repeating fabric attributes per colour, and an embeddable
 * removes that need without a second table.
 *
 * <p>That was wrong, and a real API response settled it: one Booking line group
 * ("20X20/69X56, 70% Viscose 30% Linen") legitimately carried <b>six</b> colours, each with
 * its own {@code lab_dip_reference} ({@code "25-08A-2279 OPT-C"} for White,
 * {@code "25-08A-2221 OPT-F"} for Black — completely different approvals), its own
 * {@code fabrics_style}, and its own price. Embedding fabric attributes on the colour row
 * would mean re-entering construction, weave, composition and every count/shrinkage/GSM
 * field six times for one order — exactly the redundant data entry the legacy two-level
 * {@code dtlSet}/{@code dtlLine} structure existed to avoid, which "an embeddable removes
 * the need to avoid" turned out to be true only if nobody re-types the form. This class is
 * now the shared group; {@link BusinessDocumentColorLine} carries colour, quantity, price
 * and the reference fields the real data showed are genuinely per-colour.
 */
@Embeddable
public class FabricSpec {

    /** e.g. "40X30/108X62" — warp count X weft count / EPI X PPI. */
    @Column(name = "construction", length = 60)
    private String construction;

    /** What the buyer is told, when it differs from the actual construction. */
    @Column(name = "declared_construction", length = 60)
    private String declaredConstruction;

    @Column(name = "weave_type", length = 60)
    private String weaveType;          // 1/1, 2/1 S Twill, 2/2 Matt ...

    @Column(name = "weave_style", length = 60)
    private String weaveStyle;         // Broken Twill, Cavalry Twill, HBT ...

    @Column(name = "fabric_type", length = 60)
    private String fabricType;         // Greige Solid Dyed, Greige Indigo Denim ...

    /** Multi-select on the legacy form: several finish names, comma-separated. */
    @Column(name = "finish_type", length = 300)
    private String finishType;         // Aero Finish, Both Side Peach, Brush ...

    @Column(name = "composition", length = 120)
    private String composition;        // 100% Cotton, 70% Viscose 30% Linen ...

    @Column(name = "declared_composition", length = 120)
    private String declaredComposition;

    // --- yarn spec: confirmed present on the real Booking payload, previously missing ---
    @Column(name = "warp_count_1", length = 20) private String warpCount1;
    @Column(name = "warp_count_2", length = 20) private String warpCount2;
    @Column(name = "warp_count_3", length = 20) private String warpCount3;
    @Column(name = "warp_count_ratio_1", precision = 8, scale = 3) private BigDecimal warpCountRatio1;
    @Column(name = "warp_count_ratio_2", precision = 8, scale = 3) private BigDecimal warpCountRatio2;
    @Column(name = "warp_count_ratio_3", precision = 8, scale = 3) private BigDecimal warpCountRatio3;
    @Column(name = "weft_count_1", length = 20) private String weftCount1;
    @Column(name = "weft_count_2", length = 20) private String weftCount2;
    @Column(name = "weft_count_3", length = 20) private String weftCount3;
    @Column(name = "weft_count_ratio_1", precision = 8, scale = 3) private BigDecimal weftCountRatio1;
    @Column(name = "weft_count_ratio_2", precision = 8, scale = 3) private BigDecimal weftCountRatio2;
    @Column(name = "weft_count_ratio_3", precision = 8, scale = 3) private BigDecimal weftCountRatio3;

    /** Ends per inch / picks per inch — construction density, paired with {@link #construction}. */
    @Column(name = "epi", precision = 10, scale = 2) private BigDecimal epi;
    @Column(name = "ppi", precision = 10, scale = 2) private BigDecimal ppi;

    /** Shrinkage is recorded as a range on the real form ("3-4", "8-10"), not a single number. */
    @Column(name = "shrinkage_warp", length = 20) private String shrinkageWarp;
    @Column(name = "shrinkage_weft", length = 20) private String shrinkageWeft;
    @Column(name = "shrinkage_mechanical", length = 20) private String shrinkageMechanical;

    /** "Calculated GSM" on the real form — derived, distinct from before/after-wash readings. */
    @Column(name = "gsm", precision = 12, scale = 4)
    private BigDecimal gsm;

    @Column(name = "gsm_before_wash", precision = 12, scale = 4) private BigDecimal gsmBeforeWash;
    @Column(name = "gsm_after_wash", precision = 12, scale = 4) private BigDecimal gsmAfterWash;

    @Column(name = "finish_width", precision = 12, scale = 4)
    private BigDecimal finishWidth;

    @Column(name = "cuttable_width", precision = 12, scale = 4)
    private BigDecimal cuttableWidth;

    /** Shade-matching light source: CWF, D65, TL83, Filament ... */
    @Column(name = "light_source", length = 30)
    private String lightSource;

    /** e.g. "10 mm + 10 mm". */
    @Column(name = "selvedge", length = 40)
    private String selvedge;

    @Column(name = "wash_type", length = 60) private String washType;
    @Column(name = "wash_instruction", length = 200) private String washInstruction;
    @Column(name = "end_use", length = 60) private String endUse;

    /** Planning-system cross-reference, confirmed as a real per-spec field (not a stray
     *  screen artifact as an earlier pass through {@code RequestForPiService} assumed). */
    @Column(name = "dispo_reference", length = 60) private String dispoReference;

    /**
     * Link to the external costing system. When present the server refreshes {@code gsm},
     * construction and cost from it on save rather than trusting the client.
     */
    @Column(name = "costing_code", length = 40)
    private String costingCode;

    // --- the rest of the legacy Booking spec form (create_so_dtlSet_*) ---

    /** Costing amendment the figures came from; stamped from the costing system on save. */
    @Column(name = "costing_amendment_no", length = 10) private String costingAmendmentNo;

    /** In-house or Export: where the fabric is made. The unlabelled select beside Fabrics Type. */
    @Column(name = "fabric_source", length = 20) private String fabricSource;

    @Column(name = "finish_type_ref", length = 500) private String finishTypeRef;
    @Column(name = "quality_reference", length = 120) private String qualityReference;
    /** Legacy "Style" ({@code designReference}). */
    @Column(name = "style_reference", length = 120) private String styleReference;

    /**
     * "Quot Price Costing": the price per yard the costing system quoted. Snapshotted from the
     * costing on save, never taken from the request - it is the benchmark a booking's own price
     * is judged against.
     */
    @Column(name = "quoted_price", precision = 20, scale = 6) private BigDecimal quotedPrice;

    /** Break-even per yard at the time of booking. Same provenance as {@link #quotedPrice}. */
    @Column(name = "break_even_price", precision = 20, scale = 6) private BigDecimal breakEvenPrice;

    @Column(name = "warp_yarn_name", length = 120) private String warpYarnName;
    @Column(name = "weft_yarn_name", length = 120) private String weftYarnName;

    /** Primary or Secondary - pairs with {@link #lightSource}. */
    @Column(name = "light_source_type", length = 20) private String lightSourceType;
    /** "As per Swatch" / "As per Specification" (legacy {@code sampleType}). */
    @Column(name = "base_material", length = 40) private String baseMaterial;
    @Column(name = "swatch_no", length = 60) private String swatchNo;

    @Column(name = "lc_tenure", length = 30) private String lcTenure;
    @Column(name = "lc_payment_type", length = 30) private String lcPaymentType;
    @Column(name = "lead_time_days") private Integer leadTimeDays;
    @Column(name = "target_quality_parameter", length = 200) private String targetQualityParameter;
    @Column(name = "item_description", length = 1000) private String itemDescription;

    public String getConstruction()               { return construction; }
    public void setConstruction(String v)         { this.construction = v; }
    public String getDeclaredConstruction()       { return declaredConstruction; }
    public void setDeclaredConstruction(String v) { this.declaredConstruction = v; }
    public String getWeaveType()                  { return weaveType; }
    public void setWeaveType(String v)            { this.weaveType = v; }
    public String getWeaveStyle()                 { return weaveStyle; }
    public void setWeaveStyle(String v)           { this.weaveStyle = v; }
    public String getFabricType()                 { return fabricType; }
    public void setFabricType(String v)           { this.fabricType = v; }
    public String getFinishType()                 { return finishType; }
    public void setFinishType(String v)           { this.finishType = v; }
    public String getComposition()                { return composition; }
    public void setComposition(String v)          { this.composition = v; }
    public String getDeclaredComposition()        { return declaredComposition; }
    public void setDeclaredComposition(String v)  { this.declaredComposition = v; }
    public String getWarpCount1()                 { return warpCount1; }
    public void setWarpCount1(String v)           { this.warpCount1 = v; }
    public String getWarpCount2()                 { return warpCount2; }
    public void setWarpCount2(String v)           { this.warpCount2 = v; }
    public String getWarpCount3()                 { return warpCount3; }
    public void setWarpCount3(String v)           { this.warpCount3 = v; }
    public BigDecimal getWarpCountRatio1()        { return warpCountRatio1; }
    public void setWarpCountRatio1(BigDecimal v)  { this.warpCountRatio1 = v; }
    public BigDecimal getWarpCountRatio2()        { return warpCountRatio2; }
    public void setWarpCountRatio2(BigDecimal v)  { this.warpCountRatio2 = v; }
    public BigDecimal getWarpCountRatio3()        { return warpCountRatio3; }
    public void setWarpCountRatio3(BigDecimal v)  { this.warpCountRatio3 = v; }
    public String getWeftCount1()                 { return weftCount1; }
    public void setWeftCount1(String v)           { this.weftCount1 = v; }
    public String getWeftCount2()                 { return weftCount2; }
    public void setWeftCount2(String v)           { this.weftCount2 = v; }
    public String getWeftCount3()                 { return weftCount3; }
    public void setWeftCount3(String v)           { this.weftCount3 = v; }
    public BigDecimal getWeftCountRatio1()        { return weftCountRatio1; }
    public void setWeftCountRatio1(BigDecimal v)  { this.weftCountRatio1 = v; }
    public BigDecimal getWeftCountRatio2()        { return weftCountRatio2; }
    public void setWeftCountRatio2(BigDecimal v)  { this.weftCountRatio2 = v; }
    public BigDecimal getWeftCountRatio3()        { return weftCountRatio3; }
    public void setWeftCountRatio3(BigDecimal v)  { this.weftCountRatio3 = v; }
    public BigDecimal getEpi()                    { return epi; }
    public void setEpi(BigDecimal v)              { this.epi = v; }
    public BigDecimal getPpi()                    { return ppi; }
    public void setPpi(BigDecimal v)              { this.ppi = v; }
    public String getShrinkageWarp()              { return shrinkageWarp; }
    public void setShrinkageWarp(String v)        { this.shrinkageWarp = v; }
    public String getShrinkageWeft()              { return shrinkageWeft; }
    public void setShrinkageWeft(String v)        { this.shrinkageWeft = v; }
    public String getShrinkageMechanical()        { return shrinkageMechanical; }
    public void setShrinkageMechanical(String v)  { this.shrinkageMechanical = v; }
    public BigDecimal getGsm()                    { return gsm; }
    public void setGsm(BigDecimal v)              { this.gsm = v; }
    public BigDecimal getGsmBeforeWash()          { return gsmBeforeWash; }
    public void setGsmBeforeWash(BigDecimal v)    { this.gsmBeforeWash = v; }
    public BigDecimal getGsmAfterWash()           { return gsmAfterWash; }
    public void setGsmAfterWash(BigDecimal v)     { this.gsmAfterWash = v; }
    public BigDecimal getFinishWidth()            { return finishWidth; }
    public void setFinishWidth(BigDecimal v)      { this.finishWidth = v; }
    public BigDecimal getCuttableWidth()          { return cuttableWidth; }
    public void setCuttableWidth(BigDecimal v)    { this.cuttableWidth = v; }
    public String getLightSource()                { return lightSource; }
    public void setLightSource(String v)          { this.lightSource = v; }
    public String getSelvedge()                   { return selvedge; }
    public void setSelvedge(String v)             { this.selvedge = v; }
    public String getWashType()                   { return washType; }
    public void setWashType(String v)             { this.washType = v; }
    public String getWashInstruction()            { return washInstruction; }
    public void setWashInstruction(String v)      { this.washInstruction = v; }
    public String getEndUse()                     { return endUse; }
    public void setEndUse(String v)               { this.endUse = v; }
    public String getDispoReference()             { return dispoReference; }
    public void setDispoReference(String v)       { this.dispoReference = v; }
    public String getCostingCode()                { return costingCode; }
    public void setCostingCode(String v)          { this.costingCode = v == null ? null : v.trim(); }
    public String getCostingAmendmentNo()         { return costingAmendmentNo; }
    public void setCostingAmendmentNo(String v)   { this.costingAmendmentNo = v; }
    public String getFabricSource()               { return fabricSource; }
    public void setFabricSource(String v)         { this.fabricSource = v; }
    public String getFinishTypeRef()              { return finishTypeRef; }
    public void setFinishTypeRef(String v)        { this.finishTypeRef = v; }
    public String getQualityReference()           { return qualityReference; }
    public void setQualityReference(String v)     { this.qualityReference = v; }
    public String getStyleReference()             { return styleReference; }
    public void setStyleReference(String v)       { this.styleReference = v; }
    public BigDecimal getQuotedPrice()            { return quotedPrice; }
    public void setQuotedPrice(BigDecimal v)      { this.quotedPrice = v; }
    public BigDecimal getBreakEvenPrice()         { return breakEvenPrice; }
    public void setBreakEvenPrice(BigDecimal v)   { this.breakEvenPrice = v; }
    public String getWarpYarnName()               { return warpYarnName; }
    public void setWarpYarnName(String v)         { this.warpYarnName = v; }
    public String getWeftYarnName()               { return weftYarnName; }
    public void setWeftYarnName(String v)         { this.weftYarnName = v; }
    public String getLightSourceType()            { return lightSourceType; }
    public void setLightSourceType(String v)      { this.lightSourceType = v; }
    public String getBaseMaterial()               { return baseMaterial; }
    public void setBaseMaterial(String v)         { this.baseMaterial = v; }
    public String getSwatchNo()                   { return swatchNo; }
    public void setSwatchNo(String v)             { this.swatchNo = v; }
    public String getLcTenure()                   { return lcTenure; }
    public void setLcTenure(String v)             { this.lcTenure = v; }
    public String getLcPaymentType()              { return lcPaymentType; }
    public void setLcPaymentType(String v)        { this.lcPaymentType = v; }
    public Integer getLeadTimeDays()              { return leadTimeDays; }
    public void setLeadTimeDays(Integer v)        { this.leadTimeDays = v; }
    public String getTargetQualityParameter()     { return targetQualityParameter; }
    public void setTargetQualityParameter(String v) { this.targetQualityParameter = v; }
    public String getItemDescription()            { return itemDescription; }
    public void setItemDescription(String v)      { this.itemDescription = v; }

    public boolean hasCostingCode() {
        return costingCode != null && !costingCode.isBlank();
    }
}

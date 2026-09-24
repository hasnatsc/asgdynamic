package com.asg.fabricerp.global.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * The fabric identity of a document line — asgdynamic's core business logic, normalised.
 *
 * <h2>Why this is embedded rather than a detail-set table</h2>
 * asgdynamic modelled fabric attributes as a middle level ({@code so_dtlSet}) between the
 * document and its colour/quantity lines, then repeated those same columns on every screen
 * under entity-prefixed names — {@code so_dtlSet_weaveType}, {@code po_dtlSet_weaveType},
 * {@code grm_dtlSet_countries} and so on. The attributes are not a separate thing; they are
 * what the line <i>is</i>. Embedding them removes a join, a table per document type, and the
 * duplicated markup that came with it.
 *
 * <h2>Why SpindleERP has no equivalent</h2>
 * A spinning mill identifies its product with count, ply and blend, which live on the yarn
 * item itself. Woven fabric is identified per order line by construction, GSM and finish —
 * the same yarn becomes a different product depending on how it is woven and processed.
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

    @Column(name = "finish_type", length = 60)
    private String finishType;         // Aero Finish, Both Side Peach, Brush ...

    @Column(name = "composition", length = 120)
    private String composition;        // 100% Cotton, 70% Viscose 30% Linen ...

    @Column(name = "gsm", precision = 12, scale = 4)
    private BigDecimal gsm;

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

    @Column(name = "colour_code", length = 40)
    private String colourCode;

    @Column(name = "colour_name", length = 120)
    private String colourName;

    /**
     * Link to the external costing system. When present the server refreshes
     * {@code gsm}, construction and cost from it on save rather than trusting the client.
     */
    @Column(name = "costing_code", length = 40)
    private String costingCode;

    public String getConstruction()             { return construction; }
    public void setConstruction(String v)       { this.construction = v; }
    public String getDeclaredConstruction()     { return declaredConstruction; }
    public void setDeclaredConstruction(String v){ this.declaredConstruction = v; }
    public String getWeaveType()                { return weaveType; }
    public void setWeaveType(String v)          { this.weaveType = v; }
    public String getWeaveStyle()               { return weaveStyle; }
    public void setWeaveStyle(String v)         { this.weaveStyle = v; }
    public String getFabricType()               { return fabricType; }
    public void setFabricType(String v)         { this.fabricType = v; }
    public String getFinishType()               { return finishType; }
    public void setFinishType(String v)         { this.finishType = v; }
    public String getComposition()              { return composition; }
    public void setComposition(String v)        { this.composition = v; }
    public BigDecimal getGsm()                  { return gsm; }
    public void setGsm(BigDecimal v)            { this.gsm = v; }
    public BigDecimal getFinishWidth()          { return finishWidth; }
    public void setFinishWidth(BigDecimal v)    { this.finishWidth = v; }
    public BigDecimal getCuttableWidth()        { return cuttableWidth; }
    public void setCuttableWidth(BigDecimal v)  { this.cuttableWidth = v; }
    public String getLightSource()              { return lightSource; }
    public void setLightSource(String v)        { this.lightSource = v; }
    public String getSelvedge()                 { return selvedge; }
    public void setSelvedge(String v)           { this.selvedge = v; }
    public String getColourCode()               { return colourCode; }
    public void setColourCode(String v)         { this.colourCode = v; }
    public String getColourName()               { return colourName; }
    public void setColourName(String v)         { this.colourName = v; }
    public String getCostingCode()              { return costingCode; }
    public void setCostingCode(String v)        { this.costingCode = v; }

    public boolean hasCostingCode() {
        return costingCode != null && !costingCode.isBlank();
    }
}

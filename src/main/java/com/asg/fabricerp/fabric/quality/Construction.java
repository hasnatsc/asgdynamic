package com.asg.fabricerp.fabric.quality;

import com.asg.fabricerp.common.BaseOrgEntity;
import com.asg.fabricerp.fabric.setup.FabricAttribute;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A fabric quality: what a booking line will reference instead of re-keying the legacy line's
 * sixty spec columns on every order.
 *
 * <h2>What moved here</h2>
 * Warp/weft count 1-3 and their ratios become {@link ConstructionYarn} rows; composition becomes
 * {@link ConstructionFibre} rows that total 100, so it can be searched and reported rather than
 * parsed out of a name; weave, density, widths and GSM are columns. The legacy "Construction"
 * text ({@code 40x40/120x80}) is {@link #notation()} - derived, never typed, so it cannot
 * disagree with the yarns and density it describes.
 */
@Entity
@Table(name = "fab_constructions")
public class Construction extends BaseOrgEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    /** Optional trade name: "Denim 3/1 RHT 9.5 oz". The notation identifies it either way. */
    @Column(length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "weave_type_id")
    private FabricAttribute weaveType;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "weave_style_id")
    private FabricAttribute weaveStyle;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finish_type_id")
    private FabricAttribute finishType;

    @Column(precision = 10, scale = 2) private BigDecimal epi;
    @Column(precision = 10, scale = 2) private BigDecimal ppi;
    @Column(name = "reed_count", precision = 10, scale = 2) private BigDecimal reedCount;
    @Column(name = "greige_width", precision = 10, scale = 2) private BigDecimal greigeWidth;
    @Column(name = "finish_width", precision = 10, scale = 2) private BigDecimal finishWidth;
    @Column(name = "cuttable_width", precision = 10, scale = 2) private BigDecimal cuttableWidth;
    @Column(precision = 10, scale = 2) private BigDecimal gsm;

    @Column(length = 1000)
    private String remarks;

    @OneToMany(mappedBy = "construction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("direction ASC, sequence ASC")
    private List<ConstructionYarn> yarns = new ArrayList<>();

    @OneToMany(mappedBy = "construction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ConstructionFibre> fibres = new ArrayList<>();

    protected Construction() { }

    public Construction(String code) {
        this.code = code;
    }

    /** Replaces the yarn rows wholesale - the form posts all of them or none. */
    void replaceYarns(List<ConstructionYarn> rows) {
        yarns.clear();
        rows.forEach(row -> {
            row.attachTo(this);
            yarns.add(row);
        });
    }

    void replaceFibres(List<ConstructionFibre> rows) {
        fibres.clear();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).attachTo(this, i + 1);
            fibres.add(rows.get(i));
        }
    }

    /**
     * The trade notation: warp counts x weft counts / EPI x PPI, then the finished width -
     * {@code 40x40/120x80, 58"}. Several yarns in one direction join with "+".
     */
    public String notation() {
        String warp = counts(ConstructionYarn.Direction.WARP);
        String weft = counts(ConstructionYarn.Direction.WEFT);
        StringBuilder out = new StringBuilder();
        if (!warp.isEmpty() || !weft.isEmpty()) {
            out.append(warp.isEmpty() ? "?" : warp).append('x').append(weft.isEmpty() ? "?" : weft);
        }
        if (epi != null || ppi != null) {
            if (!out.isEmpty()) out.append('/');
            out.append(plain(epi)).append('x').append(plain(ppi));
        }
        if (finishWidth != null) {
            if (!out.isEmpty()) out.append(", ");
            out.append(plain(finishWidth)).append('"');
        }
        return out.toString();
    }

    /** "70% Viscose 30% Linen", largest share first. */
    public String composition() {
        return fibres.stream()
            .sorted(Comparator.comparing(ConstructionFibre::getPercentage).reversed())
            .map(f -> plain(f.getPercentage()) + "% " + f.getFiberType().label())
            .collect(Collectors.joining(" "));
    }

    private String counts(ConstructionYarn.Direction direction) {
        return yarns.stream()
            .filter(y -> y.getDirection() == direction)
            .sorted(Comparator.comparing(ConstructionYarn::getSequence))
            .map(y -> y.getYarnCount().getName())
            .collect(Collectors.joining("+"));
    }

    static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    public String getCode()                    { return code; }
    public String getName()                    { return name; }
    public void setName(String v)              { this.name = v; }
    public FabricAttribute getWeaveType()      { return weaveType; }
    public void setWeaveType(FabricAttribute v){ this.weaveType = v; }
    public FabricAttribute getWeaveStyle()     { return weaveStyle; }
    public void setWeaveStyle(FabricAttribute v){ this.weaveStyle = v; }
    public FabricAttribute getFinishType()     { return finishType; }
    public void setFinishType(FabricAttribute v){ this.finishType = v; }
    public BigDecimal getEpi()                 { return epi; }
    public void setEpi(BigDecimal v)           { this.epi = v; }
    public BigDecimal getPpi()                 { return ppi; }
    public void setPpi(BigDecimal v)           { this.ppi = v; }
    public BigDecimal getReedCount()           { return reedCount; }
    public void setReedCount(BigDecimal v)     { this.reedCount = v; }
    public BigDecimal getGreigeWidth()         { return greigeWidth; }
    public void setGreigeWidth(BigDecimal v)   { this.greigeWidth = v; }
    public BigDecimal getFinishWidth()         { return finishWidth; }
    public void setFinishWidth(BigDecimal v)   { this.finishWidth = v; }
    public BigDecimal getCuttableWidth()       { return cuttableWidth; }
    public void setCuttableWidth(BigDecimal v) { this.cuttableWidth = v; }
    public BigDecimal getGsm()                 { return gsm; }
    public void setGsm(BigDecimal v)           { this.gsm = v; }
    public String getRemarks()                 { return remarks; }
    public void setRemarks(String v)           { this.remarks = v; }
    public List<ConstructionYarn> getYarns()   { return List.copyOf(yarns); }
    public List<ConstructionFibre> getFibres() { return List.copyOf(fibres); }
}

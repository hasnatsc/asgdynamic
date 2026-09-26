package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One colour drawn against a {@link BusinessDocumentLineGroup} — asgdynamic's {@code dtlLine}.
 * This is where quantity, price and fulfilment actually live; the fabric spec one level up
 * is shared, but everything here is confirmed per-colour by a real production payload:
 * {@code fabrics_style}, {@code color_reference}, {@code strike_off_reference},
 * {@code lab_dip_reference} and {@code loom_reference} all differed between colours under
 * the same construction, and {@code so_line_dtl_id} — the id a downstream document actually
 * draws against — is this row's id, not the group's.
 */
@Entity
@Table(
    name = "gbl_business_document_color_lines",
    indexes = {
        @Index(name = "ix_gbdcl_line_group", columnList = "line_group_id"),
        @Index(name = "ix_gbdcl_org",        columnList = "organization_id")
    })
public class BusinessDocumentColorLine extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_group_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gbdcl_line_group"))
    private BusinessDocumentLineGroup lineGroup;

    @Column(name = "color_line_no", nullable = false)
    private Integer colorLineNo = 0;

    /**
     * The colour line this line draws against in an upstream document — BPO colour draws
     * against a Booking colour, a Weaving WO colour draws against a BPO colour, and so on.
     * Same role {@code BusinessDocumentLine.sourceLineId} played before the restructure;
     * moved here because the real drawable unit is a colour line, not a fabric-spec group
     * (a BPO can commit less than the full colour breakdown of a Booking line).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_color_line_id",
                foreignKey = @ForeignKey(name = "fk_gbdcl_source_color_line"))
    private BusinessDocumentColorLine sourceColorLine;

    /** Construction-keyed draws: the parent fabric line this line takes its quantity from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_line_group_id",
                foreignKey = @ForeignKey(name = "fk_gbdcl_source_line_group"))
    private BusinessDocumentLineGroup sourceLineGroup;

    /** The stock lot a store document's line moves (inv_fabric_lots). */
    @Column(name = "fabric_lot_id")
    private Long fabricLotId;

    @Column(name = "dye_lot", length = 40)
    private String dyeLot;

    @Column(name = "shade", length = 40)
    private String shade;

    /** A or B - finished fabric only. */
    @Column(name = "grade", length = 2)
    private String grade;

    @PositiveOrZero
    @Column(name = "rolls")
    private Integer rolls;

    /** A delivery schedule line's date. */
    @Column(name = "delivery_date")
    private java.time.LocalDate deliveryDate;

    /** The balance given up when the line was short-closed; no stream may draw on it after. */
    @Column(name = "short_closed_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal shortClosedQuantity = BigDecimal.ZERO;

    @Column(name = "short_close_reason", length = 300)
    private String shortCloseReason;

    /** On a revision: the line of the superseded version this one continues. */
    @Column(name = "revised_from_line_id")
    private Long revisedFromLineId;

    @Column(name = "color_code", length = 40)
    private String colorCode;

    @Column(name = "color_name", length = 120)
    private String colorName;

    /** The buyer-facing style code — confirmed per-colour, not per fabric spec. */
    @Column(name = "fabrics_style", length = 80)
    private String fabricsStyle;

    @Column(name = "color_reference", length = 120)
    private String colorReference;

    @Column(name = "strike_off_reference", length = 120)
    private String strikeOffReference;

    @Column(name = "lab_dip_reference", length = 120)
    private String labDipReference;

    @Column(name = "loom_reference", length = 60)
    private String loomReference;

    @Column(name = "color_specification", length = 200)
    private String colorSpecification;

    /** Approval swatch / lab-dip attachment path. */
    @Column(name = "file_path", length = 300)
    private String filePath;

    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Price per yard — the unit the legacy "Price In Yard" column quotes. */
    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal rate = BigDecimal.ZERO;

    /** Price per metre, quoted alongside rate when the buyer's contract is metric. */
    @PositiveOrZero
    @Column(name = "price_in_meter", precision = 20, scale = 6)
    private BigDecimal priceInMeter;

    @PositiveOrZero
    // Written by LineDrawLedger alone (a mirror of the line's principal stream), so never by an update here.
    @Column(name = "fulfilled_quantity", nullable = false, updatable = false, precision = 20, scale = 6)
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    /** Derived. No setter: {@link #recalculate()} owns it. */
    @Column(name = "line_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal lineAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String remarks;

    public BusinessDocumentLineGroup getLineGroup()      { return lineGroup; }
    public void setLineGroup(BusinessDocumentLineGroup v){ this.lineGroup = v; }
    public Integer getColorLineNo()                      { return colorLineNo; }
    public void setColorLineNo(Integer v)                { this.colorLineNo = v; }
    public BusinessDocumentColorLine getSourceColorLine() { return sourceColorLine; }
    public void setSourceColorLine(BusinessDocumentColorLine v) { this.sourceColorLine = v; }
    public BusinessDocumentLineGroup getSourceLineGroup() { return sourceLineGroup; }
    public void setSourceLineGroup(BusinessDocumentLineGroup v) { this.sourceLineGroup = v; }
    public Long getFabricLotId()                         { return fabricLotId; }
    public void setFabricLotId(Long v)                   { this.fabricLotId = v; }
    public String getDyeLot()                            { return dyeLot; }
    public void setDyeLot(String v)                      { this.dyeLot = blankToNull(v); }
    public String getShade()                             { return shade; }
    public void setShade(String v)                       { this.shade = blankToNull(v); }
    public String getGrade()                             { return grade; }
    public void setGrade(String v)                       { this.grade = blankToNull(v); }
    public Integer getRolls()                            { return rolls; }
    public void setRolls(Integer v)                      { this.rolls = v; }
    public java.time.LocalDate getDeliveryDate()         { return deliveryDate; }
    public void setDeliveryDate(java.time.LocalDate v)   { this.deliveryDate = v; }
    public BigDecimal getShortClosedQuantity()           { return shortClosedQuantity; }
    public String getShortCloseReason()                  { return shortCloseReason; }
    public boolean isShortClosed()                       { return shortCloseReason != null; }
    public Long getRevisedFromLineId()                   { return revisedFromLineId; }
    public void setRevisedFromLineId(Long v)             { this.revisedFromLineId = v; }

    /** Closes the line's balance: nothing may be drawn on it after, and the reason stays on record. */
    public void shortClose(BigDecimal balance, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Say why the line is short-closed");
        }
        if (isShortClosed()) {
            throw new IllegalStateException("Line %d is already short-closed".formatted(colorLineNo));
        }
        this.shortClosedQuantity = balance == null || balance.signum() < 0 ? BigDecimal.ZERO : balance;
        this.shortCloseReason = reason.strip();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    public String getColorCode()                         { return colorCode; }
    public void setColorCode(String v)                   { this.colorCode = v; }
    public String getColorName()                         { return colorName; }
    public void setColorName(String v)                   { this.colorName = v; }
    public String getFabricsStyle()                      { return fabricsStyle; }
    public void setFabricsStyle(String v)                { this.fabricsStyle = v; }
    public String getColorReference()                    { return colorReference; }
    public void setColorReference(String v)              { this.colorReference = v; }
    public String getStrikeOffReference()                { return strikeOffReference; }
    public void setStrikeOffReference(String v)          { this.strikeOffReference = v; }
    public String getLabDipReference()                   { return labDipReference; }
    public void setLabDipReference(String v)             { this.labDipReference = v; }
    public String getLoomReference()                     { return loomReference; }
    public void setLoomReference(String v)               { this.loomReference = v; }
    public String getColorSpecification()                { return colorSpecification; }
    public void setColorSpecification(String v)          { this.colorSpecification = v; }
    public String getFilePath()                          { return filePath; }
    public void setFilePath(String v)                    { this.filePath = v; }
    public BigDecimal getQuantity()                      { return quantity; }
    public void setQuantity(BigDecimal v)                { this.quantity = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getRate()                          { return rate; }
    public void setRate(BigDecimal v)                    { this.rate = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getPriceInMeter()                  { return priceInMeter; }
    public void setPriceInMeter(BigDecimal v)            { this.priceInMeter = v; }
    public BigDecimal getFulfilledQuantity()             { return fulfilledQuantity; }
    public BigDecimal getLineAmount()                    { return lineAmount; }
    public String getRemarks()                           { return remarks; }
    public void setRemarks(String v)                     { this.remarks = v; }

    /** One yard in metres - exact by definition, so the conversion never drifts. */
    public static final BigDecimal METRES_PER_YARD = new BigDecimal("0.9144");

    /** Authoritative line maths for a yard-priced document. */
    public void recalculate() {
        recalculate(false);
    }

    /**
     * Authoritative line maths. Invoked from the document's recalculateTotals().
     *
     * <p>The price the buyer was quoted in is the one entered; the other unit is derived, so the
     * two can never disagree. Yard-priced (the usual case): price per metre = price per yard /
     * 0.9144 - the legacy screen's 4.10/yd beside 4.48/m. Metre-priced: price per yard = price
     * per metre x 0.9144, and the quantity is in metres, so the amount is taken in metres.
     */
    public void recalculate(boolean pricedInMeter) {
        if (pricedInMeter) {
            BigDecimal perMetre = priceInMeter == null ? BigDecimal.ZERO : priceInMeter;
            this.rate = perMetre.multiply(METRES_PER_YARD).setScale(6, RoundingMode.HALF_UP);
            this.lineAmount = quantity.multiply(perMetre).setScale(6, RoundingMode.HALF_UP);
        } else {
            this.priceInMeter = rate.divide(METRES_PER_YARD, 6, RoundingMode.HALF_UP);
            this.lineAmount = quantity.multiply(rate).setScale(6, RoundingMode.HALF_UP);
        }
    }

    public BigDecimal outstandingQuantity() {
        return quantity.subtract(fulfilledQuantity).max(BigDecimal.ZERO);
    }

    public boolean isFullyFulfilled() {
        return fulfilledQuantity.compareTo(quantity) >= 0;
    }

    /** Guarded so a downstream draw can never exceed this colour's ordered quantity. */
    public void fulfil(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Fulfilment quantity must be positive");
        }
        BigDecimal next = fulfilledQuantity.add(amount);
        if (next.compareTo(quantity) > 0) {
            throw new IllegalStateException(
                ("Colour line %d: fulfilling %s would exceed the ordered quantity %s "
               + "(already fulfilled %s)").formatted(colorLineNo, amount, quantity, fulfilledQuantity));
        }
        this.fulfilledQuantity = next;
    }

    /** Reverses a prior {@link #fulfil}. */
    public void release(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        BigDecimal next = fulfilledQuantity.subtract(amount);
        if (next.signum() < 0) {
            throw new IllegalStateException(
                ("Colour line %d: releasing %s would take fulfilled quantity below zero "
               + "(currently fulfilled %s)").formatted(colorLineNo, amount, fulfilledQuantity));
        }
        this.fulfilledQuantity = next;
    }
}

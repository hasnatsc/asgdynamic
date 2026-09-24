package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A line of any document — item, fabric identity, quantity, money.
 *
 * <p>Collapses asgdynamic's two-level {@code dtlSet} → {@code dtlLine} structure into one
 * row carrying an embedded {@link FabricSpec}. The middle level existed only to avoid
 * repeating fabric attributes per colour; an embeddable does that without a second table.
 */
@Entity
@Table(
    name = "gbl_business_document_lines",
    indexes = {
        @Index(name = "ix_gbdl_document", columnList = "document_id"),
        @Index(name = "ix_gbdl_item",     columnList = "item_id"),
        @Index(name = "ix_gbdl_org",      columnList = "organization_id")
    })
public class BusinessDocumentLine extends BaseOrgLineEntity {

    /**
     * Mapped by id, not association. The parent is always loaded with the line in practice,
     * and an association here forces a join on every grid query that reads lines.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gbdl_document"))
    private BusinessDocument document;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo = 0;

    /**
     * The upstream line this line draws against — a BPO line's source is the Booking line
     * it was raised from, an MRR line's source is the PO line it receives. Null on a line
     * that originates its own quantity (a Booking line has no source).
     *
     * <p>Sibling to {@link BusinessDocument#getParentDocumentId()}: that links document to
     * document (this BPO came from that Booking); this links line to line (this specific
     * 500m draw came from that specific Booking line's 2000m). asgdynamic tracked this per
     * screen as {@code transaction_qty_so} alongside {@code transactionQty}; here it is one
     * column reused by every document type that consumes another.
     *
     * <p>Not a JPA association for the same reason {@link #document} isn't one directly
     * off the id — the source line usually belongs to a different aggregate root than the
     * one being loaded, so resolving it is a deliberate, visible repository call in the
     * service layer, not something that happens implicitly on access.
     */
    @Column(name = "source_line_id")
    private Long sourceLineId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "uom_id")
    private Long uomId;

    /** Fabric identity. Null on non-fabric lines (MRO, general items, dyes). */
    @Embedded
    private FabricSpec fabric = new FabricSpec();

    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal rate = BigDecimal.ZERO;

    /**
     * Quantity already consumed downstream — received against a PO, issued against an SR,
     * delivered against a booking. Drives the PARTIAL status without a second query.
     */
    @PositiveOrZero
    @Column(name = "fulfilled_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    /** Derived. No setter: {@link #recalculate()} owns it. */
    @Column(name = "line_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal lineAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String remarks;

    public BusinessDocument getDocument()            { return document; }
    public void setDocument(BusinessDocument d)      { this.document = d; }
    public Integer getLineNo()                       { return lineNo; }
    public void setLineNo(Integer v)                 { this.lineNo = v; }
    public Long getSourceLineId()                    { return sourceLineId; }
    public void setSourceLineId(Long v)              { this.sourceLineId = v; }
    public Long getItemId()                          { return itemId; }
    public void setItemId(Long v)                    { this.itemId = v; }
    public Long getUomId()                           { return uomId; }
    public void setUomId(Long v)                     { this.uomId = v; }
    public FabricSpec getFabric()                    { return fabric; }
    public void setFabric(FabricSpec v)              { this.fabric = v == null ? new FabricSpec() : v; }
    public BigDecimal getQuantity()                  { return quantity; }
    public void setQuantity(BigDecimal v)            { this.quantity = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getRate()                      { return rate; }
    public void setRate(BigDecimal v)                { this.rate = v == null ? BigDecimal.ZERO : v; }
    public BigDecimal getFulfilledQuantity()         { return fulfilledQuantity; }
    public BigDecimal getLineAmount()                { return lineAmount; }
    public String getRemarks()                       { return remarks; }
    public void setRemarks(String v)                 { this.remarks = v; }

    /** Authoritative line maths. Invoked from the parent's recalculateTotals(). */
    public void recalculate() {
        this.lineAmount = quantity.multiply(rate).setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal outstandingQuantity() {
        return quantity.subtract(fulfilledQuantity).max(BigDecimal.ZERO);
    }

    public boolean isFullyFulfilled() {
        return fulfilledQuantity.compareTo(quantity) >= 0;
    }

    /**
     * Records downstream consumption. Guarded so a receipt or issue can never exceed the
     * ordered quantity — the kind of ceiling SpindleERP had to add later as
     * {@code YarnReceiveCeilingTest} and {@code DeliveryChallanBalanceGuardTest}.
     */
    public void fulfil(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Fulfilment quantity must be positive");
        }
        BigDecimal next = fulfilledQuantity.add(amount);
        if (next.compareTo(quantity) > 0) {
            throw new IllegalStateException(
                ("Line %d: fulfilling %s would exceed the ordered quantity %s "
               + "(already fulfilled %s)").formatted(lineNo, amount, quantity, fulfilledQuantity));
        }
        this.fulfilledQuantity = next;
    }

    /**
     * Reverses a prior {@link #fulfil}. Needed wherever a draw against this line can be
     * undone — a BPO line deleted or re-quantified while still DRAFT releases what it had
     * reserved against the Booking line it sourced from.
     */
    public void release(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        BigDecimal next = fulfilledQuantity.subtract(amount);
        if (next.signum() < 0) {
            throw new IllegalStateException(
                ("Line %d: releasing %s would take fulfilled quantity below zero "
               + "(currently fulfilled %s)").formatted(lineNo, amount, fulfilledQuantity));
        }
        this.fulfilledQuantity = next;
    }
}

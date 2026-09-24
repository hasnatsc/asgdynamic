package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One table for every transactional document — SpindleERP's {@code global_business_documents}
 * idea, carried over and tightened.
 *
 * <h2>What it replaces</h2>
 * asgdynamic implements 42 master-detail screens as 42 controller/table pairs with the same
 * shape: header, {@code dtlSet}, {@code dtlLine}, {@code dtlTcSet}, plus a parallel
 * {@code *Revision} controller. All of that collapses into this type plus {@link DocumentType}.
 *
 * <h2>Where it improves on SpindleERP</h2>
 * <ul>
 *   <li><b>Indexed from day one.</b> The legacy schema grew under {@code ddl-auto=update},
 *       which creates foreign keys but never indexes them — 59% of its FKs have no index.
 *       The indexes below are declared with the table and shipped by Flyway.</li>
 *   <li><b>Totals are derived, not set.</b> {@code subtotalAmount} and friends have no public
 *       setters; {@link #recalculateTotals()} owns them. asgdynamic computed these in the
 *       browser and POSTed the result.</li>
 *   <li><b>Revision lineage is explicit</b> ({@code revisionNo} + {@code revisionOfId}) rather
 *       than a separate controller per revisable document.</li>
 * </ul>
 */
@Entity
@Table(
    name = "gbl_business_documents",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_gbd_org_document_no", columnNames = {"organization_id", "document_no"}),
    indexes = {
        // Every list screen filters org + type + status, in that order.
        @Index(name = "ix_gbd_org_type_status", columnList = "organization_id,document_type,status"),
        @Index(name = "ix_gbd_org_unit_date",   columnList = "organization_id,business_unit_id,document_date"),
        @Index(name = "ix_gbd_party",           columnList = "party_id"),
        @Index(name = "ix_gbd_parent",          columnList = "parent_document_id"),
        @Index(name = "ix_gbd_revision_of",     columnList = "revision_of_id"),
        @Index(name = "ix_gbd_document_date",   columnList = "document_date")
    })
public class BusinessDocument extends BaseOrgEntity {

    @Column(name = "document_no", nullable = false, length = 60)
    private String documentNo;

    /** Buyer's or supplier's own reference, when they impose one. */
    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessDocumentStatus status = BusinessDocumentStatus.DRAFT;

    @Column(name = "business_unit_id", nullable = false)
    private Long businessUnitId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    /** Customer or supplier, depending on {@link DocumentType#family()}. */
    @Column(name = "party_id")
    private Long partyId;

    /** Upstream document: BPO -> Booking, Rout Card -> BPO, MRR -> PO. */
    @Column(name = "parent_document_id")
    private Long parentDocumentId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "BDT";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 0;

    @Column(name = "revision_of_id")
    private Long revisionOfId;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    // --- derived totals: no setters, see recalculateTotals() ---
    @Column(name = "subtotal_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "total_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    @Valid
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessDocumentLine> lines = new ArrayList<>();

    public String getDocumentNo()               { return documentNo; }
    public void setDocumentNo(String v)         { this.documentNo = v; }
    public String getReferenceNo()              { return referenceNo; }
    public void setReferenceNo(String v)        { this.referenceNo = v; }
    public DocumentType getDocumentType()       { return documentType; }
    public void setDocumentType(DocumentType v) { this.documentType = v; }
    public BusinessDocumentStatus getStatus()   { return status; }
    public Long getBusinessUnitId()             { return businessUnitId; }
    public void setBusinessUnitId(Long v)       { this.businessUnitId = v; }
    public Long getWarehouseId()                { return warehouseId; }
    public void setWarehouseId(Long v)          { this.warehouseId = v; }
    public LocalDate getDocumentDate()          { return documentDate; }
    public void setDocumentDate(LocalDate v)    { this.documentDate = v; }
    public LocalDate getRequiredDate()          { return requiredDate; }
    public void setRequiredDate(LocalDate v)    { this.requiredDate = v; }
    public Long getPartyId()                    { return partyId; }
    public void setPartyId(Long v)              { this.partyId = v; }
    public Long getParentDocumentId()           { return parentDocumentId; }
    public void setParentDocumentId(Long v)     { this.parentDocumentId = v; }
    public String getCurrencyCode()             { return currencyCode; }
    public void setCurrencyCode(String v)       { this.currencyCode = v; }
    public BigDecimal getExchangeRate()         { return exchangeRate; }
    public void setExchangeRate(BigDecimal v)   { this.exchangeRate = v; }
    public Integer getRevisionNo()              { return revisionNo; }
    public void setRevisionNo(Integer v)        { this.revisionNo = v; }
    public Long getRevisionOfId()               { return revisionOfId; }
    public void setRevisionOfId(Long v)         { this.revisionOfId = v; }
    public String getRemarks()                  { return remarks; }
    public void setRemarks(String v)            { this.remarks = v; }
    public BigDecimal getSubtotalAmount()       { return subtotalAmount; }
    public BigDecimal getTotalQuantity()        { return totalQuantity; }
    public List<BusinessDocumentLine> getLines(){ return lines; }

    public void setLines(List<BusinessDocumentLine> incoming) {
        this.lines.clear();
        if (incoming != null) incoming.forEach(this::addLine);
    }

    public void addLine(BusinessDocumentLine line) {
        line.setDocument(this);
        line.setOrganizationId(getOrganizationId());
        this.lines.add(line);
    }

    /**
     * Authoritative header maths. Called by the service before persist; the browser's
     * numbers are never trusted.
     */
    public void recalculateTotals() {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal qty = BigDecimal.ZERO;
        for (BusinessDocumentLine line : lines) {
            line.recalculate();
            amount = amount.add(line.getLineAmount());
            qty = qty.add(line.getQuantity());
        }
        this.subtotalAmount = amount;
        this.totalQuantity = qty;
    }

    public void transitionTo(BusinessDocumentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                "Illegal transition %s -> %s for %s".formatted(status, target, documentNo));
        }
        this.status = target;
    }

    public void assertEditable() {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                ("Document %s is %s and can no longer be edited. "
               + "Raise a revision instead.").formatted(documentNo, status));
        }
    }
}

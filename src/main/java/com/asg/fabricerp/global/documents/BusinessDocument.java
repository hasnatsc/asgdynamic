package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgEntity;
import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.security.FabricUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * shape: header → {@code dtlSet} (fabric spec, one per construction) → {@code dtlLine}
 * (one per colour) → {@code dtlTcSet}, plus a parallel {@code *Revision} controller. That
 * collapses into this type plus {@link DocumentType}, {@link BusinessDocumentLineGroup} and
 * {@link BusinessDocumentColorLine} — the same two levels, not flattened into one the way
 * an earlier version of this model tried (see {@link FabricSpec}'s javadoc for why that
 * was wrong).
 *
 * <h2>Where it improves on SpindleERP</h2>
 * <ul>
 *   <li><b>Indexed from day one.</b> The legacy schema grew under {@code ddl-auto=update},
 *       which creates foreign keys but never indexes them — 59% of its FKs have no index.
 *       The indexes below are declared with the table and shipped by Flyway.</li>
 *   <li><b>Totals are derived, not set.</b> {@code subtotalAmount} and friends have no public
 *       setters; {@link #recalculateTotals()} owns them. asgdynamic computed these in the
 *       browser and POSTed the result.</li>
 *   <li><b>Revision lineage is explicit</b> ({@code revisionNo} + {@code revisionOf}) rather
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
        @Index(name = "ix_gbd_document_date",   columnList = "document_date"),
        @Index(name = "ix_gbd_marketing_team",  columnList = "marketing_team_id"),
        @Index(name = "ix_gbd_business_unit",   columnList = "business_unit_id"),
        @Index(name = "ix_gbd_warehouse",       columnList = "warehouse_id")
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

    /** Stamped by the service from the caller's operating unit, never taken from the request. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_unit_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gbd_business_unit"))
    private BusinessUnit businessUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", foreignKey = @ForeignKey(name = "fk_gbd_warehouse"))
    private Warehouse warehouse;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    /**
     * Customer or supplier - whichever {@link DocumentType#requiredPartyRole()} demands, checked
     * on save by {@link DocumentReferences}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", foreignKey = @ForeignKey(name = "fk_gbd_party"))
    private Party party;

    /** Upstream document: BPO -> Booking, Rout Card -> BPO, MRR -> PO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id", foreignKey = @ForeignKey(name = "fk_gbd_parent"))
    private BusinessDocument parentDocument;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "BDT";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /**
     * ADM-7: the marketing team this document was raised under. Stamped once at creation — a
     * Booking from its creator's team, everything downstream from its parent — and never
     * rewritten, so a person moving team does not drag their old documents with them.
     *
     * <p>Read-only to JSON: controllers bind this entity straight from the request body, and
     * the team is decided by the server, never submitted.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketing_team_id", foreignKey = @ForeignKey(name = "fk_gbd_marketing_team"))
    private MarketingTeam marketingTeam;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 0;

    /** The root of this document's revision chain. Set by {@link DocumentRevisionService} only. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_of_id", foreignKey = @ForeignKey(name = "fk_gbd_revision_of"))
    private BusinessDocument revisionOf;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    // --- derived totals: no setters, see recalculateTotals() ---
    @Column(name = "subtotal_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "total_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    // --- sales header: Booking's, inherited by the documents raised against it ---

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", length = 20)
    private BookingType bookingType;

    /** Legacy "Type of order" ({@code specialType}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 20)
    private OrderType orderType;

    /** The label the fabric carries; a party holding {@code BRAND}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", foreignKey = @ForeignKey(name = "fk_gbd_brand"))
    private Party brand;

    /** The factory that will cut the fabric; a party holding {@code GARMENT_FACTORY}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garments_id", foreignKey = @ForeignKey(name = "fk_gbd_garments"))
    private Party garments;

    @Column(name = "garments_address", length = 500)
    private String garmentsAddress;

    /** The buyer name the costing was raised for - shown beside the booking's own buyer. */
    @Column(name = "pre_cost_buyer", length = 150)
    private String preCostBuyer;

    /**
     * The buyer is quoted per metre: colour lines take their price in metres and quantities in
     * metres, and the yard price is derived. Off (the usual case), the reverse.
     */
    @Column(name = "price_in_meter", nullable = false)
    private Boolean priceInMeter = Boolean.FALSE;

    /**
     * Never bound from JSON - a user row is not something a request body gets to describe.
     * The request names {@link #marketingPersonId}; {@link DocumentReferences} resolves it.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketing_person_id", foreignKey = @ForeignKey(name = "fk_gbd_marketing_person"))
    private FabricUser marketingPerson;

    // --- production chain header (V28) ---

    /** Dyeing work order: dye, print, finish only or rework. */
    @Enumerated(EnumType.STRING)
    @Column(name = "process_kind", length = 20)
    private ProcessKind processKind;

    /** Subcontracted weaving or dyeing: the vendor who does the work. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_party_id", foreignKey = @ForeignKey(name = "fk_gbd_vendor"))
    private Party vendor;

    @Column(name = "vehicle_no", length = 40)
    private String vehicleNo;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    /** Dyeing work order: closed, with its unreturned greige booked as process loss. */
    @Column(name = "batch_closed", nullable = false)
    private Boolean batchClosed = Boolean.FALSE;

    @Transient
    private Long marketingPersonId;

    /**
     * The team an UNRESTRICTED user files a new Booking under. Request-only: {@link #marketingTeam}
     * stays read-only to JSON, and the Booking service decides what to stamp - a restricted user's
     * own team whatever this says, and this only once, at creation (ADM-7).
     */
    @Transient
    private Long marketingTeamId;

    @Valid
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessDocumentLineGroup> lineGroups = new ArrayList<>();

    @Valid
    @OrderBy("serialNo ASC")
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessDocumentTerm> terms = new ArrayList<>();

    /** Whether the request said anything about terms: absent on create means "the defaults". */
    @Transient
    private boolean termsSubmitted;

    public String getDocumentNo()               { return documentNo; }
    public void setDocumentNo(String v)         { this.documentNo = v; }
    public String getReferenceNo()              { return referenceNo; }
    public void setReferenceNo(String v)        { this.referenceNo = v; }
    public DocumentType getDocumentType()       { return documentType; }
    public void setDocumentType(DocumentType v) { this.documentType = v; }
    public BusinessDocumentStatus getStatus()   { return status; }
    public BusinessUnit getBusinessUnit()       { return businessUnit; }
    public void setBusinessUnit(BusinessUnit v) { this.businessUnit = v; }
    public Warehouse getWarehouse()             { return warehouse; }
    public void setWarehouse(Warehouse v)       { this.warehouse = v; }
    public LocalDate getDocumentDate()          { return documentDate; }
    public void setDocumentDate(LocalDate v)    { this.documentDate = v; }
    public LocalDate getRequiredDate()          { return requiredDate; }
    public void setRequiredDate(LocalDate v)    { this.requiredDate = v; }
    public Party getParty()                     { return party; }
    public void setParty(Party v)               { this.party = v; }
    public BusinessDocument getParentDocument() { return parentDocument; }
    public void setParentDocument(BusinessDocument v) { this.parentDocument = v; }
    public String getCurrencyCode()             { return currencyCode; }
    public void setCurrencyCode(String v)       { this.currencyCode = v; }
    public BigDecimal getExchangeRate()         { return exchangeRate; }
    public void setExchangeRate(BigDecimal v)   { this.exchangeRate = v; }
    public MarketingTeam getMarketingTeam()     { return marketingTeam; }
    public void stampMarketingTeam(MarketingTeam v) { this.marketingTeam = v; }
    public Integer getRevisionNo()              { return revisionNo; }
    public void setRevisionNo(Integer v)        { this.revisionNo = v; }
    public BusinessDocument getRevisionOf()     { return revisionOf; }
    public void setRevisionOf(BusinessDocument v) { this.revisionOf = v; }
    public String getRemarks()                  { return remarks; }
    public void setRemarks(String v)            { this.remarks = v; }
    public BigDecimal getSubtotalAmount()       { return subtotalAmount; }
    public BigDecimal getTotalQuantity()        { return totalQuantity; }
    public List<BusinessDocumentLineGroup> getLineGroups() { return lineGroups; }
    public BookingType getBookingType()         { return bookingType; }
    public void setBookingType(BookingType v)   { this.bookingType = v; }
    public OrderType getOrderType()             { return orderType; }
    public void setOrderType(OrderType v)       { this.orderType = v; }
    public Party getBrand()                     { return brand; }
    public void setBrand(Party v)               { this.brand = v; }
    public Party getGarments()                  { return garments; }
    public void setGarments(Party v)            { this.garments = v; }
    public String getGarmentsAddress()          { return garmentsAddress; }
    public void setGarmentsAddress(String v)    { this.garmentsAddress = v; }
    public String getPreCostBuyer()             { return preCostBuyer; }
    public void setPreCostBuyer(String v)       { this.preCostBuyer = v; }
    public boolean isPriceInMeter()             { return Boolean.TRUE.equals(priceInMeter); }
    public void setPriceInMeter(Boolean v)      { this.priceInMeter = Boolean.TRUE.equals(v); }
    public FabricUser getMarketingPerson()      { return marketingPerson; }
    public void setMarketingPerson(FabricUser v) { this.marketingPerson = v; }
    public Long getMarketingPersonId()          { return marketingPersonId; }
    public void setMarketingPersonId(Long v)    { this.marketingPersonId = v; }
    public Long getMarketingTeamId()            { return marketingTeamId; }
    public void setMarketingTeamId(Long v)      { this.marketingTeamId = v; }
    public List<BusinessDocumentTerm> getTerms() { return terms; }
    public ProcessKind getProcessKind()         { return processKind; }
    public void setProcessKind(ProcessKind v)   { this.processKind = v; }
    public Party getVendor()                    { return vendor; }
    public void setVendor(Party v)              { this.vendor = v; }
    public String getVehicleNo()                { return vehicleNo; }
    public void setVehicleNo(String v)          { this.vehicleNo = v; }
    public String getDriverName()               { return driverName; }
    public void setDriverName(String v)         { this.driverName = v; }
    public boolean isBatchClosed()              { return Boolean.TRUE.equals(batchClosed); }
    public void closeBatch()                    { this.batchClosed = Boolean.TRUE; }
    public boolean isTermsSubmitted()           { return termsSubmitted; }

    public void setTerms(List<BusinessDocumentTerm> incoming) {
        this.termsSubmitted = true;
        this.terms.clear();
        if (incoming != null) incoming.forEach(this::addTerm);
    }

    public void addTerm(BusinessDocumentTerm term) {
        term.setDocument(this);
        term.setOrganizationId(getOrganizationId());
        this.terms.add(term);
    }

    /**
     * Blank clauses dropped, the rest ordered by the serial the user gave them and renumbered
     * 1..n - the user's serial says where a clause goes, not what number it keeps.
     */
    public void normalizeTerms() {
        terms.removeIf(t -> t.getBodyText() == null || t.getBodyText().isBlank());
        terms.sort(java.util.Comparator.comparing(
            BusinessDocumentTerm::getSerialNo, java.util.Comparator.nullsLast(Integer::compare)));
        int serial = 1;
        for (BusinessDocumentTerm term : terms) term.setSerialNo(serial++);
    }

    /**
     * ADM-3: whether this document is within a user's row scope. Must agree exactly with the
     * predicate in {@link BusinessDocumentRepository#search} — a grid and a detail lookup that
     * disagree leak through whichever is wider.
     *
     * <p>A document with no warehouse is not narrowed by warehouse scope: it isn't held in any
     * store (a Booking, say), and hiding it from every store-restricted user would stop a store
     * clerk opening the BPO their receipt is raised against. A document with no marketing team
     * gets no such allowance — it predates team stamping, and ADM-4's whole point is that a
     * team-restricted user sees their own team's work and nothing else.
     */
    public boolean isVisibleTo(RowScope scope) {
        return scope.permits(ScopeDimension.BUSINESS_UNIT, idOf(businessUnit))
            && (warehouse == null || scope.permits(ScopeDimension.WAREHOUSE, idOf(warehouse)))
            && scope.permits(ScopeDimension.MARKETING_TEAM, idOf(marketingTeam));
    }

    public void setLineGroups(List<BusinessDocumentLineGroup> incoming) {
        this.lineGroups.clear();
        if (incoming != null) incoming.forEach(this::addLineGroup);
    }

    public void addLineGroup(BusinessDocumentLineGroup group) {
        group.setDocument(this);
        group.setOrganizationId(getOrganizationId());
        this.lineGroups.add(group);
    }

    /**
     * Numbers every group 1..n and every group's colour lines 1..n, independently per
     * group (matching the legacy {@code sort_order}, which restarts per fabric-spec group
     * rather than running document-wide).
     *
     * <p>Used to live only inside {@code ParentLineDrawService.draw()}, so a type with
     * nothing to draw against — Booking, the root of every chain — never got numbered at
     * all: real Maven test run, not the offline reasoning that built this class, caught it.
     * Numbering is a property of the document's own lines regardless of whether it also
     * draws against a parent, so it belongs here and runs for every type unconditionally.
     */
    public void renumberLines() {
        int groupNo = 1;
        for (BusinessDocumentLineGroup group : lineGroups) {
            group.setGroupNo(groupNo++);
            int colorNo = 1;
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                line.setColorLineNo(colorNo++);
            }
        }
    }

    /**
     * Authoritative header maths, rolled up through both levels. Called by the service
     * before persist; the browser's numbers are never trusted.
     */
    public void recalculateTotals() {
        renumberLines();
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal qty = BigDecimal.ZERO;
        for (BusinessDocumentLineGroup group : lineGroups) {
            for (BusinessDocumentColorLine colorLine : group.getColorLines()) {
                colorLine.recalculate(isPriceInMeter());
            }
            amount = amount.add(group.groupAmount());
            qty = qty.add(group.groupQuantity());
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

    /**
     * Moves a committed document along its progress - approved, processing, partial, completed -
     * as its downstream documents are posted or cancelled. Unlike {@link #transitionTo} it may step
     * back (a cancelled delivery reopens a completed order), but never out of the committed states:
     * approval, cancelling and closing keep their own gates.
     */
    public void progressTo(BusinessDocumentStatus target) {
        java.util.Set<BusinessDocumentStatus> inFlight = java.util.EnumSet.of(BusinessDocumentStatus.APPROVED,
            BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL, BusinessDocumentStatus.COMPLETED);
        if (!inFlight.contains(status) || !inFlight.contains(target)) {
            throw new IllegalStateException(
                "%s is %s and its progress cannot move to %s".formatted(documentNo, status, target));
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

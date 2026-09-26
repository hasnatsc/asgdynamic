package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.global.numbering.NumberSeries;
import com.asg.fabricerp.party.PartyRoleType;

/**
 * Every document the system issues, in one enum — SpindleERP's central idea, carried over.
 *
 * <p>It replaces asgdynamic's ~40 separate controllers/tables with one generic
 * {@link BusinessDocument} discriminated by this type. The prefix is the legacy one
 * (BPOAF000001 was a BPO) and is what each organization's numbering scheme starts from:
 * {@code BPO-2026-000001} by default, reconfigurable per organization - see
 * {@link com.asg.fabricerp.global.numbering.BusinessNumberService}.
 *
 * <p><b>Where this differs from SpindleERP:</b> the production and sales families are
 * fabric-shaped, not yarn-shaped. Spinning blends fibres by recipe and books waste as
 * output; weaving and dyeing route one material through sequential operations and book
 * greige and finished fabric instead. See {@link #isFabricProcess()}.
 */
public enum DocumentType implements NumberSeries {

    /* ---------------------------------------------------------------- SALES
     * Fabric is made to order against a buyer's construction, so the chain starts at a
     * Booking rather than a quotation, and every stage is revisable. SpindleERP's
     * SALES_QUOTATION has no counterpart here.
     */
    // Prefix confirmed "BK" (not a guessed "BKG") against a real production Booking
    // payload: document codes BKAF000017, BKAF000028, BKAF000059.
    BOOKING("BK", "Booking", Family.SALES, "BOOKING"),
    BULK_PRODUCTION_ORDER("BPO", "Production Order", Family.SALES, "BPO"),
    REQUEST_FOR_PI("RPI", "Delivery Schedule", Family.SALES, "RPI"),
    DELIVERY_ORDER("DO", "Delivery Order", Family.SALES, "DO"),
    FABRICS_DELIVERY("FD", "Fabrics Delivery", Family.SALES, "FD"),
    SALES_RETURN("SRT", "Sales Return", Family.SALES, null),

    /* ------------------------------------------------------------- PURCHASE
     * No RFQ / Comparative Statement: fabric inputs (yarn, dyes) are bought on contract,
     * not competitively tendered the way a spinner buys cotton.
     */
    PURCHASE_REQUISITION("SPR", "Store Purchase Requisition", Family.PURCHASE, null),
    CONSUMPTION_SPR("CSPR", "Consumption SPR", Family.PURCHASE, null),
    PURCHASE_ORDER("PO", "Purchase Order", Family.PURCHASE, null),
    GOODS_RECEIPT_NOTE("MRR", "Material Receive Report", Family.PURCHASE, null),
    PURCHASE_RETURN("PRT", "Purchase Return", Family.PURCHASE, null),

    /* ---------------------------------------------------------------- STORE */
    STORE_REQUISITION("SR", "Store Requisition", Family.STORE, null),
    MATERIAL_ISSUE("MI", "Material Issue", Family.STORE, null),
    MATERIAL_RECEIVE("MR", "Material Receive", Family.STORE, null),
    STOCK_TRANSFER("ST", "Stock Transfer", Family.STORE, null),
    STOCK_ADJUSTMENT("SA", "Stock Adjustment", Family.STORE, null),

    /* ----------------------------------------------------------- PRODUCTION
     * Routing, not recipe. The Production-to-Delivery design (com.asg.fabricerp.production):
     * Weaving WO and Dyeing WO (the legacy Processing WO) each draw their own stream of a
     * production order line; Greige Receive is taken against a Weaving WO, Greige Issue and
     * Finished Fabrics Receive against a Dyeing WO. See ChainStep for the whole chain.
     */
    ROUT_CARD("RC", "Rout Card", Family.PRODUCTION, null),
    WEAVING_WORK_ORDER("WWO", "Weaving Work Order", Family.PRODUCTION, "WWO"),
    PROCESSING_WORK_ORDER("PWO", "Dyeing Work Order", Family.PRODUCTION, "PWO"),
    GREIGE_RECEIVE("GR", "Greige Fabrics Received", Family.PRODUCTION, "GR"),
    GREIGE_ISSUE("GI", "Greige Issue", Family.PRODUCTION, "GI"),
    FINISHED_FABRICS_RECEIVE("FFR", "Finished Fabrics Receive", Family.PRODUCTION, "FFR"),
    RAW_MATERIAL_ISSUE("RMI", "Raw Material Issue", Family.PRODUCTION, null),

    /* ----------------------------------------------------------- COMMERCIAL
     * Taken from SpindleERP wholesale, including the back-to-back instruments asgdynamic
     * never had. A fabric exporter needs EBLC/IBLC more than a spinner does: the export LC
     * collateralises the import LC for yarn and dyes.
     */
    EXPORT_PROFORMA_INVOICE("EPI", "Export Proforma Invoice", Family.COMMERCIAL, null),
    IMPORT_PROFORMA_INVOICE("IPI", "Import Proforma Invoice", Family.COMMERCIAL, null),
    EXPORT_LETTER_OF_CREDIT("ELC", "Export Letter Of Credit", Family.COMMERCIAL, null),
    IMPORT_LETTER_OF_CREDIT("ILC", "Import Letter Of Credit", Family.COMMERCIAL, null),
    EXPORT_BACK_TO_BACK_LC("EBLC", "Export Back-to-Back LC", Family.COMMERCIAL, null),
    IMPORT_BACK_TO_BACK_LC("IBLC", "Import Back-to-Back LC", Family.COMMERCIAL, null),
    EXPORT_COMMERCIAL_INVOICE("ECI", "Export Commercial Invoice", Family.COMMERCIAL, null),
    IMPORT_COMMERCIAL_INVOICE("ICI", "Import Commercial Invoice", Family.COMMERCIAL, null),
    DEBIT_NOTE("DN", "Debit Note", Family.COMMERCIAL, null),
    CREDIT_NOTE("CN", "Credit Note", Family.COMMERCIAL, null);

    public enum Family { SALES, PURCHASE, STORE, PRODUCTION, COMMERCIAL }

    private final String prefix;
    private final String label;
    private final Family family;
    private final String roleRoot;

    DocumentType(String prefix, String label, Family family, String roleRoot) {
        this.prefix = prefix;
        this.label = label;
        this.family = family;
        this.roleRoot = roleRoot;
    }

    public String prefix() { return prefix; }
    @Override public String label() { return label; }
    public Family family() { return family; }

    @Override public String seriesCode()    { return name(); }
    @Override public String defaultPrefix() { return prefix; }

    /**
     * The readable root used in this type's screen authorities, e.g. {@code "BOOKING"} for
     * {@code SCREEN_BOOKING_CREATE}. Deliberately not derived from {@link #prefix()} — BPO's
     * prefix ("BPO") happens to read the same as its role root, but Booking's prefix
     * ("BKG") does not, and mechanically deriving one from the other would have silently
     * produced {@code SCREEN_BKG_CREATE}, contradicting the literal string already checked by
     * {@code BookingController}. Null until a type has a controller: {@link #createAuthority()}
     * fails loudly rather than inventing a name nobody has committed to yet.
     */
    public String roleRoot() {
        if (roleRoot == null) {
            throw new UnsupportedOperationException(
                "No role-name root registered for " + this + " — add one to DocumentType "
              + "alongside its controller, matching whatever @PreAuthorize already checks.");
        }
        return roleRoot;
    }

    /**
     * The authority that may create a new document of this type, e.g.
     * {@code SCREEN_BOOKING_CREATE}. See {@code Screen}/{@code Verb} in the {@code security}
     * package — this used to be a single flat {@code ROLE_BOOKING_MAKER} covering create,
     * revise and delete together; the verb model grants each separately.
     */
    public String createAuthority() {
        return "SCREEN_" + roleRoot() + "_CREATE";
    }

    /** The authority that may revise an existing document of this type. */
    public String amendAuthority() {
        return "SCREEN_" + roleRoot() + "_AMEND";
    }

    /**
     * The approve/reject authority for this type — genuinely per-screen now, where the old
     * model had one global {@code ROLE_APPROVAL} covering every document type. No Commercial
     * document type has a controller yet, so the three-stage maker/checker/approval path the
     * legacy requestmap carried for that family
     * ({@code ROLE_PI_MAKER}/{@code ROLE_LC_CHECKER}/{@code ROLE_CI_APPROVAL}) is still not
     * implemented here — only single-stage approve/reject exists in {@code ApprovalService}.
     * Build the checker stage when a Commercial type needs it rather than half-wiring an
     * untestable one now.
     */
    public String approveAuthority() {
        return "SCREEN_" + roleRoot() + "_APPROVE";
    }

    /** Documents that move fabric through weaving/dyeing rather than moving stock. */
    public boolean isFabricProcess() {
        return this == ROUT_CARD
            || this == WEAVING_WORK_ORDER
            || this == PROCESSING_WORK_ORDER
            || this == GREIGE_RECEIVE
            || this == GREIGE_ISSUE
            || this == FINISHED_FABRICS_RECEIVE;
    }

    /**
     * The role a party must hold to be named on this type - asfl-erp's
     * {@code DocumentType.requiredPartyRole}, which {@code DocumentReferences} enforces on save.
     * Sales and production documents name the customer the fabric is for (downstream ones inherit
     * it from their parent); purchase documents name a supplier. Store and commercial documents
     * name none until one is built and says otherwise - the commercial family needs both a
     * customer (export) and a supplier (import), so it cannot be decided by family alone.
     *
     * @return null when the type does not constrain its party
     */
    public PartyRoleType requiredPartyRole() {
        return switch (family) {
            case SALES, PRODUCTION -> PartyRoleType.CUSTOMER;
            case PURCHASE -> PartyRoleType.SUPPLIER;
            case STORE, COMMERCIAL -> null;
        };
    }

    /** Types that carry a revision lineage. Fabric sales documents are revised, not edited. */
    public boolean isRevisable() {
        return this == BOOKING
            || this == BULK_PRODUCTION_ORDER
            || this == REQUEST_FOR_PI
            || family == Family.COMMERCIAL;
    }
}

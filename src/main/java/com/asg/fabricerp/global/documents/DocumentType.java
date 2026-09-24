package com.asg.fabricerp.global.documents;

/**
 * Every document the system issues, in one enum — SpindleERP's central idea, carried over.
 *
 * <p>It replaces asgdynamic's ~40 separate controllers/tables with one generic
 * {@link BusinessDocument} discriminated by this type. The prefix is the {@code TYPE} half
 * of the legacy document number {@code {TYPE}{UNIT}{000000}} (BPOAF000001, LCAF000002),
 * so existing numbers keep their shape.
 *
 * <p><b>Where this differs from SpindleERP:</b> the production and sales families are
 * fabric-shaped, not yarn-shaped. Spinning blends fibres by recipe and books waste as
 * output; weaving and dyeing route one material through sequential operations and book
 * greige and finished fabric instead. See {@link #isFabricProcess()}.
 */
public enum DocumentType {

    /* ---------------------------------------------------------------- SALES
     * Fabric is made to order against a buyer's construction, so the chain starts at a
     * Booking rather than a quotation, and every stage is revisable. SpindleERP's
     * SALES_QUOTATION has no counterpart here.
     */
    BOOKING("BKG", "Booking", Family.SALES),
    BULK_PRODUCTION_ORDER("BPO", "Bulk Production Order", Family.SALES),
    REQUEST_FOR_PI("RPI", "Request For PI", Family.SALES),
    DELIVERY_ORDER("DO", "Delivery Order", Family.SALES),
    FABRICS_DELIVERY("FD", "Fabrics Delivery", Family.SALES),
    SALES_RETURN("SRT", "Sales Return", Family.SALES),

    /* ------------------------------------------------------------- PURCHASE
     * No RFQ / Comparative Statement: fabric inputs (yarn, dyes) are bought on contract,
     * not competitively tendered the way a spinner buys cotton.
     */
    PURCHASE_REQUISITION("SPR", "Store Purchase Requisition", Family.PURCHASE),
    CONSUMPTION_SPR("CSPR", "Consumption SPR", Family.PURCHASE),
    PURCHASE_ORDER("PO", "Purchase Order", Family.PURCHASE),
    GOODS_RECEIPT_NOTE("MRR", "Material Receive Report", Family.PURCHASE),
    PURCHASE_RETURN("PRT", "Purchase Return", Family.PURCHASE),

    /* ---------------------------------------------------------------- STORE */
    STORE_REQUISITION("SR", "Store Requisition", Family.STORE),
    MATERIAL_ISSUE("MI", "Material Issue", Family.STORE),
    MATERIAL_RECEIVE("MR", "Material Receive", Family.STORE),
    STOCK_TRANSFER("ST", "Stock Transfer", Family.STORE),
    STOCK_ADJUSTMENT("SA", "Stock Adjustment", Family.STORE),

    /* ----------------------------------------------------------- PRODUCTION
     * Routing, not recipe. A BPO is decomposed into a Rout Card, then work orders per
     * operation; greige comes off the loom, goes out for processing, and returns finished.
     */
    ROUT_CARD("RC", "Rout Card", Family.PRODUCTION),
    WEAVING_WORK_ORDER("WWO", "Weaving Work Order", Family.PRODUCTION),
    PROCESSING_WORK_ORDER("PWO", "Processing Work Order", Family.PRODUCTION),
    GREIGE_RECEIVE("GR", "Greige Fabrics Received", Family.PRODUCTION),
    GREIGE_ISSUE("GI", "Greige Issue For Processing", Family.PRODUCTION),
    FINISHED_FABRICS_RECEIVE("FFR", "Finished Fabrics Received", Family.PRODUCTION),
    RAW_MATERIAL_ISSUE("RMI", "Raw Material Issue", Family.PRODUCTION),

    /* ----------------------------------------------------------- COMMERCIAL
     * Taken from SpindleERP wholesale, including the back-to-back instruments asgdynamic
     * never had. A fabric exporter needs EBLC/IBLC more than a spinner does: the export LC
     * collateralises the import LC for yarn and dyes.
     */
    EXPORT_PROFORMA_INVOICE("EPI", "Export Proforma Invoice", Family.COMMERCIAL),
    IMPORT_PROFORMA_INVOICE("IPI", "Import Proforma Invoice", Family.COMMERCIAL),
    EXPORT_LETTER_OF_CREDIT("ELC", "Export Letter Of Credit", Family.COMMERCIAL),
    IMPORT_LETTER_OF_CREDIT("ILC", "Import Letter Of Credit", Family.COMMERCIAL),
    EXPORT_BACK_TO_BACK_LC("EBLC", "Export Back-to-Back LC", Family.COMMERCIAL),
    IMPORT_BACK_TO_BACK_LC("IBLC", "Import Back-to-Back LC", Family.COMMERCIAL),
    EXPORT_COMMERCIAL_INVOICE("ECI", "Export Commercial Invoice", Family.COMMERCIAL),
    IMPORT_COMMERCIAL_INVOICE("ICI", "Import Commercial Invoice", Family.COMMERCIAL),
    DEBIT_NOTE("DN", "Debit Note", Family.COMMERCIAL),
    CREDIT_NOTE("CN", "Credit Note", Family.COMMERCIAL);

    public enum Family { SALES, PURCHASE, STORE, PRODUCTION, COMMERCIAL }

    private final String prefix;
    private final String label;
    private final Family family;

    DocumentType(String prefix, String label, Family family) {
        this.prefix = prefix;
        this.label = label;
        this.family = family;
    }

    public String prefix() { return prefix; }
    public String label()  { return label; }
    public Family family() { return family; }

    /** Documents that move fabric through weaving/dyeing rather than moving stock. */
    public boolean isFabricProcess() {
        return this == ROUT_CARD
            || this == WEAVING_WORK_ORDER
            || this == PROCESSING_WORK_ORDER
            || this == GREIGE_RECEIVE
            || this == GREIGE_ISSUE
            || this == FINISHED_FABRICS_RECEIVE;
    }

    /** Types that carry a revision lineage. Fabric sales documents are revised, not edited. */
    public boolean isRevisable() {
        return this == BOOKING
            || this == BULK_PRODUCTION_ORDER
            || this == REQUEST_FOR_PI
            || family == Family.COMMERCIAL;
    }
}

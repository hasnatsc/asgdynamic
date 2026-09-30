package com.asg.fabricerp.supply;

import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.Screen;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The purchase and store documents, and how each one is raised - the legacy Inventory and
 * Purchase menus in one table, read by {@link SupplyDocumentService}.
 *
 * <pre>
 *   Store requisition ─┬─► Purchase requisition ─► Purchase order ─► MRR ─► Purchase return
 *                      └─► Material issue
 *   Direct receive · Stock adjustment
 *   Transfer request ─► Transfer issue ─► Transfer receive          (items)
 *   Fabric transfer issue ─► Fabric transfer receive                (fabric lots)
 * </pre>
 *
 * <p>A line raised against a parent draws that parent line's own stream (the stream is the
 * step's document type), capped at the parent line's quantity - an MRR cannot receive more than
 * was ordered, and an issue cannot give out more than was asked for. Purchase requisitions and
 * orders go through the approval matrix; MRRs and purchase returns are posted by the store in one
 * step. Every store document - requisition, issue, direct receive, transfer, adjustment - is
 * approved and then posted, as the fabric chain's store documents are: it moves stock (or, for a
 * requisition or transfer request, is released to the store) only when posted.
 */
public enum SupplyStep {

    SR(DocumentType.STORE_REQUISITION, null, false, "store-requisition", Screen.SR,
        "Store requisition", "Store requisitions", SignOff.APPROVAL_THEN_POSTING, Stock.NONE, Lines.ITEM),
    SPR(DocumentType.PURCHASE_REQUISITION, DocumentType.STORE_REQUISITION, true, "purchase-requisition", Screen.SPR,
        "Purchase requisition", "Purchase requisitions", SignOff.APPROVAL, Stock.NONE, Lines.ITEM),
    PO(DocumentType.PURCHASE_ORDER, DocumentType.PURCHASE_REQUISITION, true, "purchase-order", Screen.PO,
        "Purchase order", "Purchase orders", SignOff.APPROVAL, Stock.NONE, Lines.ITEM),
    MRR(DocumentType.GOODS_RECEIPT_NOTE, DocumentType.PURCHASE_ORDER, false, "mrr", Screen.MRR,
        "MRR", "Material receipts (MRR)", SignOff.POSTING, Stock.IN, Lines.ITEM),
    PRT(DocumentType.PURCHASE_RETURN, DocumentType.GOODS_RECEIPT_NOTE, false, "purchase-return", Screen.PRT,
        "Purchase return", "Purchase returns", SignOff.POSTING, Stock.OUT, Lines.ITEM),
    MI(DocumentType.MATERIAL_ISSUE, DocumentType.STORE_REQUISITION, true, "material-issue", Screen.MI,
        "Material issue", "Material issues", SignOff.APPROVAL_THEN_POSTING, Stock.OUT, Lines.ITEM),
    MR(DocumentType.MATERIAL_RECEIVE, null, true, "direct-receive", Screen.MR,
        "Direct receive", "Direct receives", SignOff.APPROVAL_THEN_POSTING, Stock.IN, Lines.ITEM),
    ST(DocumentType.STOCK_TRANSFER, null, true, "transfer-request", Screen.ST,
        "Transfer request", "Transfer requests", SignOff.APPROVAL_THEN_POSTING, Stock.NONE, Lines.ITEM),
    TI(DocumentType.TRANSFER_ISSUE, DocumentType.STOCK_TRANSFER, true, "transfer-issue", Screen.TI,
        "Transfer issue", "Transfer issues", SignOff.APPROVAL_THEN_POSTING, Stock.TRANSFER_OUT, Lines.ITEM),
    TRC(DocumentType.TRANSFER_RECEIVE, DocumentType.TRANSFER_ISSUE, false, "transfer-receive", Screen.TRC,
        "Transfer receive", "Transfer receives", SignOff.APPROVAL_THEN_POSTING, Stock.TRANSFER_IN, Lines.ITEM),
    SA(DocumentType.STOCK_ADJUSTMENT, null, true, "stock-adjustment", Screen.SA,
        "Stock adjustment", "Stock adjustments", SignOff.APPROVAL_THEN_POSTING, Stock.ADJUST, Lines.ITEM),
    FTI(DocumentType.FABRIC_TRANSFER_ISSUE, null, true, "fabric-transfer-issue", Screen.FTI,
        "Fabric transfer issue", "Fabric transfer issues", SignOff.APPROVAL_THEN_POSTING, Stock.TRANSFER_OUT, Lines.FABRIC_LOT),
    FTR(DocumentType.FABRIC_TRANSFER_RECEIVE, DocumentType.FABRIC_TRANSFER_ISSUE, false, "fabric-transfer-receive", Screen.FTR,
        "Fabric transfer receive", "Fabric transfer receives", SignOff.APPROVAL_THEN_POSTING, Stock.TRANSFER_IN, Lines.FABRIC_LOT);

    /** How a document of the step becomes binding. */
    public enum SignOff {
        /** Submitted and signed through the approval matrix; binding once approved. */
        APPROVAL,
        /** Posted by the store in one step; cancelled with exact reversing ledger rows. */
        POSTING,
        /**
         * Submitted and signed through the approval matrix, then posted by the store: it waits
         * {@link BusinessDocumentStatus#READY_TO_POST} and takes effect only when posted.
         */
        APPROVAL_THEN_POSTING
    }

    /** What the step does to stock. */
    public enum Stock { NONE, IN, OUT, TRANSFER_OUT, TRANSFER_IN, ADJUST }

    /** What a line names: an item (general stock), or a fabric lot (the order-wise fabric stock). */
    public enum Lines { ITEM, FABRIC_LOT }

    private static final Set<BusinessDocumentStatus> OPEN_PARENT = EnumSet.of(
        BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL);

    private final DocumentType type;
    private final DocumentType parentType;
    private final boolean direct;
    private final String slug;
    private final Screen screen;
    private final String label;
    private final String plural;
    private final SignOff signOff;
    private final Stock stock;
    private final Lines lines;

    SupplyStep(DocumentType type, DocumentType parentType, boolean direct, String slug, Screen screen, String label,
               String plural, SignOff signOff, Stock stock, Lines lines) {
        this.type = type;
        this.parentType = parentType;
        this.direct = direct;
        this.slug = slug;
        this.screen = screen;
        this.label = label;
        this.plural = plural;
        this.signOff = signOff;
        this.stock = stock;
        this.lines = lines;
    }

    public DocumentType type()       { return type; }
    /** The document its lines are raised against, or null when it is always raised directly. */
    public DocumentType parentType() { return parentType; }

    /**
     * Every document type its lines may be raised against: the parent type, and for a purchase
     * order an approved import PI too - an import is ordered from the supplier's PI.
     */
    public Set<DocumentType> parentTypes() {
        if (parentType == null) return Set.of();
        return this == PO ? Set.of(DocumentType.PURCHASE_REQUISITION, DocumentType.IMPORT_PROFORMA_INVOICE) : Set.of(parentType);
    }
    public String slug()             { return slug; }
    public Screen screen()           { return screen; }
    public String label()            { return label; }
    public String plural()           { return plural; }
    public SignOff signOff()         { return signOff; }
    public Stock stock()             { return stock; }
    public Lines lines()             { return lines; }
    /** Has a Post action: posted in one step, or posted once approved. */
    public boolean isPosted()        { return signOff != SignOff.APPROVAL; }
    /** Goes through the approval matrix before it is binding (or before it may be posted). */
    public boolean needsApproval()   { return signOff != SignOff.POSTING; }
    /** Writes the stock ledger when posted. */
    public boolean movesStock()      { return stock != Stock.NONE; }

    /** The status a document of the step is posted from: a draft, or one its approvers have signed. */
    public BusinessDocumentStatus postsFrom() {
        return signOff == SignOff.POSTING ? BusinessDocumentStatus.DRAFT : BusinessDocumentStatus.READY_TO_POST;
    }
    public boolean hasParent()       { return parentType != null; }

    /** Lines may name an item (or lot) straight away rather than a parent line. */
    public boolean allowsDirect()    { return direct || parentType == null; }

    /** Lines may only come from a parent: an MRR is always against a purchase order. */
    public boolean requiresParent()  { return parentType != null && !direct; }

    /** Transfers name the store the stock goes to as well as the one it leaves. */
    public boolean isTransfer() {
        return this == ST || this == TI || this == TRC || this == FTI || this == FTR;
    }

    /** Lines carry a price: what is bought, received, returned, or valued into stock. */
    public boolean isPriced() {
        return this == PO || this == MRR || this == PRT || this == MR || this == SA;
    }

    /** The header names a supplier. */
    public boolean namesSupplier() {
        return this == PO || this == MRR || this == PRT;
    }

    /** Every document of the step belongs to a store (the one that asks, receives, issues or sends). */
    public boolean needsStore() {
        return this != SPR && this != PO;
    }

    /** The stream this step draws on its parent's lines. */
    public String stream() {
        return type.name();
    }

    /** A parent in one of these states may be drawn on. */
    public boolean acceptsParentStatus(BusinessDocumentStatus status) {
        return OPEN_PARENT.contains(status);
    }

    /** The step's authority for one verb, e.g. SCREEN_MRR_CREATE. */
    public String authority(String verb) {
        return "SCREEN_" + screen.name() + "_" + verb;
    }

    public static SupplyStep ofSlug(String slug) {
        return Arrays.stream(values()).filter(s -> s.slug.equals(slug)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No such document screen: " + slug));
    }

    public static Optional<SupplyStep> of(DocumentType type) {
        return Arrays.stream(values()).filter(s -> s.type == type).findFirst();
    }

    /** The steps raised against {@code parent}, e.g. SPR and material issue against a store requisition. */
    public static java.util.List<SupplyStep> childrenOf(DocumentType parent) {
        return Arrays.stream(values()).filter(s -> s.parentTypes().contains(parent)).toList();
    }
}

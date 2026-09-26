package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.Screen;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The nine documents that carry an order from Booking to the buyer's gate, and how each one is
 * raised - the whole production chain in one table, read by {@link ChainDocumentService}.
 *
 * <pre>
 *   Booking ─► Production order ─┬─► Weaving WO ─► Greige receive ──────────► Greige store
 *                                ├─► Dyeing WO ─┬─► Greige issue (out of the greige store)
 *                                │              └─► Finished receive ────────► Finished store
 *                                └─► Delivery schedule ─► Delivery order ─► Fabrics delivery
 * </pre>
 *
 * <p>Each step draws its own <em>stream</em> of its parent's lines (the stream is the step's own
 * document type), so the Weaving WO, the Dyeing WO and the delivery schedule of one production
 * order line each see their own balance. Documents that commit the mill go through the approval
 * matrix; store documents record something that physically happened and are posted in one step.
 */
public enum ChainStep {

    BPO(DocumentType.BULK_PRODUCTION_ORDER, DocumentType.BOOKING, "bpo", Screen.BPO,
        "Production order", "Production orders", SignOff.APPROVAL, StockEffect.NONE),
    WWO(DocumentType.WEAVING_WORK_ORDER, DocumentType.BULK_PRODUCTION_ORDER, "weaving-wo", Screen.WWO,
        "Weaving work order", "Weaving work orders", SignOff.APPROVAL, StockEffect.NONE),
    PWO(DocumentType.PROCESSING_WORK_ORDER, DocumentType.BULK_PRODUCTION_ORDER, "processing-wo", Screen.PWO,
        "Dyeing work order", "Dyeing work orders", SignOff.APPROVAL, StockEffect.NONE),
    GR(DocumentType.GREIGE_RECEIVE, DocumentType.WEAVING_WORK_ORDER, "greige-receive", Screen.GR,
        "Greige receive", "Greige receipts", SignOff.POSTING, StockEffect.GREIGE_IN),
    GI(DocumentType.GREIGE_ISSUE, DocumentType.PROCESSING_WORK_ORDER, "greige-issue", Screen.GI,
        "Greige issue", "Greige issues", SignOff.POSTING, StockEffect.ISSUE),
    FFR(DocumentType.FINISHED_FABRICS_RECEIVE, DocumentType.PROCESSING_WORK_ORDER, "finished-receive", Screen.FFR,
        "Finished receive", "Finished receipts", SignOff.POSTING, StockEffect.FINISHED_IN),
    RPI(DocumentType.REQUEST_FOR_PI, DocumentType.BULK_PRODUCTION_ORDER, "requestforpi", Screen.RPI,
        "Delivery schedule", "Delivery schedules", SignOff.APPROVAL, StockEffect.NONE),
    DO(DocumentType.DELIVERY_ORDER, DocumentType.REQUEST_FOR_PI, "delivery-order", Screen.DO,
        "Delivery order", "Delivery orders", SignOff.APPROVAL, StockEffect.RESERVE),
    FD(DocumentType.FABRICS_DELIVERY, DocumentType.DELIVERY_ORDER, "fabrics-delivery", Screen.FD,
        "Fabrics delivery", "Fabrics deliveries", SignOff.POSTING, StockEffect.DELIVERY_OUT);

    /** How a document of the step becomes binding. */
    public enum SignOff {
        /** Submitted and signed through the approval matrix. */
        APPROVAL,
        /** Posted by the store in one step; cancelled with exact reversing ledger rows. */
        POSTING
    }

    /** What the step does to the fabric stock ledger. */
    public enum StockEffect { NONE, GREIGE_IN, ISSUE, FINISHED_IN, RESERVE, DELIVERY_OUT }

    /** The stream a rework Dyeing WO draws on its production order line - apart from the order's own dyeing. */
    public static final String REWORK_STREAM = "REWORK";

    private static final Set<BusinessDocumentStatus> OPEN_PARENT = EnumSet.of(
        BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL);

    private final DocumentType type;
    private final DocumentType parentType;
    private final String slug;
    private final Screen screen;
    private final String label;
    private final String plural;
    private final SignOff signOff;
    private final StockEffect stock;

    ChainStep(DocumentType type, DocumentType parentType, String slug, Screen screen, String label, String plural,
              SignOff signOff, StockEffect stock) {
        this.type = type;
        this.parentType = parentType;
        this.slug = slug;
        this.screen = screen;
        this.label = label;
        this.plural = plural;
        this.signOff = signOff;
        this.stock = stock;
    }

    public DocumentType type()       { return type; }
    public DocumentType parentType() { return parentType; }
    public String slug()             { return slug; }
    public Screen screen()           { return screen; }
    public String label()            { return label; }
    public String plural()           { return plural; }
    public SignOff signOff()         { return signOff; }
    public StockEffect stock()       { return stock; }
    public boolean isPosting()       { return signOff == SignOff.POSTING; }
    public boolean isRevisable()     { return type.isRevisable(); }

    /** The stream this step draws on its parent's lines. */
    public String stream() {
        return type.name();
    }

    /** A parent in one of these states may be drawn on. */
    public boolean acceptsParentStatus(BusinessDocumentStatus status) {
        return OPEN_PARENT.contains(status);
    }

    /** The step's name the header's authorities use, e.g. SCREEN_GR_CREATE. */
    public String authority(String verb) {
        return "SCREEN_" + screen.name() + "_" + verb;
    }

    public static ChainStep ofSlug(String slug) {
        return Arrays.stream(values()).filter(s -> s.slug.equals(slug)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No such document screen: " + slug));
    }

    public static Optional<ChainStep> of(DocumentType type) {
        return Arrays.stream(values()).filter(s -> s.type == type).findFirst();
    }

    /**
     * The one child stream a parent line's {@code fulfilled_quantity} mirrors - what "fulfilled"
     * means on that document: a Booking line is fulfilled by production orders, a production order
     * line by its delivery schedules, a work order line by its receipts, and so on.
     */
    public static Optional<ChainStep> principalChildOf(DocumentType parent) {
        return Optional.ofNullable(switch (parent) {
            case BOOKING -> BPO;
            case BULK_PRODUCTION_ORDER -> RPI;
            case WEAVING_WORK_ORDER -> GR;
            case PROCESSING_WORK_ORDER -> FFR;
            case REQUEST_FOR_PI -> DO;
            case DELIVERY_ORDER -> FD;
            default -> null;
        });
    }
}

package com.asg.fabricerp.commercial;

import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.global.terms.ConditionType;
import com.asg.fabricerp.security.Screen;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The commercial documents and how each is raised - the legacy Commercial menu in one table, read
 * by {@link CommercialDocumentService}.
 *
 * <pre>
 *   Export   Delivery schedule ─► Export PI ─► Export LC ─► Export CI (regular: from delivery challans;
 *                                                                     advance: from LC lines)
 *   Import   Purchase requisition (or items) ─► Import PI ─┬─► Import LC
 *                                                          └─► Purchase order ─► MRR (landed cost)
 * </pre>
 *
 * <p>A line draws its parent line's balance, capped at the parent line's quantity: a PI cannot offer
 * more than was scheduled, an LC cannot open more than the PIs offered, a CI cannot invoice more than
 * the LC covers - nor a delivery challan twice. Everything here is approved; PIs and LCs are amended
 * by revision.
 */
public enum CommercialStep {

    EPI(DocumentType.EXPORT_PROFORMA_INVOICE, DocumentType.REQUEST_FOR_PI, false, true, "export-pi", Screen.EPI,
        "Export PI", "Export PIs", true, ConditionType.PI),
    ELC(DocumentType.EXPORT_LETTER_OF_CREDIT, DocumentType.EXPORT_PROFORMA_INVOICE, false, true, "export-lc", Screen.ELC,
        "Export LC", "Export LCs", true, ConditionType.LC),
    ECI(DocumentType.EXPORT_COMMERCIAL_INVOICE, DocumentType.EXPORT_LETTER_OF_CREDIT, false, false, "export-ci", Screen.ECI,
        "Export CI", "Export CIs", false, ConditionType.CI),
    IPI(DocumentType.IMPORT_PROFORMA_INVOICE, DocumentType.PURCHASE_REQUISITION, true, false, "import-pi", Screen.IPI,
        "Import PI", "Import PIs", true, ConditionType.PI),
    ILC(DocumentType.IMPORT_LETTER_OF_CREDIT, DocumentType.IMPORT_PROFORMA_INVOICE, false, true, "import-lc", Screen.ILC,
        "Import LC", "Import LCs", true, ConditionType.LC);

    private static final Set<BusinessDocumentStatus> OPEN_PARENT = EnumSet.of(
        BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL);

    private final DocumentType type;
    private final DocumentType parentType;
    private final boolean direct;
    private final boolean manyParents;
    private final String slug;
    private final Screen screen;
    private final String label;
    private final String plural;
    private final boolean revisable;
    private final ConditionType terms;

    CommercialStep(DocumentType type, DocumentType parentType, boolean direct, boolean manyParents, String slug, Screen screen,
                   String label, String plural, boolean revisable, ConditionType terms) {
        this.type = type;
        this.parentType = parentType;
        this.direct = direct;
        this.manyParents = manyParents;
        this.slug = slug;
        this.screen = screen;
        this.label = label;
        this.plural = plural;
        this.revisable = revisable;
        this.terms = terms;
    }

    public DocumentType type()        { return type; }
    public DocumentType parentType()  { return parentType; }
    /** Lines may name items directly (an import PI for items no requisition asked for). */
    public boolean allowsDirect()     { return direct; }
    /** Lines may come from several parent documents - of one buyer or supplier. */
    public boolean manyParents()      { return manyParents; }
    public String slug()              { return slug; }
    public Screen screen()            { return screen; }
    public String label()             { return label; }
    public String plural()            { return plural; }
    /** Amended by raising a revision once approved. A CI is cancelled and raised again instead. */
    public boolean isRevisable()      { return revisable; }
    /** Which clauses of the terms library it carries. */
    public ConditionType conditionType() { return terms; }
    public boolean isExport()         { return this == EPI || this == ELC || this == ECI; }
    public boolean isLc()             { return this == ELC || this == ILC; }

    /**
     * The stream it draws on its parent's lines: its own type - except an import PI, which draws a
     * requisition's "ordered" balance, the one a local purchase order draws, so a requisition line
     * is never bought twice.
     */
    public String stream() {
        return this == IPI ? DocumentType.PURCHASE_ORDER.name() : type.name();
    }

    public boolean acceptsParentStatus(BusinessDocumentStatus status) {
        return OPEN_PARENT.contains(status);
    }

    public String authority(String verb) {
        return "SCREEN_" + screen.name() + "_" + verb;
    }

    public static CommercialStep ofSlug(String slug) {
        return Arrays.stream(values()).filter(s -> s.slug.equals(slug)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No such document screen: " + slug));
    }

    public static Optional<CommercialStep> of(DocumentType type) {
        return Arrays.stream(values()).filter(s -> s.type == type).findFirst();
    }
}

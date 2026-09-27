package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.production.LineDrawLedger;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Drawing and giving back a purchase or store document's lines on their parent lines' streams,
 * through the same {@link LineDrawLedger} the production chain uses: one atomic counter per parent
 * line and child type, so two MRRs against the last hundred kilos of one order line cannot both
 * be saved.
 */
@Component
public class SupplyDraws {

    private final LineDrawLedger ledger;
    private final OrgContext context;

    public SupplyDraws(LineDrawLedger ledger, OrgContext context) {
        this.ledger = ledger;
        this.context = context;
    }

    public LineDrawLedger ledger() {
        return ledger;
    }

    /** Every live document holds its draws - drafts included, so a draft MRR keeps what it will receive. */
    public boolean holdsDraws(BusinessDocument doc) {
        return !Boolean.TRUE.equals(doc.getDeleted()) && doc.getStatus() != BusinessDocumentStatus.CANCELLED;
    }

    /** What a line holds on its parent: its quantity, less any balance it gave back when short-closed. */
    public static BigDecimal held(BusinessDocumentColorLine line) {
        return line.getQuantity().subtract(line.getShortClosedQuantity()).max(BigDecimal.ZERO);
    }

    /** How much of a parent line a child may draw: the parent line's own quantity. */
    public static BigDecimal cap(BusinessDocumentColorLine parentLine) {
        return parentLine.getQuantity();
    }

    public void drawAll(SupplyStep step, BusinessDocument doc) {
        Long orgId = context.requireOrganizationId();
        for (BusinessDocumentColorLine line : lines(doc)) {
            BusinessDocumentColorLine source = line.getSourceColorLine();
            if (source == null) continue;
            ledger.draw(orgId, SourceKind.COLOUR, source.getId(), step.stream(), held(line), cap(source),
                step.parentType(), () -> describe(source));
        }
    }

    public void releaseAll(SupplyStep step, BusinessDocument doc) {
        for (BusinessDocumentColorLine line : lines(doc)) {
            BusinessDocumentColorLine source = line.getSourceColorLine();
            if (source == null) continue;
            ledger.release(SourceKind.COLOUR, source.getId(), step.stream(), held(line), step.parentType());
        }
    }

    /** Every stream drawn on each of a document's lines: line id -> child type -> total. */
    public Map<Long, Map<String, BigDecimal>> streams(BusinessDocument doc) {
        return ledger.streams(SourceKind.COLOUR, lines(doc).stream().map(BusinessDocumentColorLine::getId).toList());
    }

    /** "Cotton yarn 30/1 on PO-2026-000012" - names a parent line in a refusal. */
    public static String describe(BusinessDocumentColorLine line) {
        return "%s on %s".formatted(lineName(line), line.getLineGroup().getDocument().getDocumentNo());
    }

    /** A line as people read it: its item, else its fabric colour, else its number. */
    public static String lineName(BusinessDocumentColorLine line) {
        BusinessDocumentLineGroup g = line.getLineGroup();
        if (g != null && g.getItem() != null) return g.getItem().getName();
        if (line.getColorName() != null) {
            String construction = g == null ? null : g.getFabric().getConstruction();
            return construction == null ? line.getColorName() : construction + " " + line.getColorName();
        }
        return "Line " + (g == null ? line.getColorLineNo() : g.getGroupNo());
    }

    public static List<BusinessDocumentColorLine> lines(BusinessDocument doc) {
        return doc.getLineGroups().stream().flatMap(g -> g.getColorLines().stream()).toList();
    }
}

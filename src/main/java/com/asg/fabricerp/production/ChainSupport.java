package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.global.documents.ProcessKind;
import com.asg.fabricerp.global.documents.RouteSnapshot;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.asg.fabricerp.production.LineDrawLedger.plain;

/**
 * The chain's shared mechanics: where a line comes from, which production order it belongs to,
 * and drawing or releasing a whole document's lines on their parents' streams.
 */
@Component
public class ChainSupport {

    private final LineDrawLedger ledger;
    private final OrgContext context;

    public ChainSupport(LineDrawLedger ledger, OrgContext context) {
        this.ledger = ledger;
        this.context = context;
    }

    public LineDrawLedger ledger() {
        return ledger;
    }

    // ------------------------------------------------------------------------------- lineage

    /** The production order, fabric line and (when there is one) colour line a chain line belongs to. */
    public record Anchor(BusinessDocument bpo, BusinessDocumentLineGroup group, BusinessDocumentColorLine line) {
        public RouteSnapshot route() {
            return group.getRoute();
        }
    }

    /** Walks up the chain from any line to its production order line. */
    public Anchor anchor(BusinessDocumentColorLine line) {
        BusinessDocumentColorLine current = line;
        for (int hop = 0; hop < 6 && current != null; hop++) {
            BusinessDocumentLineGroup group = current.getLineGroup();
            if (group.getDocument().getDocumentType() == DocumentType.BULK_PRODUCTION_ORDER) {
                return new Anchor(group.getDocument(), group, current);
            }
            if (current.getSourceLineGroup() != null) {
                BusinessDocumentLineGroup bpoGroup = current.getSourceLineGroup();
                return new Anchor(bpoGroup.getDocument(), bpoGroup, null);
            }
            current = current.getSourceColorLine();
        }
        throw new IllegalStateException("Line %s does not trace back to a production order".formatted(line.getId()));
    }

    public SourceKind sourceKind(BusinessDocumentColorLine line) {
        if (line.getSourceLineGroup() != null) return SourceKind.GROUP;
        return line.getSourceColorLine() != null ? SourceKind.COLOUR : null;
    }

    public Long sourceId(BusinessDocumentColorLine line) {
        if (line.getSourceLineGroup() != null) return line.getSourceLineGroup().getId();
        return line.getSourceColorLine() == null ? null : line.getSourceColorLine().getId();
    }

    /** The parent fabric line a line draws from (its source colour line's group, or the group itself). */
    public BusinessDocumentLineGroup sourceGroup(BusinessDocumentColorLine line) {
        if (line.getSourceLineGroup() != null) return line.getSourceLineGroup();
        return line.getSourceColorLine() == null ? null : line.getSourceColorLine().getLineGroup();
    }

    /** The source's quantity: a colour line's own, or a whole fabric line's for construction-keyed weaving. */
    public BigDecimal sourceQuantity(BusinessDocumentColorLine line) {
        if (line.getSourceLineGroup() != null) return line.getSourceLineGroup().groupQuantity();
        return line.getSourceColorLine() == null ? BigDecimal.ZERO : line.getSourceColorLine().getQuantity();
    }

    /** "Navy on BPO-2026-000014" / "Fabric line 2 on BPO-2026-000014" - names a parent line in a message. */
    public String describe(BusinessDocumentColorLine line) {
        BusinessDocumentLineGroup group = sourceGroup(line);
        String doc = group == null ? "?" : group.getDocument().getDocumentNo();
        if (line.getSourceLineGroup() != null) {
            String construction = group.getFabric().getConstruction();
            return "Fabric line %d%s on %s".formatted(group.getGroupNo(),
                construction == null ? "" : " (" + construction + ")", doc);
        }
        BusinessDocumentColorLine source = line.getSourceColorLine();
        String name = source.getColorName() != null ? source.getColorName()
            : source.getColorCode() != null ? source.getColorCode() : "Colour " + source.getColorLineNo();
        return "%s on %s".formatted(name, doc);
    }

    // --------------------------------------------------------------------------------- draws

    /**
     * Whether a document's lines hold draws on their parents: every live version does, except a
     * revision still awaiting approval - its predecessor holds them until the revision takes over.
     */
    public boolean holdsDraws(BusinessDocument doc) {
        if (Boolean.TRUE.equals(doc.getDeleted()) || doc.getStatus() == BusinessDocumentStatus.CANCELLED) return false;
        return !(doc.getRevisionNo() != null && doc.getRevisionNo() > 0 && !doc.getStatus().isCommitted());
    }

    /**
     * What a line holds on its parent's stream: its quantity, less any balance it gave back when it
     * was short-closed (a production order's short-close gives nothing back to its Booking).
     */
    public BigDecimal held(ChainStep step, BusinessDocumentColorLine line) {
        BigDecimal qty = line.getQuantity();
        return step == ChainStep.BPO ? qty : qty.subtract(line.getShortClosedQuantity()).max(BigDecimal.ZERO);
    }

    public BigDecimal cap(ChainStep step, ProcessKind kind, BusinessDocumentColorLine line) {
        BusinessDocumentLineGroup group = sourceGroup(line);
        BigDecimal issued = step == ChainStep.FFR
            ? ledger.drawn(SourceKind.COLOUR, sourceId(line), ChainStep.GI.stream()) : null;
        return DrawCaps.cap(step, kind, sourceQuantity(line), group == null ? null : group.getRoute(), issued);
    }

    /** Draws every line of {@code doc} on its parent line's stream, refusing any that would pass its cap. */
    public void drawAll(ChainStep step, BusinessDocument doc) {
        String stream = DrawCaps.stream(step, doc.getProcessKind());
        Long orgId = context.requireOrganizationId();
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                SourceKind kind = sourceKind(line);
                if (kind == null) continue;
                BusinessDocumentColorLine l = line;
                ledger.draw(orgId, kind, sourceId(line), stream, held(step, line), cap(step, doc.getProcessKind(), line),
                    step.parentType(), () -> describe(l));
            }
        }
    }

    /** Gives back everything {@code doc}'s lines hold on their parents. */
    public void releaseAll(ChainStep step, BusinessDocument doc) {
        String stream = DrawCaps.stream(step, doc.getProcessKind());
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                SourceKind kind = sourceKind(line);
                if (kind == null) continue;
                ledger.release(kind, sourceId(line), stream, held(step, line), step.parentType());
            }
        }
    }

    public static String qty(BigDecimal v) {
        return plain(v);
    }
}

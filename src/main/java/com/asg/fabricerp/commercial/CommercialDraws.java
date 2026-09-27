package com.asg.fabricerp.commercial;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.production.LineDrawLedger;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import com.asg.fabricerp.supply.SupplyDraws;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Drawing and giving back a commercial document's lines on the shared {@link LineDrawLedger}: each
 * line on its parent line (the schedule, PI or LC line, capped at its quantity), and a regular CI
 * line also on the delivery challan line it invoices - so no challan is invoiced twice, whichever
 * LC it is invoiced under.
 */
@Component
public class CommercialDraws {

    private final LineDrawLedger ledger;
    private final OrgContext context;

    public CommercialDraws(LineDrawLedger ledger, OrgContext context) {
        this.ledger = ledger;
        this.context = context;
    }

    public LineDrawLedger ledger() {
        return ledger;
    }

    /**
     * Whether a document's lines hold draws: every live version does, except an amendment still
     * awaiting approval - the version it amends holds them until the amendment takes over.
     */
    public boolean holdsDraws(BusinessDocument doc) {
        if (Boolean.TRUE.equals(doc.getDeleted()) || doc.getStatus() == BusinessDocumentStatus.CANCELLED) return false;
        return !(doc.getRevisionNo() != null && doc.getRevisionNo() > 0 && !doc.getStatus().isCommitted());
    }

    public void drawAll(CommercialStep step, BusinessDocument doc) {
        Long orgId = context.requireOrganizationId();
        for (BusinessDocumentColorLine line : SupplyDraws.lines(doc)) {
            BusinessDocumentColorLine source = line.getSourceColorLine();
            BigDecimal held = SupplyDraws.held(line);
            if (source != null) {
                ledger.draw(orgId, SourceKind.COLOUR, source.getId(), step.stream(), held, source.getQuantity(),
                    SupplyDraws.typeOf(source), () -> SupplyDraws.describe(source));
            }
            BusinessDocumentColorLine challan = line.getDeliveryLine();
            if (challan != null) {
                ledger.draw(orgId, SourceKind.COLOUR, challan.getId(), step.stream(), held, challan.getQuantity(),
                    SupplyDraws.typeOf(challan), () -> "Challan %s, %s".formatted(challan.getLineGroup().getDocument().getDocumentNo(),
                        SupplyDraws.lineName(challan)));
            }
        }
    }

    public void releaseAll(CommercialStep step, BusinessDocument doc) {
        for (BusinessDocumentColorLine line : SupplyDraws.lines(doc)) {
            BigDecimal held = SupplyDraws.held(line);
            if (line.getSourceColorLine() != null) {
                ledger.release(SourceKind.COLOUR, line.getSourceColorLine().getId(), step.stream(), held,
                    SupplyDraws.typeOf(line.getSourceColorLine()));
            }
            if (line.getDeliveryLine() != null) {
                ledger.release(SourceKind.COLOUR, line.getDeliveryLine().getId(), step.stream(), held,
                    SupplyDraws.typeOf(line.getDeliveryLine()));
            }
        }
    }

    /** Every stream drawn on each of a document's lines: line id -> stream -> total. */
    public Map<Long, Map<String, BigDecimal>> streams(BusinessDocument doc) {
        return ledger.streams(SourceKind.COLOUR, SupplyDraws.lines(doc).stream().map(BusinessDocumentColorLine::getId).toList());
    }
}

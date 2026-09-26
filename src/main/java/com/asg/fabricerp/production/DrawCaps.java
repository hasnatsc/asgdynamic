package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.ProcessKind;
import com.asg.fabricerp.global.documents.RouteSnapshot;

import java.math.BigDecimal;

/**
 * How much each step may draw from one parent line - the design's quantity-control table, and
 * nothing else, so it can be read and tested on its own.
 *
 * <pre>
 *   Parent line            Drawn by            May draw up to
 *   Booking line           Production order    booked quantity
 *   Production order line  Weaving WO          greige: finished × (1 + allowance)
 *   Production order line  Dyeing WO           finished quantity
 *   Production order line  Delivery schedule   finished × (1 + delivery tolerance)
 *   Weaving WO line        Greige receive      ordered × (1 + receive tolerance)
 *   Dyeing WO line         Greige issue        greige planned for the batch × (1 + 5 %)
 *   Dyeing WO line         Finished receive    greige actually issued to it
 *   Delivery schedule line Delivery order      scheduled quantity (the schedule already carries the tolerance)
 *   Delivery order line    Fabrics delivery    ordered quantity
 * </pre>
 */
public final class DrawCaps {

    /** Greige may be issued to a batch up to this much over what was planned for it. */
    public static final BigDecimal ISSUE_TOLERANCE_PCT = BigDecimal.valueOf(5);

    private DrawCaps() { }

    /**
     * @param step          the step drawing
     * @param kind          the drawing document's process kind (Dyeing WO and its issues), or null
     * @param parentQty     the parent line's quantity (for a construction-keyed Weaving WO, its fabric line's)
     * @param route         the route on the parent's fabric line
     * @param issued        Finished receive only: greige issued to the Dyeing WO line so far
     */
    public static BigDecimal cap(ChainStep step, ProcessKind kind, BigDecimal parentQty, RouteSnapshot route,
                                 BigDecimal issued) {
        RouteSnapshot r = route == null ? new RouteSnapshot() : route;
        BigDecimal qty = parentQty == null ? BigDecimal.ZERO : parentQty;
        return switch (step) {
            case BPO, PWO, DO, FD -> qty;
            case WWO -> r.greigeFor(qty);
            case RPI -> RouteSnapshot.plus(qty, r.getDeliveryTolerancePct());
            case GR -> RouteSnapshot.plus(qty, r.getReceiveTolerancePct());
            // Rework issues finished B-grade cloth, one for one; a batch issues greige grossed up for loss.
            case GI -> RouteSnapshot.plus(kind == ProcessKind.REWORK ? qty : r.greigeFor(qty), ISSUE_TOLERANCE_PCT);
            case FFR -> issued == null ? BigDecimal.ZERO : issued;
        };
    }

    /**
     * The stream a document draws on: its own type, except a rework Dyeing WO, which re-dyes cloth
     * already made and so must not use up the order's own dyeing balance.
     */
    public static String stream(ChainStep step, ProcessKind kind) {
        return step == ChainStep.PWO && kind == ProcessKind.REWORK ? ChainStep.REWORK_STREAM : step.stream();
    }
}

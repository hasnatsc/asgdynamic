package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The design's quantity-control table, one row per assertion. */
class DrawCapsTest {

    private static final RouteSnapshot PIECE_DYED = new RouteSnapshot(RouteCode.PIECE_DYED, true, ProcessKind.DYE, YarnPrep.NONE,
        GreigeKey.CONSTRUCTION, DeliverStage.FINISHED, new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("3"));

    private static BigDecimal cap(ChainStep step, String qty) {
        return DrawCaps.cap(step, null, new BigDecimal(qty), PIECE_DYED, null);
    }

    @Test
    void eachStepHasItsOwnCeiling() {
        assertThat(cap(ChainStep.BPO, "1000")).isEqualByComparingTo("1000");     // booked quantity
        assertThat(cap(ChainStep.WWO, "10000")).isEqualByComparingTo("11000");   // greige: finished + 10 % allowance
        assertThat(cap(ChainStep.PWO, "1000")).isEqualByComparingTo("1000");     // finished quantity
        assertThat(cap(ChainStep.RPI, "1000")).isEqualByComparingTo("1030");     // + 3 % delivery tolerance
        assertThat(cap(ChainStep.GR, "1000")).isEqualByComparingTo("1050");      // + 5 % over-receipt
        assertThat(cap(ChainStep.GI, "1000")).isEqualByComparingTo("1155");      // planned greige 1,100 + 5 %
        assertThat(cap(ChainStep.DO, "1000")).isEqualByComparingTo("1000");      // the schedule already carries the tolerance
        assertThat(cap(ChainStep.FD, "1000")).isEqualByComparingTo("1000");      // the delivery order's quantity
    }

    @Test
    void finishedReceiveIsCappedByWhatWasIssued() {
        assertThat(DrawCaps.cap(ChainStep.FFR, null, new BigDecimal("1000"), PIECE_DYED, new BigDecimal("640")))
            .isEqualByComparingTo("640");
        assertThat(DrawCaps.cap(ChainStep.FFR, null, new BigDecimal("1000"), PIECE_DYED, null)).isZero();
    }

    @Test
    void reworkIssuesFinishedClothOneForOne_andDrawsItsOwnStream() {
        assertThat(DrawCaps.cap(ChainStep.GI, ProcessKind.REWORK, new BigDecimal("200"), PIECE_DYED, null))
            .isEqualByComparingTo("210");
        assertThat(DrawCaps.stream(ChainStep.PWO, ProcessKind.REWORK)).isEqualTo(ChainStep.REWORK_STREAM);
        assertThat(DrawCaps.stream(ChainStep.PWO, ProcessKind.DYE)).isEqualTo("PROCESSING_WORK_ORDER");
        assertThat(DrawCaps.stream(ChainStep.GI, ProcessKind.REWORK)).isEqualTo("GREIGE_ISSUE");
    }

    @Test
    void aLineWithoutARouteHasNoAllowance() {
        assertThat(DrawCaps.cap(ChainStep.WWO, null, new BigDecimal("500"), null, null)).isEqualByComparingTo("500");
    }

    @Test
    void theChainIsOneTable() {
        assertThat(ChainStep.ofSlug("greige-issue")).isEqualTo(ChainStep.GI);
        assertThat(ChainStep.GI.parentType()).isEqualTo(DocumentType.PROCESSING_WORK_ORDER);
        assertThat(ChainStep.GR.parentType()).isEqualTo(DocumentType.WEAVING_WORK_ORDER);
        assertThat(ChainStep.FD.isPosting()).isTrue();
        assertThat(ChainStep.DO.isPosting()).isFalse();
        assertThat(ChainStep.GI.authority("CREATE")).isEqualTo("SCREEN_GI_CREATE");
        assertThat(ChainStep.principalChildOf(DocumentType.BOOKING)).contains(ChainStep.BPO);
        assertThat(ChainStep.principalChildOf(DocumentType.WEAVING_WORK_ORDER)).contains(ChainStep.GR);
        assertThat(ChainStep.principalChildOf(DocumentType.FABRICS_DELIVERY)).isEmpty();
        // Every chain type has a screen whose authorities the document type names the same way.
        for (ChainStep s : ChainStep.values()) {
            assertThat(s.type().createAuthority()).isEqualTo(s.authority("CREATE"));
        }
    }

    @Test
    void aPlannerMayChangeTheAllowanceWithinReason() {
        RouteSnapshot r = PIECE_DYED.copy();
        r.setGreigeAllowancePct(new BigDecimal("12.5"));
        assertThat(r.greigeFor(new BigDecimal("800"))).isEqualByComparingTo("900");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> r.setGreigeAllowancePct(new BigDecimal("150")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

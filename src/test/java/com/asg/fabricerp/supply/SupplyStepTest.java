package com.asg.fabricerp.supply;

import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.production.ChainStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** The step table and the costing rules, on their own. */
class SupplyStepTest {

    @ParameterizedTest
    @EnumSource(SupplyStep.class)
    void eachStepsScreenAuthorityAndPathAgree(SupplyStep step) {
        // The approval engine finds a type's screen by its role root; the menu links the screen's path.
        assertThat(step.type().roleRoot()).isEqualTo(step.screen().name());
        assertThat(step.screen().path()).isEqualTo("/" + step.slug());
        assertThat(step.authority("VIEW")).isEqualTo("SCREEN_" + step.screen().name() + "_VIEW");
        assertThat(SupplyStep.ofSlug(step.slug())).isSameAs(step);
    }

    @ParameterizedTest
    @EnumSource(SupplyStep.class)
    void aParentIsAnotherSupplyStep(SupplyStep step) {
        if (step.hasParent()) assertThat(SupplyStep.of(step.parentType())).isPresent();
        // A step always raised against a parent has somewhere to take its lines from.
        assertThat(step.allowsDirect() || step.hasParent()).isTrue();
    }

    @Test
    void whatFulfilsALineIsAStepRaisedAgainstIt() {
        for (DocumentType type : DocumentType.values()) {
            DocumentType child = type.fulfilledBy();
            if (child == null) continue;
            boolean supply = SupplyStep.of(child).map(s -> s.parentTypes().contains(type)).orElse(false);
            boolean chain = ChainStep.of(child).map(s -> s.parentType() == type).orElse(false);
            boolean commercial = com.asg.fabricerp.commercial.CommercialStep.of(child).map(s -> s.parentType() == type).orElse(false);
            assertThat(supply || chain || commercial).as("%s is fulfilled by %s, which is not raised against it", type, child).isTrue();
        }
        assertThat(DocumentType.PURCHASE_ORDER.fulfilledBy()).isEqualTo(DocumentType.GOODS_RECEIPT_NOTE);
        assertThat(DocumentType.GOODS_RECEIPT_NOTE.fulfilledBy()).isNull();
        // An import PI is ordered on a purchase order, as a requisition is.
        assertThat(DocumentType.IMPORT_PROFORMA_INVOICE.fulfilledBy()).isEqualTo(DocumentType.PURCHASE_ORDER);
        assertThat(SupplyStep.PO.parentTypes()).contains(DocumentType.IMPORT_PROFORMA_INVOICE);
        // The chain's own mapping is unchanged by the move into DocumentType.
        assertThat(ChainStep.principalChildOf(DocumentType.BOOKING)).contains(ChainStep.BPO);
        assertThat(ChainStep.principalChildOf(DocumentType.DELIVERY_ORDER)).contains(ChainStep.FD);
    }

    @Test
    void storeDocumentsAreApprovedThenPostedAndPurchasesApproved() {
        // MRRs and purchase returns are posted in one step; purchase requisitions and orders are only approved.
        assertThat(Arrays.stream(SupplyStep.values()).filter(s -> !s.needsApproval()))
            .containsExactlyInAnyOrder(SupplyStep.MRR, SupplyStep.PRT);
        assertThat(Arrays.stream(SupplyStep.values()).filter(s -> !s.isPosted()))
            .containsExactlyInAnyOrder(SupplyStep.SPR, SupplyStep.PO);
        // Every store document is approved, then posted - and the approval engine agrees which those are.
        assertThat(Arrays.stream(SupplyStep.values()).filter(s -> s.type().isPostedAfterApproval()))
            .containsExactlyInAnyOrder(SupplyStep.SR, SupplyStep.MI, SupplyStep.MR, SupplyStep.ST, SupplyStep.TI,
                SupplyStep.TRC, SupplyStep.SA, SupplyStep.FTI, SupplyStep.FTR);
        for (SupplyStep s : SupplyStep.values()) {
            assertThat(s.type().isPostedAfterApproval()).as(s.name()).isEqualTo(s.needsApproval() && s.isPosted());
        }
        assertThat(SupplyStep.MRR.postsFrom()).isEqualTo(BusinessDocumentStatus.DRAFT);
        assertThat(SupplyStep.MI.postsFrom()).isEqualTo(BusinessDocumentStatus.READY_TO_POST);
        assertThat(SupplyStep.MRR.requiresParent()).isTrue();
        assertThat(SupplyStep.PO.allowsDirect()).isTrue();
        assertThat(SupplyStep.childrenOf(DocumentType.STORE_REQUISITION)).containsExactly(SupplyStep.SPR, SupplyStep.MI);
    }

    @Test
    void goingOutTakesTheAverageAndTheLastUnitTakesWhatIsLeft() {
        ItemStockService.Balance b = new ItemStockService.Balance(new BigDecimal("3"), new BigDecimal("10"));
        assertThat(b.averageCost()).isEqualByComparingTo("3.333333");
        assertThat(ItemStockService.outValue(b, new BigDecimal("1"))).isEqualByComparingTo("3.333333");
        // Emptying the store takes all of its value - no stray paisa left on zero stock.
        assertThat(ItemStockService.outValue(b, new BigDecimal("3"))).isEqualByComparingTo("10");
        // Never more than the store is worth, however the average rounds.
        ItemStockService.Balance tiny = new ItemStockService.Balance(new BigDecimal("3"), new BigDecimal("0.000001"));
        assertThat(ItemStockService.outValue(tiny, new BigDecimal("2"))).isLessThanOrEqualTo(new BigDecimal("0.000001"));
    }
}

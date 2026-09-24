package com.asg.fabricerp.global.documents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Covers the rules that move quantity and money.
 *
 * <p>SpindleERP has ~40 tests on its AI layer and three on its transactional core, so the
 * guards that matter most commercially are the least covered. These are the equivalent
 * guards here, written alongside the model rather than after an incident.
 *
 * <p>The totals tests below build the two-level shape a real Booking actually has — one
 * fabric-spec group with several colours under it — rather than one colour per group,
 * because a flat one-colour-per-group model is exactly what an earlier version of this
 * class got wrong (see {@link FabricSpec}'s javadoc).
 */
class BusinessDocumentTest {

    private BusinessDocument booking() {
        BusinessDocument doc = new BusinessDocument();
        doc.setOrganizationId(1L);
        doc.setBusinessUnitId(10L);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKAF000001");
        doc.setDocumentDate(LocalDate.of(2026, 9, 24));
        return doc;
    }

    private BusinessDocumentColorLine colorLine(String qty, String rate) {
        BusinessDocumentColorLine l = new BusinessDocumentColorLine();
        l.setQuantity(new BigDecimal(qty));
        l.setRate(new BigDecimal(rate));
        return l;
    }

    private BusinessDocumentLineGroup groupOf(BusinessDocumentColorLine... colorLines) {
        BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
        for (BusinessDocumentColorLine l : colorLines) g.addColorLine(l);
        return g;
    }

    @Nested
    @DisplayName("totals")
    class Totals {

        @Test
        void rollUpThroughColourLinesAndGroupsNotFromTheClient() {
            BusinessDocument doc = booking();
            // One construction, two colours — the shape the real Booking payload showed.
            doc.addLineGroup(groupOf(colorLine("100", "2.50"), colorLine("40", "2.50")));

            doc.recalculateTotals();

            // (100 + 40) * 2.50 = 350
            assertThat(doc.getSubtotalAmount()).isEqualByComparingTo("350");
            assertThat(doc.getTotalQuantity()).isEqualByComparingTo("140");
        }

        @Test
        void sumAcrossMultipleGroupsToo() {
            BusinessDocument doc = booking();
            doc.addLineGroup(groupOf(colorLine("100", "2.50")));  // group 1: 250
            doc.addLineGroup(groupOf(colorLine("40", "1.25")));   // group 2: 50

            doc.recalculateTotals();

            assertThat(doc.getSubtotalAmount()).isEqualByComparingTo("300");
            assertThat(doc.getTotalQuantity()).isEqualByComparingTo("140");
        }

        @Test
        void recalculatingIsIdempotent() {
            BusinessDocument doc = booking();
            doc.addLineGroup(groupOf(colorLine("10", "3")));

            doc.recalculateTotals();
            BigDecimal once = doc.getSubtotalAmount();
            doc.recalculateTotals();

            assertThat(doc.getSubtotalAmount()).isEqualByComparingTo(once);
        }

        @Test
        void replacingGroupsDropsTheOldOnes() {
            BusinessDocument doc = booking();
            doc.addLineGroup(groupOf(colorLine("100", "1")));
            doc.setLineGroups(List.of(groupOf(colorLine("5", "1"))));

            doc.recalculateTotals();

            assertThat(doc.getLineGroups()).hasSize(1);
            assertThat(doc.getTotalQuantity()).isEqualByComparingTo("5");
        }

        @Test
        void groupNumbersAndColourLineNumbersAreIndependentSequences() {
            // ParentLineDrawService assigns these; verify a group's colour numbering
            // restarts at 1 rather than continuing a document-wide sequence, matching the
            // legacy sort_order, which is scoped per fabric-spec group in the real payload.
            BusinessDocumentLineGroup group = groupOf(
                colorLine("10", "1"), colorLine("20", "1"), colorLine("30", "1"));
            int n = 1;
            for (BusinessDocumentColorLine l : group.getColorLines()) l.setColorLineNo(n++);

            assertThat(group.getColorLines()).extracting(BusinessDocumentColorLine::getColorLineNo)
                .containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("fulfilment ceiling")
    class Fulfilment {

        @Test
        void allowsPartialThenExactCompletion() {
            BusinessDocumentColorLine l = colorLine("100", "1");

            l.fulfil(new BigDecimal("60"));
            assertThat(l.outstandingQuantity()).isEqualByComparingTo("40");
            assertThat(l.isFullyFulfilled()).isFalse();

            l.fulfil(new BigDecimal("40"));
            assertThat(l.outstandingQuantity()).isEqualByComparingTo("0");
            assertThat(l.isFullyFulfilled()).isTrue();
        }

        @Test
        void refusesToExceedTheOrderedQuantity() {
            BusinessDocumentColorLine l = colorLine("100", "1");
            l.fulfil(new BigDecimal("90"));

            assertThatThrownBy(() -> l.fulfil(new BigDecimal("20")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed the ordered quantity");

            // The rejected attempt must not have moved the counter.
            assertThat(l.getFulfilledQuantity()).isEqualByComparingTo("90");
        }

        @Test
        void refusesNonPositiveFulfilment() {
            BusinessDocumentColorLine l = colorLine("100", "1");
            assertThatThrownBy(() -> l.fulfil(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> l.fulfil(new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void eachColourLineHasItsOwnCeilingIndependentOfSiblings() {
            // Two colours under one construction must not share a ceiling — drawing all of
            // one colour must leave the other's untouched.
            BusinessDocumentColorLine white = colorLine("100", "1");
            BusinessDocumentColorLine black = colorLine("50", "1");
            groupOf(white, black);

            white.fulfil(new BigDecimal("100"));

            assertThat(white.isFullyFulfilled()).isTrue();
            assertThat(black.getFulfilledQuantity()).isEqualByComparingTo("0");
            assertThat(black.outstandingQuantity()).isEqualByComparingTo("50");
        }
    }

    @Nested
    @DisplayName("status machine")
    class Status {

        @Test
        void followsTheApprovalPath() {
            BusinessDocument doc = booking();
            assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);

            doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
            doc.transitionTo(BusinessDocumentStatus.APPROVED);

            assertThat(doc.getStatus().isCommitted()).isTrue();
        }

        @Test
        void refusesToSkipApproval() {
            BusinessDocument doc = booking();
            assertThatThrownBy(() -> doc.transitionTo(BusinessDocumentStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transition");
        }

        @Test
        void refusesToReviveAClosedDocument() {
            BusinessDocument doc = booking();
            doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
            doc.transitionTo(BusinessDocumentStatus.APPROVED);
            doc.transitionTo(BusinessDocumentStatus.COMPLETED);
            doc.transitionTo(BusinessDocumentStatus.CLOSED);

            assertThatThrownBy(() -> doc.transitionTo(BusinessDocumentStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void blocksEditingOnceCommitted() {
            BusinessDocument doc = booking();
            doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
            doc.transitionTo(BusinessDocumentStatus.APPROVED);

            assertThatThrownBy(doc::assertEditable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Raise a revision instead");
        }

        @Test
        void rejectedDocumentsAreEditableAgain() {
            BusinessDocument doc = booking();
            doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
            doc.transitionTo(BusinessDocumentStatus.REJECTED);

            assertThatCode(doc::assertEditable).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("document types")
    class Types {

        @Test
        void fabricProcessTypesAreDistinguishedFromStockMovements() {
            assertThat(DocumentType.WEAVING_WORK_ORDER.isFabricProcess()).isTrue();
            assertThat(DocumentType.GREIGE_ISSUE.isFabricProcess()).isTrue();
            assertThat(DocumentType.STOCK_TRANSFER.isFabricProcess()).isFalse();
        }

        @Test
        void salesAndCommercialDocumentsAreRevisable() {
            assertThat(DocumentType.BOOKING.isRevisable()).isTrue();
            assertThat(DocumentType.BULK_PRODUCTION_ORDER.isRevisable()).isTrue();
            assertThat(DocumentType.EXPORT_LETTER_OF_CREDIT.isRevisable()).isTrue();
            assertThat(DocumentType.STORE_REQUISITION.isRevisable()).isFalse();
        }

        @Test
        void prefixesAreUniqueSoDocumentNumbersCannotCollide() {
            long distinct = java.util.Arrays.stream(DocumentType.values())
                .map(DocumentType::prefix).distinct().count();
            assertThat(distinct).isEqualTo(DocumentType.values().length);
        }
    }
}

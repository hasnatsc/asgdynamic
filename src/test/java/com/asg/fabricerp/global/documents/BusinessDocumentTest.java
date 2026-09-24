package com.asg.fabricerp.global.documents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Covers the rules that move quantity and money.
 *
 * <p>SpindleERP has ~40 tests on its AI layer and three on its transactional core, so the
 * guards that matter most commercially are the least covered. These are the equivalent
 * guards here, written alongside the model rather than after an incident.
 */
class BusinessDocumentTest {

    private BusinessDocument booking() {
        BusinessDocument doc = new BusinessDocument();
        doc.setOrganizationId(1L);
        doc.setBusinessUnitId(10L);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKGAF000001");
        doc.setDocumentDate(LocalDate.of(2026, 9, 24));
        return doc;
    }

    private BusinessDocumentLine line(String qty, String rate) {
        BusinessDocumentLine l = new BusinessDocumentLine();
        l.setLineNo(1);
        l.setQuantity(new BigDecimal(qty));
        l.setRate(new BigDecimal(rate));
        return l;
    }

    @Nested
    @DisplayName("totals")
    class Totals {

        @Test
        void areDerivedFromLinesNotFromTheClient() {
            BusinessDocument doc = booking();
            doc.addLine(line("100", "2.50"));
            doc.addLine(line("40", "1.25"));

            doc.recalculateTotals();

            // 100*2.50 + 40*1.25 = 250 + 50
            assertThat(doc.getSubtotalAmount()).isEqualByComparingTo("300");
            assertThat(doc.getTotalQuantity()).isEqualByComparingTo("140");
        }

        @Test
        void recalculatingIsIdempotent() {
            BusinessDocument doc = booking();
            doc.addLine(line("10", "3"));

            doc.recalculateTotals();
            BigDecimal once = doc.getSubtotalAmount();
            doc.recalculateTotals();

            assertThat(doc.getSubtotalAmount()).isEqualByComparingTo(once);
        }

        @Test
        void replacingLinesDropsTheOldOnes() {
            BusinessDocument doc = booking();
            doc.addLine(line("100", "1"));
            doc.setLines(java.util.List.of(line("5", "1")));

            doc.recalculateTotals();

            assertThat(doc.getLines()).hasSize(1);
            assertThat(doc.getTotalQuantity()).isEqualByComparingTo("5");
        }
    }

    @Nested
    @DisplayName("fulfilment ceiling")
    class Fulfilment {

        @Test
        void allowsPartialThenExactCompletion() {
            BusinessDocumentLine l = line("100", "1");

            l.fulfil(new BigDecimal("60"));
            assertThat(l.outstandingQuantity()).isEqualByComparingTo("40");
            assertThat(l.isFullyFulfilled()).isFalse();

            l.fulfil(new BigDecimal("40"));
            assertThat(l.outstandingQuantity()).isEqualByComparingTo("0");
            assertThat(l.isFullyFulfilled()).isTrue();
        }

        @Test
        void refusesToExceedTheOrderedQuantity() {
            BusinessDocumentLine l = line("100", "1");
            l.fulfil(new BigDecimal("90"));

            assertThatThrownBy(() -> l.fulfil(new BigDecimal("20")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed the ordered quantity");

            // The rejected attempt must not have moved the counter.
            assertThat(l.getFulfilledQuantity()).isEqualByComparingTo("90");
        }

        @Test
        void refusesNonPositiveFulfilment() {
            BusinessDocumentLine l = line("100", "1");
            assertThatThrownBy(() -> l.fulfil(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> l.fulfil(new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
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

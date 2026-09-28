package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.DocumentRefs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/** What a booking needs before approval - and what it no longer does: one colour per solid-dyed line. */
class BookingSubmissionCheckTest {

    private final BookingSubmissionCheck check = new BookingSubmissionCheck();

    private static BusinessDocument booking(String fabricType, String... colours) {
        BusinessDocument doc = new BusinessDocument();
        doc.setDocumentNo("BKAF000031");
        doc.setParty(DocumentRefs.party(77L));
        doc.setRequiredDate(LocalDate.of(2025, 12, 10));
        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.getFabric().setFabricType(fabricType);
        for (String name : colours) {
            BusinessDocumentColorLine line = new BusinessDocumentColorLine();
            line.setColorName(name);
            line.setQuantity(new BigDecimal("1000"));
            line.setRate(new BigDecimal("2"));
            group.addColorLine(line);
        }
        doc.addLineGroup(group);
        return doc;
    }

    @Test
    void aSolidDyedLineWithTwoColoursIsSubmitted() {
        assertThatCode(() -> check.check(booking("Greige Solid Dyed", "Navy", "Black"))).doesNotThrowAnyException();
    }

    @Test
    void aLineWithoutColoursIsStillNotSubmitted() {
        assertThatThrownBy(() -> check.check(booking("Yarn Dyed")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has no colours");
    }

    @Test
    void aMultiColourLineWithTwoColoursIsSubmitted() {
        assertThatCode(() -> check.check(booking("Yarn Dyed", "Navy", "Black"))).doesNotThrowAnyException();
    }
}

package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.approval.SubmissionCheck;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a Booking must be before it goes for approval: a buyer and a delivery date, and every
 * fabric line carrying at least one colour with a quantity and a price - and only one colour
 * when its fabric type is a single-colour one ({@link ColourStructure}). A draft may be saved
 * half-keyed; a booking that is signed must be one somebody can plan and invoice from.
 *
 * <p>The amount the approval matrix bands on is the booking's total, so a colour with no price
 * would also let a large order slip under a level meant for it.
 */
@Component
public class BookingSubmissionCheck implements SubmissionCheck {

    @Override
    public DocumentType type() {
        return DocumentType.BOOKING;
    }

    @Override
    public void check(BusinessDocument booking) {
        String no = booking.getDocumentNo();
        if (booking.getParty() == null) {
            throw new IllegalStateException("Choose the buyer on " + no + " before submitting it");
        }
        if (booking.getRequiredDate() == null) {
            throw new IllegalStateException("Set the delivery required date on " + no + " before submitting it");
        }
        List<BusinessDocumentLineGroup> lines = booking.getLineGroups();
        for (int i = 0; i < lines.size(); i++) {
            List<BusinessDocumentColorLine> colours = lines.get(i).getColorLines();
            if (colours.isEmpty()) {
                throw new IllegalStateException("Fabric line %d of %s has no colours. Add its colour breakdown before submitting."
                    .formatted(i + 1, no));
            }
            String fabricType = lines.get(i).getFabric().getFabricType();
            if (ColourStructure.of(fabricType) == ColourStructure.SINGLE && colours.size() > 1) {
                throw new IllegalStateException("Fabric line %d of %s is %s, a single-colour fabric, but carries %d colours. Split it into one line per colour before submitting."
                    .formatted(i + 1, no, fabricType, colours.size()));
            }
            for (int c = 0; c < colours.size(); c++) {
                BusinessDocumentColorLine colour = colours.get(c);
                String name = colour.getColorName() != null ? colour.getColorName()
                    : colour.getColorCode() != null ? colour.getColorCode() : "colour " + (c + 1);
                if (!positive(colour.getQuantity())) {
                    throw new IllegalStateException("%s on fabric line %d has no quantity".formatted(name, i + 1));
                }
                BigDecimal price = booking.isPriceInMeter() ? colour.getPriceInMeter() : colour.getRate();
                if (!positive(price)) {
                    throw new IllegalStateException("%s on fabric line %d has no price".formatted(name, i + 1));
                }
            }
        }
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}

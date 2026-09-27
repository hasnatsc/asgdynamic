package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a commercial editor sends. Lines name the parent line they draw on (a schedule line, a PI
 * line, an LC line) - or, for a regular CI, the delivery challan line they invoice; or, for an
 * import PI, an item directly. Buyer, supplier, currency, fabric and price come from the parent,
 * never from the request, so a PI cannot drift from the order it offers.
 *
 * @param partyId  export PI: the applicant (the garments factory opening the LC, or the buyer);
 *                 import PI: the supplier. Later documents inherit it.
 * @param terms    the clauses, in order; null on a new document takes the library's defaults
 */
public record CommercialDocumentRequest(Long id, LocalDate documentDate, Long partyId, String currencyCode,
                                        BigDecimal exchangeRate, String referenceNo, String remarks, Details details,
                                        List<Line> lines, List<String> terms) {

    /** The commercial facts; each step reads the ones it has. */
    public record Details(LocalDate validityDate, LocalDate shipmentDate, LocalDate issueDate, String lcNo,
                          String masterLcNo, LocalDate masterLcDate, Tenure tenure, PaymentTerms paymentTerms,
                          IncoTerms deliveryTerms, Long bankId, Long bankAccountId, Long counterBankId,
                          Long counterBankAccountId, String foreignBankName, String foreignBankBin, String foreignBankSwift,
                          String foreignBankRouting, String beneficiaryAccountNo, Long hsCodeId, String applicantBondLicence,
                          BigDecimal netWeight, BigDecimal grossWeight, Boolean partialShipment, Boolean btmaCertificate,
                          CiKind ciKind, ImportDocType importDocType, LcType lcType, String port, String cnfAgent, String ipNo,
                          Boolean sroBenefited, String btmaNo, LocalDate btmaDate, Long localAgentId, Long backedByDocumentId) {

        public static Details empty() {
            return new Details(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * @param sourceId       the parent line drawn on
     * @param deliveryLineId a regular CI: the delivery challan line invoiced
     * @param itemId         an import PI's direct line: the item
     * @param rate           an import PI line's unit price
     */
    public record Line(Long sourceId, Long deliveryLineId, Long itemId, BigDecimal quantity, BigDecimal rate,
                       String specification, String remarks) { }

    public List<Line> lines() {
        return lines == null ? List.of() : lines;
    }

    public Details details() {
        return details == null ? Details.empty() : details;
    }
}

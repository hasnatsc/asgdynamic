package com.asg.fabricerp.commercial;

import com.asg.fabricerp.approval.SubmissionCheck;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.supply.SupplyDraws;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What a commercial document must carry before it may enter approval - the legacy screens' starred
 * fields, which the browser alone enforced. A document that is wrong is refused at submission, not
 * signed.
 */
@Configuration
public class CommercialSubmissionChecks {

    private final CommercialDetailsRepository details;

    public CommercialSubmissionChecks(CommercialDetailsRepository details) {
        this.details = details;
    }

    /** An export PI: validity, advising bank and account, HS code, tenure, payment and INCO terms. */
    @Bean
    SubmissionCheck exportPiCheck() {
        return check(DocumentType.EXPORT_PROFORMA_INVOICE, (doc, d) -> {
            need(doc, d.getValidityDate(), "its validity");
            need(doc, d.getBankId(), "the advising bank");
            need(doc, d.getBankAccountId(), "the account the buyer's LC is advised to");
            need(doc, d.getHsCodeId(), "the HS code");
            need(doc, d.getTenure(), "the tenure");
            need(doc, d.getPaymentTerms(), "the payment terms");
            need(doc, d.getDeliveryTerms(), "the INCO terms");
            priced(doc);
        });
    }

    /** An export LC: its number, dates, beneficiary bank and account, buyer's bank. */
    @Bean
    SubmissionCheck exportLcCheck() {
        return check(DocumentType.EXPORT_LETTER_OF_CREDIT, (doc, d) -> {
            need(doc, d.getLcNo(), "the LC number");
            need(doc, d.getIssueDate(), "the date the LC was issued");
            need(doc, d.getValidityDate(), "the expiry date");
            need(doc, d.getShipmentDate(), "the last shipment date");
            need(doc, d.getTenure(), "the tenure");
            need(doc, d.getPaymentTerms(), "the payment terms");
            need(doc, d.getBankId(), "the beneficiary bank");
            need(doc, d.getBankAccountId(), "the beneficiary account");
            need(doc, d.getCounterBankId(), "the buyer's bank");
        });
    }

    @Bean
    SubmissionCheck exportCiCheck() {
        return check(DocumentType.EXPORT_COMMERCIAL_INVOICE, (doc, d) -> {
            need(doc, d.getNetWeight(), "the net weight");
            need(doc, d.getGrossWeight(), "the gross weight");
        });
    }

    /** An import PI: the supplier, and a price on every line. */
    @Bean
    SubmissionCheck importPiCheck() {
        return check(DocumentType.IMPORT_PROFORMA_INVOICE, (doc, d) -> {
            need(doc, doc.getParty(), "the supplier");
            priced(doc);
        });
    }

    /** An import LC (or TT / invoice): its number, dates, our bank; an LC's type and tenure. */
    @Bean
    SubmissionCheck importLcCheck() {
        return check(DocumentType.IMPORT_LETTER_OF_CREDIT, (doc, d) -> {
            need(doc, d.getLcNo(), "the LC (or TT) number");
            need(doc, d.getIssueDate(), "the issue date");
            need(doc, d.getBankId(), "the local bank");
            if (d.getImportDocType() == CommercialTerms.ImportDocType.LC) {
                need(doc, d.getLcType(), "the LC type");
                need(doc, d.getTenure(), "the tenure");
                need(doc, d.getValidityDate(), "the expiry date");
                need(doc, d.getShipmentDate(), "the last shipment date");
                need(doc, d.getPort(), "the port");
            }
        });
    }

    private interface Rule {
        void apply(BusinessDocument doc, CommercialDetails details);
    }

    private SubmissionCheck check(DocumentType type, Rule rule) {
        return new SubmissionCheck() {
            @Override public DocumentType type() { return type; }

            @Override public void check(BusinessDocument doc) {
                rule.apply(doc, details.findById(doc.getId()).orElseGet(() -> new CommercialDetails(doc.getId(), doc.getOrganizationId())));
            }
        };
    }

    private static void need(BusinessDocument doc, Object value, String what) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new IllegalStateException("Give %s on %s before submitting it".formatted(what, doc.getDocumentNo()));
        }
    }

    private static void priced(BusinessDocument doc) {
        for (BusinessDocumentColorLine l : SupplyDraws.lines(doc)) {
            if (l.getRate().signum() <= 0) {
                throw new IllegalStateException("Price %s on %s before submitting it".formatted(SupplyDraws.lineName(l), doc.getDocumentNo()));
            }
        }
    }
}

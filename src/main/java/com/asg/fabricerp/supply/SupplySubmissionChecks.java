package com.asg.fabricerp.supply;

import com.asg.fabricerp.approval.SubmissionCheck;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What a purchase or store document must satisfy before it may enter approval. A document that is
 * wrong is refused at submission, not signed.
 */
@Configuration
public class SupplySubmissionChecks {

    /** A purchase order names its supplier and prices every line - the legacy screen's "Unit Price *". */
    @Bean
    SubmissionCheck purchaseOrderCheck() {
        return new SubmissionCheck() {
            @Override public DocumentType type() { return DocumentType.PURCHASE_ORDER; }

            @Override public void check(BusinessDocument doc) {
                if (doc.getParty() == null) {
                    throw new IllegalStateException("Name the supplier on %s before submitting it".formatted(doc.getDocumentNo()));
                }
                for (BusinessDocumentColorLine l : SupplyDraws.lines(doc)) {
                    if (l.getRate().signum() <= 0) {
                        throw new IllegalStateException("Price %s on %s before submitting it".formatted(SupplyDraws.lineName(l), doc.getDocumentNo()));
                    }
                }
            }
        };
    }

    /** Store documents record what happened and are posted; they never enter approval. */
    @Bean
    SubmissionCheck mrrIsPosted() { return posted(SupplyStep.MRR); }

    @Bean
    SubmissionCheck purchaseReturnIsPosted() { return posted(SupplyStep.PRT); }

    @Bean
    SubmissionCheck materialIssueIsPosted() { return posted(SupplyStep.MI); }

    @Bean
    SubmissionCheck directReceiveIsPosted() { return posted(SupplyStep.MR); }

    @Bean
    SubmissionCheck transferIssueIsPosted() { return posted(SupplyStep.TI); }

    @Bean
    SubmissionCheck transferReceiveIsPosted() { return posted(SupplyStep.TRC); }

    @Bean
    SubmissionCheck fabricTransferIssueIsPosted() { return posted(SupplyStep.FTI); }

    @Bean
    SubmissionCheck fabricTransferReceiveIsPosted() { return posted(SupplyStep.FTR); }

    private static SubmissionCheck posted(SupplyStep step) {
        return new SubmissionCheck() {
            @Override public DocumentType type() { return step.type(); }

            @Override public void check(BusinessDocument doc) {
                throw new IllegalStateException("%s are posted by the store, not approved: open %s and post it"
                    .formatted(step.plural(), doc.getDocumentNo()));
            }
        };
    }
}

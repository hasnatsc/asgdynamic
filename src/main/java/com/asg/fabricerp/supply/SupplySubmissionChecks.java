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

    /** MRRs and purchase returns record what happened and are posted in one step; they never enter approval. */
    @Bean
    SubmissionCheck mrrIsPosted() { return posted(SupplyStep.MRR); }

    @Bean
    SubmissionCheck purchaseReturnIsPosted() { return posted(SupplyStep.PRT); }

    /** Every other store document is approved and then posted: it names the store its stock moves in. */
    @Bean SubmissionCheck materialIssueCheck()         { return storeDocument(SupplyStep.MI); }
    @Bean SubmissionCheck directReceiveCheck()         { return storeDocument(SupplyStep.MR); }
    @Bean SubmissionCheck transferIssueCheck()         { return storeDocument(SupplyStep.TI); }
    @Bean SubmissionCheck transferReceiveCheck()       { return storeDocument(SupplyStep.TRC); }
    @Bean SubmissionCheck stockAdjustmentCheck()       { return storeDocument(SupplyStep.SA); }
    @Bean SubmissionCheck fabricTransferIssueCheck()   { return storeDocument(SupplyStep.FTI); }
    @Bean SubmissionCheck fabricTransferReceiveCheck() { return storeDocument(SupplyStep.FTR); }

    private static SubmissionCheck storeDocument(SupplyStep step) {
        return new SubmissionCheck() {
            @Override public DocumentType type() { return step.type(); }

            @Override public void check(BusinessDocument doc) {
                if (doc.getWarehouse() == null) {
                    throw new IllegalStateException("Choose the store on %s before submitting it".formatted(doc.getDocumentNo()));
                }
                if (step.isTransfer() && doc.getToWarehouse() == null) {
                    throw new IllegalStateException("Choose the store %s goes to before submitting it".formatted(doc.getDocumentNo()));
                }
                for (BusinessDocumentColorLine l : SupplyDraws.lines(doc)) {
                    if (l.getQuantity() == null || l.getQuantity().signum() <= 0) {
                        throw new IllegalStateException("%s on %s has no quantity".formatted(SupplyDraws.lineName(l), doc.getDocumentNo()));
                    }
                    if (step.lines() == SupplyStep.Lines.FABRIC_LOT && l.getFabricLotId() == null) {
                        throw new IllegalStateException("Pick the lot for %s on %s".formatted(SupplyDraws.lineName(l), doc.getDocumentNo()));
                    }
                }
            }
        };
    }

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

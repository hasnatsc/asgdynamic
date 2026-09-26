package com.asg.fabricerp.production;

import com.asg.fabricerp.approval.SubmissionCheck;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.asg.fabricerp.production.ChainDocumentService.lineName;

/**
 * What each approved chain document must carry before it is submitted - refused with a sentence
 * that says what to correct, not signed and found wrong later.
 */
@Configuration
public class ChainSubmissionChecks {

    @Bean
    SubmissionCheck productionOrderCheck() {
        return check(DocumentType.BULK_PRODUCTION_ORDER, doc -> {
            if (doc.getRequiredDate() == null) {
                throw new IllegalStateException("Set the required date on %s before submitting it".formatted(doc.getDocumentNo()));
            }
            for (BusinessDocumentLineGroup g : doc.getLineGroups()) {
                if (!g.getRoute().isSet()) {
                    throw new IllegalStateException(("Fabric type '%s' on %s has no process route, so nobody can tell how it is made. "
                        + "Add it under Master data → Process routes, then save the order again.")
                        .formatted(g.getFabric().getFabricType(), doc.getDocumentNo()));
                }
            }
        });
    }

    @Bean
    SubmissionCheck weavingOrderCheck() {
        return check(DocumentType.WEAVING_WORK_ORDER, doc -> { });
    }

    @Bean
    SubmissionCheck dyeingOrderCheck() {
        return check(DocumentType.PROCESSING_WORK_ORDER, doc -> {
            if (doc.getProcessKind() == null) {
                throw new IllegalStateException("Choose the process (dye, print, finish or rework) on " + doc.getDocumentNo());
            }
        });
    }

    @Bean
    SubmissionCheck deliveryScheduleCheck() {
        return check(DocumentType.REQUEST_FOR_PI, doc -> {
            for (BusinessDocumentLineGroup g : doc.getLineGroups()) {
                for (BusinessDocumentColorLine l : g.getColorLines()) {
                    if (l.getDeliveryDate() == null && doc.getRequiredDate() == null) {
                        throw new IllegalStateException("Give %s on %s a delivery date".formatted(lineName(l), doc.getDocumentNo()));
                    }
                }
            }
        });
    }

    @Bean
    SubmissionCheck deliveryOrderCheck() {
        return check(DocumentType.DELIVERY_ORDER, doc -> {
            if (doc.getWarehouse() == null) {
                throw new IllegalStateException("Choose the delivering store on %s before submitting it".formatted(doc.getDocumentNo()));
            }
            for (BusinessDocumentLineGroup g : doc.getLineGroups()) {
                for (BusinessDocumentColorLine l : g.getColorLines()) {
                    if (l.getFabricLotId() == null) {
                        throw new IllegalStateException("Pick the lot to send for %s on %s".formatted(lineName(l), doc.getDocumentNo()));
                    }
                }
            }
        });
    }

    /** Store documents record what happened; they are posted by the store, never sent for approval. */
    @Bean SubmissionCheck greigeReceiveIsPosted()   { return posted(DocumentType.GREIGE_RECEIVE); }
    @Bean SubmissionCheck greigeIssueIsPosted()     { return posted(DocumentType.GREIGE_ISSUE); }
    @Bean SubmissionCheck finishedReceiveIsPosted() { return posted(DocumentType.FINISHED_FABRICS_RECEIVE); }
    @Bean SubmissionCheck fabricsDeliveryIsPosted() { return posted(DocumentType.FABRICS_DELIVERY); }

    private static SubmissionCheck posted(DocumentType type) {
        return check(type, doc -> {
            throw new IllegalStateException("%s is a store document: post it from its screen instead of submitting it"
                .formatted(doc.getDocumentNo()));
        });
    }

    private static SubmissionCheck check(DocumentType type, java.util.function.Consumer<BusinessDocument> rule) {
        return new SubmissionCheck() {
            @Override public DocumentType type() { return type; }
            @Override public void check(BusinessDocument document) {
                for (BusinessDocumentLineGroup g : document.getLineGroups()) {
                    for (BusinessDocumentColorLine l : g.getColorLines()) {
                        if (l.getQuantity() == null || l.getQuantity().signum() <= 0) {
                            throw new IllegalStateException("%s on %s has no quantity".formatted(lineName(l), document.getDocumentNo()));
                        }
                    }
                }
                rule.accept(document);
            }
        };
    }
}

package com.asg.fabricerp.approval;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.DocumentType;

/**
 * What a document type does the moment its last approval level signs - a delivery order reserves
 * its stock, a weaving work order starts its production order, an approved revision takes over
 * from the version it replaces. Runs inside the approval's transaction: throwing refuses the
 * approval with that message and nothing is signed.
 */
public interface ApprovalListener {

    /** True when this listener acts on {@code type}. */
    boolean handles(DocumentType type);

    void onApproved(BusinessDocument document);
}

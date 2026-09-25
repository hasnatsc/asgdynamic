package com.asg.fabricerp.approval;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.DocumentType;

/**
 * What one document type must satisfy before it may enter approval - asfl-erp's
 * {@code requireLinesReconcile} and {@code requireCounterpartyRole}, run ahead of
 * {@code submitForApproval}.
 *
 * <p>A document that is wrong must be refused at submission, not signed: a signature outlives the
 * correction, and the approver would be approving totals nobody can stand behind. The engine runs
 * every check registered for the document's type; a type with none only needs to have lines.
 */
public interface SubmissionCheck {

    DocumentType type();

    /** Throws {@link IllegalStateException} naming the first thing to correct. */
    void check(BusinessDocument document);
}

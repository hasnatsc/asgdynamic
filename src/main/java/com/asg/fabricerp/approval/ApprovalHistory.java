package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.global.documents.BusinessDocumentStatus;
import com.asg.fabricerp.global.documents.DocumentType;
import jakarta.persistence.*;

/**
 * One row per submit/approve/reject event — the audit trail asgdynamic's client-side
 * {@code soChangeStatus()}/{@code poChangeStatus()} left no server-side record of at all.
 *
 * <p>Append-only: {@code who} and {@code when} are simply {@link #getCreatedBy()} and
 * {@link #getCreatedAt()} from {@link AuditableEntity}, stamped automatically the same way
 * as every other entity. A row is never updated, so {@code updatedBy}/{@code updatedAt}
 * stay null on every row rather than duplicating the same two columns under different
 * names — reusing the base class was worth that one unused pair.
 */
@Entity
@Table(
    name = "apr_document_history",
    indexes = @Index(name = "ix_apr_hist_document", columnList = "document_id"))
public class ApprovalHistory extends AuditableEntity {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private BusinessDocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private BusinessDocumentStatus toStatus;

    @Column(length = 1000)
    private String remarks;

    protected ApprovalHistory() { }

    public ApprovalHistory(Long documentId, DocumentType documentType, ApprovalAction action,
                           BusinessDocumentStatus fromStatus, BusinessDocumentStatus toStatus,
                           String remarks) {
        this.documentId = documentId;
        this.documentType = documentType;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.remarks = remarks;
    }

    public Long getDocumentId()                   { return documentId; }
    public DocumentType getDocumentType()         { return documentType; }
    public ApprovalAction getAction()             { return action; }
    public BusinessDocumentStatus getFromStatus() { return fromStatus; }
    public BusinessDocumentStatus getToStatus()   { return toStatus; }
    public String getRemarks()                    { return remarks; }
}

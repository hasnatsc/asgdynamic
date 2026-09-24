-- Append-only audit trail for submit/approve/reject, written by ApprovalService.
--
-- asgdynamic's client-side *ChangeStatus() handlers left no server-side record of who
-- changed a document's status or why. Every event here is one row, and nothing is ever
-- updated or deleted — createdBy/createdAt (from AuditableEntity) already tell who did it
-- and when, so there is no separate actor/timestamp pair to keep in sync.

CREATE TABLE apr_document_history (
    id            BIGSERIAL    PRIMARY KEY,
    document_id   BIGINT       NOT NULL,
    document_type VARCHAR(40)  NOT NULL,
    action        VARCHAR(20)  NOT NULL,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30)  NOT NULL,
    remarks       VARCHAR(1000),
    version       BIGINT,
    created_by    VARCHAR(100),
    created_at    TIMESTAMP,
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMP,

    CONSTRAINT fk_apr_hist_document FOREIGN KEY (document_id)
        REFERENCES gbl_business_documents (id) ON DELETE CASCADE
);

CREATE INDEX ix_apr_hist_document ON apr_document_history (document_id);

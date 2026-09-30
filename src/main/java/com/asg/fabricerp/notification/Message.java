package com.asg.fabricerp.notification;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One message from one person to another, optionally about a document - an approver asking the
 * maker a question before signing, a maker asking why it came back. Read only by its two people.
 */
@Entity
@Table(name = "ntf_messages")
public class Message extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private Long senderUserId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @Column(name = "document_id", updatable = false)
    private Long documentId;

    /** "Booking BK-AF-0012", as it was called when the message was sent. */
    @Column(name = "document_label", length = 120, updatable = false)
    private String documentLabel;

    @Column(length = 300, updatable = false)
    private String link;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Message() { }

    public Message(Long organizationId, Long senderUserId, Long recipientUserId, String body,
                   Long documentId, String documentLabel, String link) {
        this.organizationId = organizationId;
        this.senderUserId = senderUserId;
        this.recipientUserId = recipientUserId;
        this.body = body;
        this.documentId = documentId;
        this.documentLabel = documentLabel;
        this.link = link;
    }

    public Long getOrganizationId()  { return organizationId; }
    public Long getSenderUserId()    { return senderUserId; }
    public Long getRecipientUserId() { return recipientUserId; }
    public String getBody()          { return body; }
    public Long getDocumentId()      { return documentId; }
    public String getDocumentLabel() { return documentLabel; }
    public String getLink()          { return link; }
    public LocalDateTime getReadAt() { return readAt; }
}

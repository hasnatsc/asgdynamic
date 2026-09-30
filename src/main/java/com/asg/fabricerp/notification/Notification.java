package com.asg.fabricerp.notification;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One event for one person - what the bell in the top bar lists. Written in the same transaction as
 * the event it tells of, so a decision that rolls back leaves no notification of itself behind.
 *
 * <p>Only its recipient reads it, marks it read or clears it; nobody else can see it at all.
 */
@Entity
@Table(name = "ntf_notifications")
public class Notification extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationKind kind;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(length = 1000, updatable = false)
    private String body;

    /** Where clicking it goes - the document on its own screen. */
    @Column(length = 300, updatable = false)
    private String link;

    @Column(name = "document_id", updatable = false)
    private Long documentId;

    /** "Booking BK-AF-0012" - what a reply about it is labelled with. */
    @Column(name = "document_label", length = 120, updatable = false)
    private String documentLabel;

    /** Who caused it, when a person did - the panel offers to message them. */
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() { }

    Notification(Long organizationId, Long recipientUserId, NotificationDraft draft) {
        this.organizationId = organizationId;
        this.recipientUserId = recipientUserId;
        this.kind = draft.kind();
        this.title = clip(draft.title(), 200);
        this.body = clip(draft.body(), 1000);
        this.link = clip(draft.link(), 300);
        this.documentId = draft.documentId();
        this.documentLabel = clip(draft.documentLabel(), 120);
        this.actorUserId = draft.actorUserId();
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Long getOrganizationId()   { return organizationId; }
    public Long getRecipientUserId()  { return recipientUserId; }
    public NotificationKind getKind() { return kind; }
    public String getTitle()          { return title; }
    public String getBody()           { return body; }
    public String getLink()           { return link; }
    public Long getDocumentId()       { return documentId; }
    public String getDocumentLabel()  { return documentLabel; }
    public Long getActorUserId()      { return actorUserId; }
    public LocalDateTime getReadAt()  { return readAt; }
    public boolean isRead()           { return readAt != null; }
}

package com.asg.fabricerp.notification;

/**
 * What to tell people, before it is addressed to anyone - {@link NotificationService#send} makes one
 * {@link Notification} of it per recipient.
 *
 * @param link        where clicking it goes, or null
 * @param documentId  the document it is about, or null
 * @param documentLabel what the document is called ("Booking BK-AF-0012"), or null
 * @param actorUserId the person who caused it, or null for the system
 */
public record NotificationDraft(NotificationKind kind, String title, String body, String link,
                                Long documentId, String documentLabel, Long actorUserId) { }

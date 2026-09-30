package com.asg.fabricerp.notification;

/**
 * What a notification is about. The category groups them in the panel's filter; the tone picks its
 * icon and colour - the client reads both from the row.
 */
public enum NotificationKind {
    APPROVAL_PENDING(Category.APPROVALS, "info", "Awaiting your approval"),
    APPROVAL_PROGRESS(Category.DECISIONS, "success", "Approved at a level"),
    APPROVAL_APPROVED(Category.DECISIONS, "success", "Approved"),
    APPROVAL_RETURNED(Category.DECISIONS, "warn", "Returned for correction"),
    APPROVAL_REJECTED(Category.DECISIONS, "error", "Rejected"),
    APPROVAL_REMINDER(Category.DEADLINES, "warn", "Due soon"),
    APPROVAL_OVERDUE(Category.DEADLINES, "error", "Overdue"),
    APPROVAL_ESCALATED(Category.DEADLINES, "warn", "Escalated"),
    APPROVAL_AUTO_DECIDED(Category.DEADLINES, "warn", "Decided automatically"),
    SYSTEM(Category.SYSTEM, "info", "Notice");

    public enum Category { APPROVALS, DECISIONS, DEADLINES, SYSTEM }

    private final Category category;
    private final String tone;
    private final String label;

    NotificationKind(Category category, String tone, String label) {
        this.category = category;
        this.tone = tone;
        this.label = label;
    }

    public Category category() { return category; }
    public String tone()       { return tone; }
    public String label()      { return label; }
}

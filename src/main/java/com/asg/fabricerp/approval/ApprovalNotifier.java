package com.asg.fabricerp.approval;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.notification.NotificationDraft;
import com.asg.fabricerp.notification.NotificationKind;
import com.asg.fabricerp.notification.NotificationService;
import com.asg.fabricerp.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tells people what the approval engine did, in the transaction that did it: the approvers of the
 * level a document reaches, the maker when it is decided, and both when a deadline passes.
 *
 * <p>Wording lives here and nowhere in the engine, as {@link ApprovalLabels} does for names.
 */
@Component
public class ApprovalNotifier {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);

    private final NotificationService notifications;
    private final ApprovalRecipients recipients;
    private final ApprovalLabels labels;

    public ApprovalNotifier(NotificationService notifications, ApprovalRecipients recipients, ApprovalLabels labels) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.labels = labels;
    }

    /** The document reached a level: its approvers are asked to decide it. */
    public void awaiting(BusinessDocument doc, ApprovalRequest request, Approver approver) {
        String due = request.getLevelDueAt() == null ? "" : " Decide by " + WHEN.format(request.getLevelDueAt()) + ".";
        String maker = request.getRaisedBy() == null ? "" : " · raised by " + makerName(request);
        send(doc, request, NotificationKind.APPROVAL_PENDING,
            "%s awaits your approval".formatted(name(doc)),
            "%s%s%s.%s".formatted(summary(doc), levelOf(request), maker, due),
            recipients.approvers(approver, doc, request));
    }

    /** A level was signed and the document moved on: the maker hears how far it has got. */
    public void progressed(BusinessDocument doc, ApprovalRequest request, int signedLevel, Approver next) {
        send(doc, request, NotificationKind.APPROVAL_PROGRESS,
            "%s approved at level %d of %d".formatted(name(doc), signedLevel, request.getTotalLevels()),
            "Now waiting for %s.".formatted(labels.approver(next, doc.getDocumentType())),
            maker(request));
    }

    /** Settled - approved, returned or rejected: the maker hears, with the approver's reason. */
    public void decided(BusinessDocument doc, ApprovalRequest request, ApprovalDecision decision, String remarks, boolean automatic) {
        NotificationKind kind = switch (decision) {
            case APPROVED -> NotificationKind.APPROVAL_APPROVED;
            case RETURNED -> NotificationKind.APPROVAL_RETURNED;
            case REJECTED -> NotificationKind.APPROVAL_REJECTED;
        };
        String verb = switch (decision) {
            case APPROVED -> "was approved";
            case RETURNED -> "was returned to you for correction";
            case REJECTED -> "was rejected";
        };
        String body = remarks == null || remarks.isBlank()
            ? (decision == ApprovalDecision.APPROVED ? "Every signature it needed is in." : null)
            : remarks.strip();
        send(doc, request, automatic ? NotificationKind.APPROVAL_AUTO_DECIDED : kind,
            "%s %s%s".formatted(name(doc), verb, automatic ? " automatically" : ""), body, maker(request));
    }

    /** Three quarters of the level's time is gone: its approvers are reminded. */
    public void reminder(BusinessDocument doc, ApprovalRequest request, Approver approver, LocalDateTime now) {
        send(doc, request, NotificationKind.APPROVAL_REMINDER,
            "%s is due in %s".formatted(name(doc), duration(Duration.between(now, request.getLevelDueAt()))),
            "%s%s. Decide by %s.".formatted(summary(doc), levelOf(request), WHEN.format(request.getLevelDueAt())),
            recipients.approvers(approver, doc, request));
    }

    /** The time ran out and nobody acts for it: approvers and maker both hear it is overdue. */
    public void overdue(BusinessDocument doc, ApprovalRequest request, Approver approver, String why) {
        Set<Long> to = new LinkedHashSet<>(recipients.approvers(approver, doc, request));
        to.addAll(maker(request));
        send(doc, request, NotificationKind.APPROVAL_OVERDUE,
            "%s is overdue at level %d".formatted(name(doc), request.getCurrentLevel()), why, to);
    }

    /** Handed to someone else: they are asked to decide it, the maker and the passed-over approvers told. */
    public void escalated(BusinessDocument doc, ApprovalRequest request, Approver from, Approver to, String why) {
        send(doc, request, NotificationKind.APPROVAL_ESCALATED,
            "%s was escalated to you".formatted(name(doc)),
            "%s%s. %s".formatted(summary(doc), levelOf(request), why),
            recipients.approvers(to, doc, request));
        Set<Long> others = new LinkedHashSet<>(recipients.approvers(from, doc, request));
        others.addAll(maker(request));
        others.removeAll(recipients.approvers(to, doc, request));
        send(doc, request, NotificationKind.APPROVAL_ESCALATED,
            "%s was escalated to %s".formatted(name(doc), labels.approver(to, doc.getDocumentType())), why, others);
    }

    /** The system decided a level itself: the level's approvers hear what happened in their name. */
    public void autoDecidedLevel(BusinessDocument doc, ApprovalRequest request, Approver approver, String why) {
        send(doc, request, NotificationKind.APPROVAL_AUTO_DECIDED,
            "%s was decided automatically at level %d".formatted(name(doc), request.getCurrentLevel()), why,
            recipients.approvers(approver, doc, request));
    }

    // ------------------------------------------------------------------------------ internals

    private void send(BusinessDocument doc, ApprovalRequest request, NotificationKind kind, String title,
                      String body, Collection<Long> to) {
        if (to.isEmpty()) return;
        notifications.send(request.getOrganizationId(),
            new NotificationDraft(kind, title, body, link(doc), doc.getId(), name(doc), CurrentUser.id()), to);
    }

    private List<Long> maker(ApprovalRequest request) {
        Long maker = recipients.maker(request);
        Long actor = CurrentUser.id();
        // Nobody is told of what they did themselves.
        return maker == null || maker.equals(actor) ? List.of() : List.of(maker);
    }

    private String makerName(ApprovalRequest request) {
        return request.getRaisedByUserId() != null ? labels.userName(request.getRaisedByUserId()) : request.getRaisedBy();
    }

    static String name(BusinessDocument doc) {
        return "%s %s".formatted(doc.getDocumentType().label(), doc.getDocumentNo());
    }

    private static String summary(BusinessDocument doc) {
        StringBuilder s = new StringBuilder();
        if (doc.getParty() != null && doc.getParty().getName() != null) s.append(doc.getParty().getName());
        BigDecimal amount = doc.getSubtotalAmount();
        if (amount != null && amount.signum() != 0) {
            if (!s.isEmpty()) s.append(" · ");
            s.append(amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            if (doc.getCurrencyCode() != null) s.append(' ').append(doc.getCurrencyCode());
        }
        return s.toString();
    }

    private static String levelOf(ApprovalRequest request) {
        return request.getTotalLevels() > 1
            ? " · level %d of %d".formatted(request.getCurrentLevel(), request.getTotalLevels()) : "";
    }

    static String link(BusinessDocument doc) {
        String path = ApprovalLabels.screenPath(doc.getDocumentType());
        return path == null ? "/approvals" : path + "?open=" + doc.getId();
    }

    /** "2 days", "5 hours", "40 minutes" - the largest whole unit, never "0 minutes". */
    static String duration(Duration d) {
        long minutes = Math.max(1, d.toMinutes());
        if (minutes >= 2 * 24 * 60) return (minutes / (24 * 60)) + " days";
        if (minutes >= 120) return (minutes / 60) + " hours";
        if (minutes >= 60) return "1 hour";
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    /** A limit in words: 1440 → "1 day", 90 → "90 minutes", 120 → "2 hours". */
    static String limit(Integer minutes) {
        if (minutes == null) return "no limit";
        if (minutes % (24 * 60) == 0) {
            long days = minutes / (24 * 60);
            return days + (days == 1 ? " day" : " days");
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}

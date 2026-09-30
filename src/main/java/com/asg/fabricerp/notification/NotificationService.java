package com.asg.fabricerp.notification;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The notification centre: what the bell and the Notifications page show, and person-to-person
 * messages.
 *
 * <p>{@link #send} is how every other module tells people something; it joins the caller's
 * transaction. Everything else acts for the signed-in user on their own notifications and messages
 * only - every query names them, so no id sent from the browser reaches anyone else's.
 */
@Service
public class NotificationService {

    /** A message is a note, not a document. */
    static final int MESSAGE_MAX = 2000;

    private final NotificationRepository notifications;
    private final MessageRepository messages;
    private final FabricUserRepository users;
    private final OrgContext context;

    public NotificationService(NotificationRepository notifications, MessageRepository messages,
                               FabricUserRepository users, OrgContext context) {
        this.notifications = notifications;
        this.messages = messages;
        this.users = users;
        this.context = context;
    }

    // ------------------------------------------------------------------------------ sending

    /** One notification per recipient; nulls and duplicates are dropped. */
    @Transactional
    public void send(Long organizationId, NotificationDraft draft, Collection<Long> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) return;
        List<Notification> rows = recipientIds.stream().filter(Objects::nonNull).distinct()
            .map(id -> new Notification(organizationId, id, draft))
            .toList();
        notifications.saveAll(rows);
    }

    // ------------------------------------------------------------------------------ the bell

    /**
     * The badge counts, and what arrived after the ids the page last saw - the client toasts those.
     * {@code null} afters ask only for the counts and the current latest ids.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long notificationsAfter, Long messagesAfter) {
        Long me = me();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unreadNotifications", notifications.countByRecipientUserIdAndReadAtIsNull(me));
        out.put("unreadMessages", messages.countByRecipientUserIdAndReadAtIsNull(me));
        out.put("latestNotificationId", notifications.latestId(me));
        out.put("latestMessageId", messages.latestId(me));
        List<Map<String, Object>> fresh = new ArrayList<>();
        if (notificationsAfter != null) {
            notifications.findTop5ByRecipientUserIdAndReadAtIsNullAndIdGreaterThanOrderByIdDesc(me, notificationsAfter)
                .forEach(n -> fresh.add(Map.of("type", "notification", "title", n.getTitle(),
                    "tone", n.getKind().tone(), "link", n.getLink() == null ? "" : n.getLink())));
        }
        if (messagesAfter != null) {
            List<Message> newMessages = messages.findTop5ByRecipientUserIdAndReadAtIsNullAndIdGreaterThanOrderByIdDesc(me, messagesAfter);
            Map<Long, FabricUser> senders = people(newMessages.stream().map(Message::getSenderUserId).toList());
            newMessages.forEach(m -> fresh.add(Map.of("type", "message",
                "title", "Message from " + display(senders.get(m.getSenderUserId()), m.getSenderUserId()),
                "tone", "info", "senderId", m.getSenderUserId())));
        }
        out.put("fresh", fresh);
        return out;
    }

    /** One page of the signed-in user's notifications, newest first. */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(boolean unreadOnly, NotificationKind.Category category, String q, Pageable pageable) {
        Set<NotificationKind> kinds = category == null ? EnumSet.allOf(NotificationKind.class)
            : Arrays.stream(NotificationKind.values()).filter(k -> k.category() == category)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationKind.class)));
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        Page<Notification> page = notifications.search(me(), unreadOnly, category == null, kinds,
            q == null || q.isBlank() ? null : q.trim(), sorted);
        Map<Long, FabricUser> actors = people(page.getContent().stream().map(Notification::getActorUserId).toList());
        return page.map(n -> row(n, actors));
    }

    @Transactional
    public int markRead(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? 0 : notifications.markRead(me(), ids, LocalDateTime.now());
    }

    @Transactional
    public int markUnread(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? 0 : notifications.markUnread(me(), ids);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(me(), LocalDateTime.now());
    }

    @Transactional
    public int delete(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? 0 : notifications.deleteOwn(me(), ids);
    }

    @Transactional
    public int clearRead() {
        return notifications.deleteAllRead(me());
    }

    // ------------------------------------------------------------------------------ messages

    /** What a message posts; the document, when there is one, is only named and linked. */
    public record MessageRequest(Long recipientId, String body, Long documentId, String documentLabel, String link) { }

    /** The people the signed-in user has messaged or been messaged by, most recent first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> conversations() {
        Long me = me();
        List<Object[]> rows = messages.conversations(me, 100);
        Map<Long, FabricUser> people = people(rows.stream().map(r -> ((Number) r[0]).longValue()).toList());
        Map<Long, Message> last = messages.findAllById(rows.stream().map(r -> ((Number) r[1]).longValue()).toList()).stream()
            .collect(Collectors.toMap(Message::getId, Function.identity()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Long otherId = ((Number) r[0]).longValue();
            Message m = last.get(((Number) r[1]).longValue());
            Map<String, Object> row = person(people.get(otherId), otherId);
            row.put("lastBody", m == null ? null : m.getBody());
            row.put("lastAt", m == null ? null : m.getCreatedAt());
            row.put("lastMine", m != null && me.equals(m.getSenderUserId()));
            row.put("unread", ((Number) r[2]).longValue());
            out.add(row);
        }
        return out;
    }

    /** One page of the thread with another person, newest first; everything they sent is read by opening it. */
    @Transactional
    public Map<String, Object> thread(Long otherId, int page) {
        Long me = me();
        FabricUser other = users.findScoped(otherId, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("No such person: " + otherId));
        messages.markThreadRead(me, otherId, LocalDateTime.now());
        Page<Message> found = messages.thread(me, otherId,
            PageRequest.of(Math.max(page, 0), 30, Sort.by(Sort.Direction.DESC, "id")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("with", person(other, otherId));
        out.put("messages", found.getContent().stream().map(m -> messageRow(m, me)).toList());
        out.put("more", found.hasNext());
        return out;
    }

    @Transactional
    public Map<String, Object> sendMessage(MessageRequest request) {
        Long me = me();
        Long orgId = context.requireOrganizationId();
        if (request == null || request.recipientId() == null) {
            throw new IllegalArgumentException("Choose who the message is for");
        }
        if (request.recipientId().equals(me)) {
            throw new IllegalArgumentException("Choose someone other than yourself");
        }
        String body = request.body() == null ? "" : request.body().strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Write a message first");
        }
        if (body.length() > MESSAGE_MAX) {
            throw new IllegalArgumentException("A message is at most %d characters - this one is %d".formatted(MESSAGE_MAX, body.length()));
        }
        FabricUser recipient = users.findScoped(request.recipientId(), orgId)
            .orElseThrow(() -> new IllegalArgumentException("No such person: " + request.recipientId()));
        if (Boolean.TRUE.equals(recipient.getAccountLocked())) {
            throw new IllegalStateException(display(recipient, recipient.getId()) + "'s account is locked - they cannot read it");
        }
        String label = request.documentLabel() == null || request.documentLabel().isBlank() ? null
            : request.documentLabel().strip();
        if (label != null && label.length() > 120) label = label.substring(0, 120);
        Message saved = messages.save(new Message(orgId, me, recipient.getId(), body,
            request.documentId(), label, safeLink(request.link())));
        return messageRow(saved, me);
    }

    /** Anyone in the organization whose account is open, by name or username - the New message picker. */
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> lookupPeople(String q, Integer page, Long id) {
        Long orgId = context.requireOrganizationId();
        if (id != null) {
            return LookupPage.single(users.findScoped(id, orgId).map(NotificationService::option).orElse(null));
        }
        Long me = me();
        Page<FabricUser> found = users.search(orgId, q == null || q.isBlank() ? null : q.trim(), false, null, null,
            LookupPage.pageable(page, null, Sort.by("fullName")));
        return LookupPage.of(found.getContent().stream().filter(u -> !u.getId().equals(me))
            .map(NotificationService::option).toList(), found.hasNext());
    }

    // ------------------------------------------------------------------------------ internals

    private static Long me() {
        Long id = CurrentUser.id();
        if (id == null) throw new IllegalStateException("Sign in to see notifications");
        return id;
    }

    /** Only a path on this site: a link a browser sent is never followed anywhere else. */
    static String safeLink(String link) {
        if (link == null || link.isBlank()) return null;
        String l = link.strip();
        return l.startsWith("/") && !l.startsWith("//") && !l.contains("\\") && l.length() <= 300 ? l : null;
    }

    private Map<Long, FabricUser> people(Collection<Long> ids) {
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) return Map.of();
        return users.findAllById(wanted).stream().collect(Collectors.toMap(FabricUser::getId, Function.identity()));
    }

    private Map<String, Object> row(Notification n, Map<Long, FabricUser> actors) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.getId());
        row.put("kind", n.getKind().name());
        row.put("kindLabel", n.getKind().label());
        row.put("category", n.getKind().category().name());
        row.put("tone", n.getKind().tone());
        row.put("title", n.getTitle());
        row.put("body", n.getBody());
        row.put("link", n.getLink());
        row.put("documentId", n.getDocumentId());
        row.put("documentLabel", n.getDocumentLabel());
        row.put("read", n.isRead());
        row.put("at", n.getCreatedAt());
        if (n.getActorUserId() != null) {
            row.put("actorId", n.getActorUserId());
            row.put("actorName", display(actors.get(n.getActorUserId()), n.getActorUserId()));
        }
        return row;
    }

    private static Map<String, Object> messageRow(Message m, Long me) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("mine", me.equals(m.getSenderUserId()));
        row.put("body", m.getBody());
        row.put("documentLabel", m.getDocumentLabel());
        row.put("link", m.getLink());
        row.put("at", m.getCreatedAt());
        row.put("read", m.getReadAt() != null);
        return row;
    }

    private static Map<String, Object> person(FabricUser u, Long id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", id);
        row.put("name", display(u, id));
        row.put("username", u == null ? null : u.getUsername());
        row.put("photoUrl", u == null ? null : u.photoThumbUrl());
        row.put("initials", initials(display(u, id)));
        return row;
    }

    private static LookupPage.Option option(FabricUser u) {
        return new LookupPage.Option(u.getId(), u.getUsername(), display(u, u.getId()), u.getDesignation());
    }

    static String display(FabricUser u, Long id) {
        if (u == null) return "user #" + id;
        return u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName();
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String s = parts.length > 1 ? "" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0) : parts[0].substring(0, Math.min(2, parts[0].length()));
        return s.toUpperCase(Locale.ROOT);
    }
}

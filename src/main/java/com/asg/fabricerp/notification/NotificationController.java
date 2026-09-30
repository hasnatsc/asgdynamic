package com.asg.fabricerp.notification;

import com.asg.fabricerp.common.LookupPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The notification centre - the bell's panel and the full page. Everybody signed in has one; it
 * is theirs alone, so there is no screen grant to hold, only a sign-in.
 *
 * <pre>
 *   GET  /notifications                              the page (?tab=messages&amp;with=12 opens a thread)
 *   GET  /api/notifications/summary?nAfter=&amp;mAfter=   badge counts, and what arrived since
 *   GET  /api/notifications?unread=&amp;category=&amp;q=&amp;page=&amp;size=
 *   POST /api/notifications/read      {ids}          mark read
 *   POST /api/notifications/unread    {ids}          mark unread
 *   POST /api/notifications/read-all
 *   POST /api/notifications/delete    {ids}
 *   POST /api/notifications/clear-read
 *   GET  /api/messages/conversations
 *   GET  /api/messages/thread/{userId}?page=         reads what they sent
 *   POST /api/messages                {recipientId, body, documentId?, documentLabel?, link?}
 *   GET  /api/messages/people?q=&amp;page=&amp;id=          the New message picker (LookupPage)
 * </pre>
 */
@Controller
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    public String page(Model model) {
        model.addAttribute("title", "Notifications");
        model.addAttribute("categories", NotificationKind.Category.values());
        model.addAttribute("content", "notifications/index :: content");
        return "layout/main";
    }

    @GetMapping("/api/notifications/summary")
    @ResponseBody
    public Map<String, Object> summary(@RequestParam(required = false) Long nAfter,
                                       @RequestParam(required = false) Long mAfter) {
        return service.summary(nAfter, mAfter);
    }

    @GetMapping("/api/notifications")
    @ResponseBody
    public Map<String, Object> list(@RequestParam(defaultValue = "false") boolean unread,
                                    @RequestParam(required = false) NotificationKind.Category category,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> found = service.list(unread, category, q,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", found.getContent());
        out.put("page", found.getNumber());
        out.put("total", found.getTotalElements());
        out.put("more", found.hasNext());
        return out;
    }

    public record Ids(List<Long> ids) { }

    @PostMapping("/api/notifications/read")
    @ResponseBody
    public Map<String, Object> read(@RequestBody Ids body) {
        return Map.of("updated", service.markRead(body.ids()));
    }

    @PostMapping("/api/notifications/unread")
    @ResponseBody
    public Map<String, Object> unread(@RequestBody Ids body) {
        return Map.of("updated", service.markUnread(body.ids()));
    }

    @PostMapping("/api/notifications/read-all")
    @ResponseBody
    public Map<String, Object> readAll() {
        return Map.of("updated", service.markAllRead());
    }

    @PostMapping("/api/notifications/delete")
    @ResponseBody
    public Map<String, Object> delete(@RequestBody Ids body) {
        return Map.of("deleted", service.delete(body.ids()));
    }

    @PostMapping("/api/notifications/clear-read")
    @ResponseBody
    public Map<String, Object> clearRead() {
        return Map.of("deleted", service.clearRead());
    }

    // ------------------------------------------------------------------------------ messages

    @GetMapping("/api/messages/conversations")
    @ResponseBody
    public List<Map<String, Object>> conversations() {
        return service.conversations();
    }

    @GetMapping("/api/messages/thread/{userId}")
    @ResponseBody
    public Map<String, Object> thread(@PathVariable Long userId, @RequestParam(defaultValue = "0") int page) {
        return service.thread(userId, page);
    }

    @PostMapping("/api/messages")
    @ResponseBody
    public Map<String, Object> send(@RequestBody NotificationService.MessageRequest request) {
        return service.sendMessage(request);
    }

    @GetMapping("/api/messages/people")
    @ResponseBody
    public LookupPage<LookupPage.Option> people(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Long id) {
        return service.lookupPeople(q, page, id);
    }
}

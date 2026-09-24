package com.asg.fabricerp.security;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.security.AccessLogEntry.Event;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only view of the ADM-11 access log. There is no write endpoint and never will be: the
 * log is append-only, and the only way into it is the event that is being recorded.
 */
@Controller
public class AccessLogController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "occurredAt", "occurredAt",
        "username", "username",
        "event", "event"
    ));

    private final AccessLogService service;
    private final OrgContext context;

    public AccessLogController(AccessLogService service, OrgContext context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping("/setup/access-log")
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Access log");
        model.addAttribute("events", Event.values());
        model.addAttribute("content", "setup/access-log :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/access-log")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "50") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Event event,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        var page = service.search(context.requireOrganizationId(), request.searchOrNull(), event, from, to,
            request.toPageable(SORTABLE, "occurredAt"));
        return DataTableResponse.from(draw, page, AccessLogController::toRow);
    }

    private static Map<String, Object> toRow(AccessLogEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("occurredAt", e.getOccurredAt());
        row.put("userId", e.getUserId());
        row.put("username", e.getUsername());
        row.put("event", e.getEvent());
        row.put("target", e.getTarget());
        row.put("detail", e.getDetail());
        row.put("sourceAddress", e.getSourceAddress());
        return row;
    }
}

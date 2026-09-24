package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The landing page of security administration — {@link Screen#SECURITY_ADMIN}'s menu entry. */
@Controller
public class SecurityOverviewController {

    private final SecurityOverviewService service;

    public SecurityOverviewController(SecurityOverviewService service) {
        this.service = service;
    }

    @GetMapping("/setup/security")
    @PreAuthorize("hasAuthority('SCREEN_SECURITY_ADMIN_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Security overview");
        model.addAttribute("overview", service.overview());
        model.addAttribute("events", Event.values());
        model.addAttribute("content", "setup/security :: content");
        return "layout/main";
    }
}

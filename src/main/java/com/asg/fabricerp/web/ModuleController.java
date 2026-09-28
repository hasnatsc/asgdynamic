package com.asg.fabricerp.web;

import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.Screen;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An app's own page - its dashboard beside its menu - and the lists the attention cards open.
 *
 * <pre>
 *   GET /module/{key}                      the app's page: its menu and dashboard
 *   GET /api/home/my-work?module=&amp;kind=    the user's drafts, returned, rejected, or awaiting approval
 * </pre>
 *
 * An app opens to whoever may VIEW at least one of its screens - the same rule as the menu; any
 * other user is refused, not shown an empty page. What each screen then shows is still decided by
 * that screen's own {@code @PreAuthorize}.
 */
@Controller
public class ModuleController {

    private final ModuleDashboardService modules;

    public ModuleController(ModuleDashboardService modules) {
        this.modules = modules;
    }

    @GetMapping("/module/{key}")
    @PreAuthorize("isAuthenticated()")
    public String module(@PathVariable String key, Model model) {
        Screen.Section section = ModuleDashboardService.section(key)
            .orElseThrow(() -> new AccessDeniedException("No such app"));
        Set<String> authorities = authorities();
        if (ModuleDashboardService.screens(section, authorities).isEmpty()) {
            throw new AccessDeniedException("None of this app's screens is in your roles");
        }
        model.addAttribute("title", section.label());
        model.addAttribute("module", modules.module(section, authorities));
        // The app's own menu: the sidebar's section for it, nothing else.
        model.addAttribute("moduleNav", Navigation.build(authorities, "/module/" + key).stream()
            .filter(s -> s.key().equals(key)).findFirst().orElseThrow());
        model.addAttribute("content", "module :: content");
        return "layout/main";
    }

    @GetMapping("/api/home/my-work")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> myWork(@RequestParam(required = false) String module,
                                           @RequestParam(required = false) String kind) {
        if (kind != null && !kind.isBlank() && !List.of("draft", "returned", "rejected", "approval").contains(kind)) {
            throw new IllegalArgumentException("Unknown list " + kind);
        }
        return modules.myWork(authorities(), module, kind);
    }

    static Set<String> authorities() {
        return CurrentUser.principal().stream().flatMap(p -> p.getAuthorities().stream())
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}

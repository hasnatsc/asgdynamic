package com.asg.fabricerp.web;

import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.Role;
import com.asg.fabricerp.web.DashboardService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The landing page after sign-in ({@code SecurityConfig}'s {@code defaultSuccessUrl("/")}):
 * every screen the user may open, and their own account's state. Before this there was no
 * {@code /} route at all, so a successful login landed on a 404.
 */
@Controller
public class HomeController {

    private final FabricUserRepository users;
    private final DashboardService dashboard;

    public HomeController(FabricUserRepository users, DashboardService dashboard) {
        this.users = users;
        this.dashboard = dashboard;
    }

    /**
     * "/" is open to everyone: a visitor sees the public company page with its Login button, a
     * signed-in user the dashboard. SecurityConfig permits exactly this path and nothing under it.
     */
    @GetMapping("/")
    public String home(@RequestParam(required = false) String passwordChanged, Model model) {
        if (CurrentUser.principal().isEmpty()) {
            return "landing";
        }
        model.addAttribute("title", "Dashboard");
        model.addAttribute("passwordChanged", passwordChanged != null);
        Long userId = CurrentUser.id();
        if (userId != null) {
            users.findById(userId).ifPresent(user -> {
                model.addAttribute("account", user);
                model.addAttribute("accountRoles", user.getRoles().stream()
                    .filter(role -> Boolean.TRUE.equals(role.getActive()))
                    .map(Role::getName).sorted().toList());
            });
        }
        Set<String> authorities = CurrentUser.principal().stream()
            .flatMap(principal -> principal.getAuthorities().stream())
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        model.addAttribute("stats", dashboard.stats());
        model.addAttribute("flow", ManufacturingFlow.build(authorities));
        model.addAttribute("content", "home :: content");
        return "layout/main";
    }
}

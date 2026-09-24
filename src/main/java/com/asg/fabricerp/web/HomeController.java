package com.asg.fabricerp.web;

import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The landing page after sign-in ({@code SecurityConfig}'s {@code defaultSuccessUrl("/")}):
 * every screen the user may open, and their own account's state. Before this there was no
 * {@code /} route at all, so a successful login landed on a 404.
 */
@Controller
public class HomeController {

    private final FabricUserRepository users;

    public HomeController(FabricUserRepository users) {
        this.users = users;
    }

    @GetMapping("/")
    @PreAuthorize("isAuthenticated()")
    public String home(@RequestParam(required = false) String passwordChanged, Model model) {
        model.addAttribute("title", "Home");
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
        model.addAttribute("content", "home :: content");
        return "layout/main";
    }
}

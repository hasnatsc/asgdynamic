package com.asg.fabricerp.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Change password — the page {@link SessionPrincipalRefreshFilter} holds a session on while an
 * administrator-issued password is still in force, and the ordinary way to change your own.
 *
 * <p>A plain form post rather than the JSON the admin screens use: this page must work while
 * every other route is redirecting here, so it depends on nothing but itself.
 */
@Controller
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping(SessionPrincipalRefreshFilter.CHANGE_PASSWORD_PATH)
    @PreAuthorize("isAuthenticated()")
    public String form(Model model) {
        model.addAttribute("forced", CurrentUser.principal()
            .map(FabricUserPrincipal::isMustChangePassword).orElse(false));
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        return "account/password";
    }

    @PostMapping(SessionPrincipalRefreshFilter.CHANGE_PASSWORD_PATH)
    @PreAuthorize("isAuthenticated()")
    public String change(@RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         Model model) {
        String error = null;
        if (!newPassword.equals(confirmPassword)) {
            error = "The new passwords do not match.";
        } else {
            try {
                accounts.changeOwnPassword(CurrentUser.id(), currentPassword, newPassword);
            } catch (AuthenticationException | IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        if (error != null) {
            model.addAttribute("error", error);
            return form(model);
        }
        return "redirect:/?passwordChanged";
    }
}

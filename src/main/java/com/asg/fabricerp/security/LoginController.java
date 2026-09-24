package com.asg.fabricerp.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    /**
     * A wrong password is reported as "invalid username or password" and nothing more. A locked,
     * disabled or unscoped account is named — as asfl-erp does — because retrying will never
     * work and the person needs to call somebody, not keep guessing and feeding the counter.
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        HttpSession session,
                        Model model) {
        model.addAttribute("error", error != null);
        model.addAttribute("loggedOut", logout != null);
        if (error != null
                && session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) instanceof AccountStatusException status) {
            model.addAttribute("accountStatus", status.getMessage());
        }
        return "login";
    }
}

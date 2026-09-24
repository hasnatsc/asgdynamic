package com.asg.fabricerp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** The authenticated {@link FabricUserPrincipal}, when there is one. Same read as {@link AuthorityChecks}. */
public final class CurrentUser {

    private CurrentUser() { }

    public static Optional<FabricUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return auth.getPrincipal() instanceof FabricUserPrincipal p ? Optional.of(p) : Optional.empty();
    }

    /** Null outside a request — a scheduler or a seeder is nobody, and cannot be granting to itself. */
    public static Long id() {
        return principal().map(FabricUserPrincipal::getUserId).orElse(null);
    }
}

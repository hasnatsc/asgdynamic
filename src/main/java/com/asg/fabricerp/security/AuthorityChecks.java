package com.asg.fabricerp.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * Runtime authority checks for the one case {@code @PreAuthorize} can't express statically: a
 * single endpoint (e.g. a document's {@code save()}) that handles both CREATE and AMEND,
 * discriminated at runtime by whether an id was submitted. Extracted from
 * {@code ApprovalService}'s original private {@code requireRole(String)} — same
 * {@code SecurityContextHolder} read, now reusable.
 *
 * <p>Everywhere else, a static {@code @PreAuthorize("hasAuthority('SCREEN_...')")} on the
 * controller method is enough and should be preferred — this class exists for the narrower case
 * that annotation alone cannot cover, not as a general-purpose replacement for it.
 */
public final class AuthorityChecks {

    private AuthorityChecks() { }

    public static void require(String authority) {
        if (!held().contains(authority)) {
            throw new AccessDeniedException("This action requires " + authority);
        }
    }

    public static void requireAny(String... authorities) {
        Set<String> held = held();
        for (String authority : authorities) {
            if (held.contains(authority)) {
                return;
            }
        }
        throw new AccessDeniedException(
            "This action requires one of " + String.join(", ", authorities));
    }

    private static Set<String> held() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());
    }
}

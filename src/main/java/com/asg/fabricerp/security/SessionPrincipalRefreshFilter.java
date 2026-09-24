package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Re-resolves the logged-in user on every request — asfl-erp's {@code loadByUsername} rule.
 *
 * <p>Before this, the principal was built once at login and carried in the session: a role
 * revoked, an account locked or deleted, a scope narrowed — none of it reached a session that
 * was already open, for as long as that session (or a 14-day remember-me cookie) lasted. Now
 * the stored principal is only a name to look up; roles, lock state and row scope are read
 * fresh each time.
 *
 * <p>Two outcomes beyond the refresh:
 * <ul>
 *   <li><b>The session ends</b> when the account has been deleted, locked or deactivated, or is
 *       restricted with no scope left (ADM-3: unconfigured is not the same as unrestricted).
 *       The request carries on anonymous, so the normal redirect to login happens.</li>
 *   <li><b>Only the change-password page is reachable</b> while {@code mustChangePassword} is
 *       set: an administrator-issued password is good for choosing a new one and nothing else.</li>
 * </ul>
 *
 * <p>Registered in {@link SecurityConfig}, deliberately not a {@code @Component}: Spring Boot
 * would also register a component filter with the servlet container, running it a second time
 * outside the security chain.
 */
public class SessionPrincipalRefreshFilter extends OncePerRequestFilter {

    static final String CHANGE_PASSWORD_PATH = "/account/password";

    private static final Set<String> ALLOWED_WHILE_PASSWORD_EXPIRED =
        Set.of(CHANGE_PASSWORD_PATH, "/logout", "/error");

    private final FabricUserDetailsService users;
    private final AccessLogService accessLog;
    private final RememberMeServices rememberMe;

    public SessionPrincipalRefreshFilter(FabricUserDetailsService users, AccessLogService accessLog,
                                         RememberMeServices rememberMe) {
        this.users = users;
        this.accessLog = accessLog;
        this.rememberMe = rememberMe;
    }

    /** Static assets need no principal, and would otherwise cost a user reload each. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
            || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !(current.getPrincipal() instanceof FabricUserPrincipal stale)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<FabricUserPrincipal> fresh = users.reload(stale.getUsername());
        String endReason = reasonToEnd(fresh);
        if (endReason != null) {
            accessLog.record(stale.getUserId(), stale.getUsername(), Event.SESSION_ENDED,
                request.getMethod() + " " + request.getRequestURI(), endReason);
            endSession(request, response);
            chain.doFilter(request, response);
            return;
        }

        FabricUserPrincipal principal = fresh.get();
        var refreshed = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        refreshed.setDetails(current.getDetails());
        // A new context rather than mutating the current one: the current one is the object the
        // session holds, and this refresh is per request by design — nothing here is saved.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);

        if (principal.isMustChangePassword()
                && !ALLOWED_WHILE_PASSWORD_EXPIRED.contains(request.getServletPath())) {
            if (request.getServletPath().startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Password change required before anything else");
            } else {
                response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_PATH);
            }
            return;
        }

        chain.doFilter(request, response);
    }

    static String reasonToEnd(Optional<FabricUserPrincipal> fresh) {
        if (fresh.isEmpty()) {
            return "Account deleted";
        }
        FabricUserPrincipal principal = fresh.get();
        if (!principal.isAccountNonLocked()) {
            return "Account locked";
        }
        if (!principal.isEnabled()) {
            return "Account deactivated";
        }
        if (!principal.getRowScope().isConfigured()) {
            return "Restricted account has no data scope";
        }
        return null;
    }

    private void endSession(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        // Cancels the remember-me cookie, which would otherwise log straight back in next request.
        rememberMe.loginFail(request, response);
    }
}

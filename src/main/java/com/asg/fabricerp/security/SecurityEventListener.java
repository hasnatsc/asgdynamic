package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Lockout and the ADM-11 access log, driven by Spring Security's own events rather than by
 * wrapping the login form — so a form login, a remember-me login and a method-security refusal
 * are all seen by the same code without any of them having to remember to call it.
 */
@Component
public class SecurityEventListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventListener.class);

    private final FabricUserRepository users;
    private final AccessLogService accessLog;
    private final int lockoutThreshold;

    public SecurityEventListener(FabricUserRepository users, AccessLogService accessLog,
                                 @Value("${app.security.lockout-threshold:5}") int lockoutThreshold) {
        this.users = users;
        this.accessLog = accessLog;
        this.lockoutThreshold = lockoutThreshold;
    }

    /**
     * A wrong password, or an unknown username — Spring reports both as bad credentials, and
     * the person at the login form is told nothing more. The log records which it was, because
     * an administrator investigating needs to know.
     *
     * <p>Locked and disabled accounts never reach here: Spring checks those before the
     * password, so a locked account's counter does not keep climbing.
     */
    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        LocalDateTime now = LocalDateTime.now();

        Long userId = users.findByUsernameIgnoreCaseAndDeletedFalse(username)
            .map(FabricUser::getId).orElse(null);
        if (userId == null) {
            accessLog.record(null, username, Event.LOGIN_FAILED, "LOGIN", "No account with that username");
            return;
        }

        users.recordFailedLogin(username, now);
        boolean nowLocked = users.lockIfOverThreshold(username, lockoutThreshold,
            "Locked after " + lockoutThreshold + " consecutive failed logins", now) > 0;

        accessLog.record(userId, username, Event.LOGIN_FAILED, "LOGIN", "Wrong password");
        if (nowLocked) {
            log.warn("Account '{}' locked after {} consecutive failed logins", username, lockoutThreshold);
            accessLog.record(userId, username, Event.ACCOUNT_LOCKED, "LOGIN",
                "Locked after " + lockoutThreshold + " consecutive failed logins");
        }
    }

    /** Locked, disabled, or restricted with no scope configured (see {@code SecurityConfig}). */
    @EventListener
    public void onRefused(AbstractAuthenticationFailureEvent event) {
        if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            return;
        }
        accessLog.record(null, event.getAuthentication().getName(), Event.LOGIN_REFUSED_INACTIVE,
            "LOGIN", event.getException().getMessage());
    }

    /**
     * Clears the failure run. Form logins only: a remember-me cookie proves nothing about the
     * password, and letting it reset the counter would let a guesser interleave attempts with
     * the owner's automatic logins indefinitely.
     */
    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (!(auth instanceof UsernamePasswordAuthenticationToken)
                || !(auth.getPrincipal() instanceof FabricUserPrincipal principal)) {
            return;
        }
        users.recordSuccessfulLogin(principal.getUserId(), LocalDateTime.now());
        accessLog.record(principal.getUserId(), principal.getUsername(), Event.LOGIN_SUCCEEDED,
            "LOGIN", null);
    }

    /**
     * ADM-11: every authenticated refusal. Depends on the {@code AuthorizationEventPublisher}
     * bean in {@code SecurityConfig} — without it Spring publishes nothing and this never runs.
     *
     * <p>Anonymous refusals are skipped: every unauthenticated page load is one, on its way to
     * the login page, and logging them would bury the refusals that mean something.
     */
    @EventListener
    public void onAccessDenied(AuthorizationDeniedEvent<?> event) {
        Authentication auth = event.getAuthentication().get();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return;
        }
        Long userId = auth.getPrincipal() instanceof FabricUserPrincipal p ? p.getUserId() : null;
        accessLog.record(userId, auth.getName(), Event.ACCESS_DENIED, describe(event.getSource()),
            String.valueOf(event.getAuthorizationResult()));
    }

    private static String describe(Object target) {
        if (target instanceof MethodInvocation invocation) {
            return invocation.getMethod().getDeclaringClass().getSimpleName()
                + "." + invocation.getMethod().getName();
        }
        if (target instanceof HttpServletRequest request) {
            return request.getMethod() + " " + request.getRequestURI();
        }
        return String.valueOf(target);
    }
}

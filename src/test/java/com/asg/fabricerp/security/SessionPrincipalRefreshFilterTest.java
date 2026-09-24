package com.asg.fabricerp.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * An open session must not outlive the decision that should have ended it: a revoked role, a
 * lock, a deletion. Before this filter the principal was fixed at login.
 */
class SessionPrincipalRefreshFilterTest {

    private FabricUserDetailsService users;
    private AccessLogService accessLog;
    private RememberMeServices rememberMe;
    private SessionPrincipalRefreshFilter filter;
    private FabricUser user;

    @BeforeEach
    void setUp() {
        users = mock(FabricUserDetailsService.class);
        accessLog = mock(AccessLogService.class);
        rememberMe = mock(RememberMeServices.class);
        filter = new SessionPrincipalRefreshFilter(users, accessLog, rememberMe);

        user = new FabricUser("merch", "hash", 10L, "AF");
        user.setId(5L);
        user.setOrganizationId(1L);
        user.setUnrestricted(true);
        Role role = new Role("Booking Maker", null);
        role.grant(Screen.BOOKING, Verb.CREATE);
        user.addRole(role);

        var atLogin = new FabricUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(atLogin, null, atLogin.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String path, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void aRoleRevokedSinceLoginStopsGrantingOnTheNextRequest() throws Exception {
        user.setRoles(java.util.Set.of());
        when(users.reload("merch")).thenReturn(Optional.of(new FabricUserPrincipal(user)));

        MockFilterChain chain = new MockFilterChain();
        run("/fabric/booking", chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .doesNotContain("SCREEN_BOOKING_CREATE");
    }

    @Test
    void aLockSinceLoginEndsTheSession() throws Exception {
        user.lock("Suspected shared credential", LocalDateTime.now());
        when(users.reload("merch")).thenReturn(Optional.of(new FabricUserPrincipal(user)));

        run("/fabric/booking", new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(rememberMe).loginFail(any(), any());
        verify(accessLog).record(eq(5L), eq("merch"), eq(AccessLogEntry.Event.SESSION_ENDED), any(), eq("Account locked"));
    }

    @Test
    void aDeletionSinceLoginEndsTheSession() throws Exception {
        when(users.reload("merch")).thenReturn(Optional.empty());

        run("/fabric/booking", new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aRestrictedUserWhoseLastScopeWasRevokedIsSignedOut() throws Exception {
        user.setUnrestricted(false);   // and no scope grants at all
        when(users.reload("merch")).thenReturn(Optional.of(new FabricUserPrincipal(user)));

        run("/fabric/booking", new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aTemporaryPasswordOnlyReachesTheChangePasswordPage() throws Exception {
        user.setPassword("hash", true, LocalDateTime.now());
        when(users.reload("merch")).thenReturn(Optional.of(new FabricUserPrincipal(user)));

        MockFilterChain blocked = new MockFilterChain();
        MockHttpServletResponse page = run("/fabric/booking", blocked);
        assertThat(blocked.getRequest()).isNull();
        assertThat(page.getRedirectedUrl()).isEqualTo("/account/password");

        MockHttpServletResponse api = run("/api/fabric/booking", new MockFilterChain());
        assertThat(api.getStatus()).isEqualTo(403);

        MockFilterChain allowed = new MockFilterChain();
        run("/account/password", allowed);
        assertThat(allowed.getRequest()).isNotNull();
    }
}

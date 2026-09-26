package com.asg.fabricerp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;

/**
 * Deny-by-default at the HTTP layer; every route's actual role requirement lives on the
 * controller method as {@code @PreAuthorize}, next to the code it guards.
 *
 * <p>This deliberately does not build SpindleERP's {@code DynamicAuthorizationManager} —
 * a URL-pattern permission table checked per request. That component's own comment records
 * why an unmatched route currently <b>grants</b> access and only logs a warning: flipping
 * to deny risked locking users out of a screen whose permission row had not been seeded.
 * That is a real, live fail-open path. Here there is no matching step to fall through:
 * {@code anyRequest().authenticated()} is the HTTP-level floor, and {@code @PreAuthorize}
 * is the only way in past it — a controller method with none is unreachable, not
 * ungoverned.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Signing key for the remember-me cookie. Must come from the environment
     * ({@code REMEMBER_ME_KEY}, e.g. {@code openssl rand -base64 48}) in every environment
     * that needs remember-me to survive a restart. Left unset, {@link #resolveRememberMeKey}
     * generates a random key for this process only, so remember-me still works locally
     * without a committed literal — restarting the app simply invalidates existing cookies.
     * Same rule as every other secret in this project: never a literal default here.
     */
    @Value("${REMEMBER_ME_KEY:}")
    private String rememberMeKey;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, FabricUserDetailsService userDetailsService,
                                    AccessLogService accessLog) throws Exception {
        TokenBasedRememberMeServices rememberMe = rememberMeServices(userDetailsService);
        http
            // After remember-me has had its chance to authenticate, before anything authorises:
            // every check downstream sees roles, lock state and scope as they are now.
            .addFilterAfter(new SessionPrincipalRefreshFilter(userDetailsService, accessLog, rememberMe),
                RememberMeAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/login", "/login/**", "/media/**").permitAll()
                // Exactly "/": visitors get the public company page, signed-in users the dashboard
                // (HomeController decides). Nothing below it is opened.
                .requestMatchers("/").permitAll()
                // The error page renders nothing but the status; without this an anonymous 404
                // is bounced to the login page instead of saying "not found".
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            // A grid's fetch() behind an expired session must see 401, not follow a redirect to
            // the login page and try to parse its HTML as JSON. app.js sends the user to /login.
            // One explicit entry point rather than one registered beside formLogin's: with two,
            // whichever registered first became the fallback for any request that matched
            // neither, and a page fetched without an HTML Accept header got a bare 401.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint()))
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(out -> out
                // logoutUrl() defaults to matching POST only, same as the explicit
                // AntPathRequestMatcher("/logout", "POST") this replaced — that matcher
                // class is deprecated in this Spring Security version, and there is no
                // need for it when the built-in default already does the same thing.
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .rememberMe(remember -> remember
                .rememberMeServices(rememberMe))
            // CSRF stays ON (Spring default). AJAX posts from the grid/form JS must send
            // the token — see the meta tags in templates/layout/main.html.
            .headers(h -> h
                .frameOptions(f -> f.sameOrigin())
                // Document numbers and ids sit in URLs; they need not travel to other sites.
                .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    /** {@code /api/**} answers 401; everything else is sent to the login page. */
    private static AuthenticationEntryPoint entryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> byPath = new LinkedHashMap<>();
        byPath.put(PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        var delegating = new DelegatingAuthenticationEntryPoint(byPath);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return delegating;
    }

    private TokenBasedRememberMeServices rememberMeServices(FabricUserDetailsService userDetailsService) {
        var services = new TokenBasedRememberMeServices(resolveRememberMeKey(rememberMeKey), userDetailsService);
        services.setTokenValiditySeconds(14 * 24 * 60 * 60); // 14 days
        return services;
    }

    private static String resolveRememberMeKey(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * The login provider, declared so it can refuse one more case than Spring's default:
     * ADM-3's restricted user with no scope. Not configured is not unrestricted — letting the
     * login succeed would only move the failure into every grid, where it reads as "no data"
     * instead of naming the cause.
     *
     * <p>A post-authentication check, so it runs only after the password matched: the message
     * is for the account's owner, not for someone guessing usernames. A single
     * {@code AuthenticationProvider} bean is what the global authentication manager uses, so
     * this replaces the default rather than sitting beside it — a second provider would be
     * tried after this one refused, and would let the same login straight through.
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(FabricUserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPostAuthenticationChecks(user -> {
            if (user instanceof FabricUserPrincipal principal && !principal.getRowScope().isConfigured()) {
                throw new DisabledException(
                    "No data scope has been set up for this account yet. Ask an administrator.");
            }
        });
        return provider;
    }

    /**
     * Makes {@code @PreAuthorize} refusals publish an event, which is how
     * {@link SecurityEventListener#onAccessDenied} writes them to the ADM-11 log. Without this
     * bean method security uses a no-op publisher and refusals leave no trace.
     */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }
}

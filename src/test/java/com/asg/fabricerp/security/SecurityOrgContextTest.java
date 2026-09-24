package com.asg.fabricerp.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The behaviour {@link com.asg.fabricerp.global.documents.DocumentNumberService} and every
 * {@code save()} in the fabric services depend on: the context must resolve to the
 * authenticated user's scope, and must fail loudly rather than silently scoping to nothing
 * when there is no authentication.
 */
class SecurityOrgContextTest {

    private final SecurityOrgContext context = new SecurityOrgContext();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private FabricUser userWithScope() {
        FabricUser user = new FabricUser("weaver1", "{noop}unused", 10L, "AF");
        user.setOrganizationId(1L);
        user.setWarehouseId(99L);
        user.grant("ROLE_BOOKING_MAKER");
        return user;
    }

    @Test
    void resolvesScopeFromTheAuthenticatedPrincipal() {
        var principal = new FabricUserPrincipal(userWithScope());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThat(context.organizationId()).isEqualTo(1L);
        assertThat(context.businessUnitId()).isEqualTo(10L);
        assertThat(context.businessUnitCode()).isEqualTo("AF");
        assertThat(context.warehouseId()).isEqualTo(99L);
        assertThat(context.username()).isEqualTo("weaver1");
    }

    @Test
    void returnsNullWhenNobodyIsAuthenticated() {
        assertThat(context.organizationId()).isNull();
        assertThat(context.businessUnitId()).isNull();
        assertThat(context.username()).isNull();
    }

    @Test
    void requireMethodsFailLoudlyRatherThanScopingToNothing() {
        assertThatThrownBy(context::requireOrganizationId).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(context::requireBusinessUnitId).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(context::requireBusinessUnitCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ignoresAnAuthenticationThatIsNotOurPrincipalType() {
        // Spring Security's own anonymous/default principal shapes must not be mistaken
        // for a FabricUserPrincipal and silently produce a wrong scope.
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("plain-string-principal", null, java.util.List.of()));

        assertThat(context.organizationId()).isNull();
        assertThat(context.username()).isNull();
    }
}

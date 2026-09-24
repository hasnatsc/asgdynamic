package com.asg.fabricerp.security;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@link OrgContext} backed by the authenticated {@link FabricUserPrincipal}.
 *
 * <p>A stateless singleton, not request/session scoped: every accessor reads
 * {@code SecurityContextHolder} fresh on each call rather than caching anything in an
 * instance field, so there is nothing here to go stale between requests on the same
 * thread pool. {@code SecurityContextHolder} itself is thread-local per request, which is
 * what makes that safe.
 *
 * <p>Outside a request (a scheduler, a startup seeder) there is no authentication and every
 * accessor returns {@code null} — callers that need a value should use the {@code requireX}
 * default methods on {@link OrgContext}, which fail loudly rather than silently scoping to
 * nothing.
 */
@Component
public class SecurityOrgContext implements OrgContext {

    @Override
    public Long organizationId() {
        return principal().map(FabricUserPrincipal::getOrganizationId).orElse(null);
    }

    @Override
    public Long businessUnitId() {
        return principal().map(FabricUserPrincipal::getBusinessUnitId).orElse(null);
    }

    @Override
    public String businessUnitCode() {
        return principal().map(FabricUserPrincipal::getBusinessUnitCode).orElse(null);
    }

    @Override
    public Long warehouseId() {
        return principal().map(FabricUserPrincipal::getWarehouseId).orElse(null);
    }

    @Override
    public String username() {
        return principal().map(FabricUserPrincipal::getUsername).orElse(null);
    }

    private java.util.Optional<FabricUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return java.util.Optional.empty();
        Object p = auth.getPrincipal();
        return p instanceof FabricUserPrincipal fp ? java.util.Optional.of(fp) : java.util.Optional.empty();
    }
}

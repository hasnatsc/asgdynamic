package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BusinessUnitRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs background work - the approval deadline job - as {@code system}, working in one
 * organization and business unit.
 *
 * <p>The engine and the listeners an approval sets off read the organization and unit from the
 * signed-in principal ({@link SecurityOrgContext}); a scheduler thread has none, so they would fail.
 * This gives them one for the duration of the call: no user id, no authorities, unrestricted row
 * scope, and the username {@code system}, which is what the audit columns and the document's
 * timeline show. It never outlives the call, and it grants no screen - code that asks
 * {@link AuthorityChecks} is refused, as it should be.
 */
@Component
public class SystemActor {

    public static final String USERNAME = "system";

    private final BusinessUnitRepository units;

    public SystemActor(BusinessUnitRepository units) {
        this.units = units;
    }

    public <T> T run(Long organizationId, Long businessUnitId, Supplier<T> work) {
        String unitCode = businessUnitId == null ? null
            : units.findById(businessUnitId).map(u -> u.getCode()).orElse(null);
        FabricUser system = new FabricUser(USERNAME, "", businessUnitId, unitCode);
        system.setOrganizationId(organizationId);
        system.setUnrestricted(true);
        FabricUserPrincipal principal = new FabricUserPrincipal(system, List.of(), LocalDate.now(),
            new Workspace(organizationId, null, null, businessUnitId, unitCode, null, null, null, null));

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public void run(Long organizationId, Long businessUnitId, Runnable work) {
        run(organizationId, businessUnitId, () -> {
            work.run();
            return null;
        });
    }
}

package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The person acting on an approval: their id and username, the roles they hold (a role-based level
 * asks about those) and their authorities (the default rule asks about those).
 *
 * <p>Roles are read from the user row rather than carried on the principal: a role granted or
 * withdrawn this morning must count on this afternoon's approval, not at the next sign-in.
 */
@Component
public class ApprovalActors {

    private final FabricUserRepository users;
    private final OrgContext context;

    public ApprovalActors(FabricUserRepository users, OrgContext context) {
        this.users = users;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Approver.Actor current() {
        Long userId = CurrentUser.id();
        Set<Long> roleIds = userId == null ? Set.of() : users.findScoped(userId, context.requireOrganizationId())
            .map(u -> u.getRoles().stream()
                .filter(r -> !Boolean.FALSE.equals(r.getActive()))
                .map(Role::getId)
                .collect(Collectors.toSet()))
            .orElse(Set.of());
        return new Approver.Actor(userId, context.username(), roleIds, AuthorityChecks.heldAuthorities());
    }
}

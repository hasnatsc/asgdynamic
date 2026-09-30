package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.security.DataScope;
import com.asg.fabricerp.security.DataScopeRepository;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserPrincipal;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.Screen;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The people a notification about a document goes to - by the same rule the engine lets them act.
 *
 * <p>A named user is that person. A role, or the default rule (anyone with Approve on the screen), is
 * every open account holding it whose row scope lets them see the document: a team's documents are
 * announced to that team's approvers, never to the holder of the same role in another team. The
 * maker is never told to approve their own document.
 */
@Component
public class ApprovalRecipients {

    private final EntityManager em;
    private final FabricUserRepository users;
    private final DataScopeRepository scopes;

    public ApprovalRecipients(EntityManager em, FabricUserRepository users, DataScopeRepository scopes) {
        this.em = em;
        this.users = users;
        this.scopes = scopes;
    }

    /** Who may decide the level {@code approver} names on {@code doc}, less whoever raised it. */
    public Set<Long> approvers(Approver approver, BusinessDocument doc, ApprovalRequest request) {
        if (approver == null) return Set.of();
        Set<Long> ids = switch (approver.kind()) {
            case USER -> approver.userId() == null ? Set.of() : Set.of(approver.userId());
            case ROLE -> visible(doc, em.createQuery("""
                    select u from FabricUser u join u.roles r
                    where r.id = :roleId and r.active = true and u.organizationId = :orgId
                      and u.deleted = false and u.active = true and u.accountLocked = false
                    """, FabricUser.class)
                .setParameter("roleId", approver.roleId())
                .setParameter("orgId", request.getOrganizationId())
                .getResultList());
            case AUTHORITY -> {
                Screen screen = screenOf(request);
                yield screen == null ? Set.of() : visible(doc, em.createQuery("""
                        select distinct u from FabricUser u join u.roles r join r.screenGrants g
                        where g.screen = :screen and g.canApprove = true and r.active = true
                          and u.organizationId = :orgId
                          and u.deleted = false and u.active = true and u.accountLocked = false
                        """, FabricUser.class)
                    .setParameter("screen", screen)
                    .setParameter("orgId", request.getOrganizationId())
                    .getResultList());
            }
        };
        Long maker = maker(request);
        return ids.stream().filter(id -> !id.equals(maker)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Who raised the request - by id, or by username for one enrolled before ids were kept. */
    public Long maker(ApprovalRequest request) {
        if (request.getRaisedByUserId() != null) return request.getRaisedByUserId();
        if (request.getRaisedBy() == null) return null;
        return users.findByUsernameIgnoreCaseAndDeletedFalse(request.getRaisedBy()).map(FabricUser::getId).orElse(null);
    }

    private Set<Long> visible(BusinessDocument doc, List<FabricUser> candidates) {
        if (candidates.isEmpty()) return Set.of();
        LocalDate today = LocalDate.now();
        Map<Long, List<DataScope>> grants = scopes.findByUserIdIn(candidates.stream().map(FabricUser::getId).toList())
            .stream().collect(Collectors.groupingBy(DataScope::getUserId));
        Set<Long> out = new LinkedHashSet<>();
        for (FabricUser u : candidates) {
            RowScope scope = FabricUserPrincipal.resolveScope(u, grants.getOrDefault(u.getId(), List.of()), today);
            if (scope.isConfigured() && doc.isVisibleTo(scope)) out.add(u.getId());
        }
        return out;
    }

    private static Screen screenOf(ApprovalRequest request) {
        try {
            return Screen.valueOf(request.getDocumentType().roleRoot());
        } catch (RuntimeException noScreen) {
            return null;
        }
    }
}

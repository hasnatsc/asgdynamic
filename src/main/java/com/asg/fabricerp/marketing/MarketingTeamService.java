package com.asg.fabricerp.marketing;

import com.asg.fabricerp.approval.ApprovalMatrixRepository;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.security.DataScope;
import com.asg.fabricerp.security.DataScopeRepository;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.UserAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * The Marketing teams screen - ADM-4's master and its members. Who approves a team's documents is
 * its team-wise approval matrix, on the Approval matrices screen.
 *
 * <h2>Members are data-scope grants</h2>
 * Adding a member is {@link UserAdminService#grantScope} on {@code MARKETING_TEAM}, and removing
 * one is {@link UserAdminService#revokeScope} - not a second membership list. That keeps every
 * rule the grant already enforces: one open team per user, no granting yourself, and a dated
 * history of who was in which team when.
 *
 * <h2>Retire, don't delete</h2>
 * A team that owns documents or has members is deactivated, never deleted: its stamp is
 * permanent on everything raised under it (ADM-7), and a deleted team would leave those
 * documents naming nothing.
 */
@Service
public class MarketingTeamService {

    private final MarketingTeamRepository teams;
    private final ApprovalMatrixRepository matrices;
    private final DataScopeRepository scopes;
    private final FabricUserRepository users;
    private final UserAdminService userAdmin;
    private final OrgContext context;

    public MarketingTeamService(MarketingTeamRepository teams, ApprovalMatrixRepository matrices,
                                DataScopeRepository scopes, FabricUserRepository users,
                                UserAdminService userAdmin, OrgContext context) {
        this.teams = teams;
        this.matrices = matrices;
        this.scopes = scopes;
        this.users = users;
        this.userAdmin = userAdmin;
        this.context = context;
    }

    /** A member, as the screen lists them. {@code id} is their team grant. */
    public record Person(Long id, Long userId, String username, String fullName, LocalDate since,
                         boolean unrestricted, boolean usable) { }

    // ------------------------------------------------------------------------------ the team

    @Transactional(readOnly = true)
    public Page<MarketingTeam> search(String q, Boolean active, Pageable pageable) {
        return teams.search(context.requireOrganizationId(), q, active, pageable);
    }

    @Transactional(readOnly = true)
    public MarketingTeam get(Long id) {
        return teams.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Marketing team not found: " + id));
    }

    @Transactional
    public MarketingTeam save(MarketingTeam submitted) {
        Long orgId = context.requireOrganizationId();
        String code = trim(submitted.getCode());
        String name = trim(submitted.getName());
        if (code == null || name == null) {
            throw new IllegalArgumentException("A marketing team needs a code and a name");
        }
        if (teams.codeTaken(orgId, code, submitted.getId())) {
            throw new IllegalStateException("Another marketing team already uses the code " + code);
        }

        MarketingTeam target;
        if (submitted.getId() == null) {
            target = new MarketingTeam(code, name);
            target.setOrganizationId(orgId);
        } else {
            target = get(submitted.getId());
            target.setCode(code);
            target.setName(name);
        }
        target.setBookingTarget(submitted.getBookingTarget());
        target.setRemarks(trim(submitted.getRemarks()));
        target.setActive(submitted.getActive() == null || submitted.getActive());

        Long leader = submitted.getLeaderUserId();
        if (leader != null && (target.getId() == null || !isMember(target.getId(), leader))) {
            throw new IllegalArgumentException("The team leader must be one of the team's members - add them first");
        }
        target.setLeaderUserId(leader);
        return teams.save(target);
    }

    @Transactional
    public void delete(Long id) {
        MarketingTeam team = get(id);
        long documents = teams.countDocuments(id);
        if (documents > 0) {
            throw new IllegalStateException(("%s owns %d document(s) and cannot be deleted - the team stays on "
                + "everything raised under it. Deactivate it instead.").formatted(team.getName(), documents));
        }
        if (matrices.countActiveForTeam(id) > 0) {
            throw new IllegalStateException("%s has its own approval matrices. Deactivate them, or deactivate the team instead."
                .formatted(team.getName()));
        }
        if (!members(id).isEmpty()) {
            throw new IllegalStateException("%s still has members. Remove them, or deactivate the team instead."
                .formatted(team.getName()));
        }
        team.markDeleted();
        teams.save(team);
    }

    // ------------------------------------------------------------------------------ members

    @Transactional(readOnly = true)
    public List<Person> members(Long teamId) {
        get(teamId);
        return scopes.findHoldersOn(ScopeDimension.MARKETING_TEAM, teamId, LocalDate.now()).stream()
            .map(scope -> person(scope.getId(), user(scope.getUserId()), scope.getGrantedFrom()))
            .toList();
    }

    /**
     * Puts a user in the team from a date. An unrestricted user is refused: they see every team
     * already, and a team grant would narrow nothing - it would only read as if it did.
     */
    @Transactional
    public void addMember(Long teamId, Long userId, LocalDate from) {
        MarketingTeam team = get(teamId);
        if (!team.getActive()) {
            throw new IllegalStateException(team.getName() + " is inactive and takes no new members");
        }
        FabricUser user = user(userId);
        if (user.isUnrestricted()) {
            throw new IllegalStateException(("%s is unrestricted and sees every team's documents. Team membership "
                + "only narrows a restricted user - make them one first, under Security › Users.")
                .formatted(display(user)));
        }
        userAdmin.grantScope(userId, ScopeDimension.MARKETING_TEAM, teamId, from, "Added on the Marketing teams screen");
    }

    /** Ends a membership from today. The grant row survives, so the history still answers. */
    @Transactional
    public void removeMember(Long teamId, Long scopeId, String reason) {
        MarketingTeam team = get(teamId);
        DataScope scope = scopes.findById(scopeId)
            .filter(s -> s.getDimension() == ScopeDimension.MARKETING_TEAM && teamId.equals(s.getScopeValueId()))
            .orElseThrow(() -> new IllegalArgumentException("That person is not a member of " + team.getName()));
        userAdmin.revokeScope(scopeId, LocalDate.now(),
            reason == null || reason.isBlank() ? "Removed on the Marketing teams screen" : reason.trim());
        if (scope.getUserId().equals(team.getLeaderUserId())) {
            team.setLeaderUserId(null);
            teams.save(team);
        }
    }

    // ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public int memberCount(Long teamId) {
        return scopes.findHoldersOn(ScopeDimension.MARKETING_TEAM, teamId, LocalDate.now()).size();
    }

    /** The team's own active approval matrices - none means its documents follow the unit-wide ones. */
    @Transactional(readOnly = true)
    public long matrixCount(Long teamId) {
        return matrices.countActiveForTeam(teamId);
    }

    @Transactional(readOnly = true)
    public String userName(Long userId) {
        return userId == null ? null : users.findScoped(userId, context.requireOrganizationId())
            .map(MarketingTeamService::display).orElse(null);
    }

    private boolean isMember(Long teamId, Long userId) {
        return scopes.findHoldersOn(ScopeDimension.MARKETING_TEAM, teamId, LocalDate.now()).stream()
            .anyMatch(s -> s.getUserId().equals(userId));
    }

    private FabricUser user(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Choose a user");
        }
        return users.findScoped(userId, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private static Person person(Long id, FabricUser user, LocalDate since) {
        boolean usable = Boolean.TRUE.equals(user.getActive()) && !Boolean.TRUE.equals(user.getAccountLocked());
        return new Person(id, user.getId(), user.getUsername(), display(user), since, user.isUnrestricted(), usable);
    }

    private static String display(FabricUser user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}

package com.asg.fabricerp.analytics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Whose bookings one person's analytics may cover, and why - resolved from who they are, never
 * from the request.
 *
 * <ul>
 *   <li><b>Everyone</b> (team members included) - {@link AnalyticsView#MINE}: the bookings they
 *       raised or are the marketing person on.</li>
 *   <li><b>Supervisors and approvers</b> - {@link AnalyticsView#TEAM}: the teams they lead
 *       ({@code leader_user_id}) and the teams whose bookings they approve, exactly as the approval
 *       engine routes them: a level naming them personally, or a role they hold / the Booking
 *       Approve verb for their own teams.</li>
 *   <li><b>Management</b> - {@link AnalyticsView#ALL}: users whose row scope does not narrow
 *       marketing teams see every team in the business unit, as they do on every other screen.</li>
 * </ul>
 *
 * @param teamIds the teams {@link AnalyticsView#TEAM} covers; empty when that view is not offered
 */
public record AnalyticsScope(Long userId, String username, List<AnalyticsView> views, Set<Long> teamIds,
                             List<String> reasons) {

    public AnalyticsScope {
        views = List.copyOf(views);
        teamIds = Set.copyOf(teamIds);
        reasons = List.copyOf(reasons);
    }

    /** The broadest view offered - what the screen opens on. */
    public AnalyticsView defaultView() {
        return views.getLast();
    }

    public boolean offers(AnalyticsView view) {
        return view != null && views.contains(view);
    }

    /** One level of an approval matrix: a role or one user. */
    public record MatrixLevel(Long roleId, Long userId) { }

    /** An active Booking approval matrix: a team's own ({@code teamId}) or the unit-wide one (null). */
    public record Matrix(Long teamId, List<MatrixLevel> levels) { }

    /**
     * The rules, free of any repository so they can be tested on their own.
     *
     * @param management    the user's row scope does not narrow marketing teams
     * @param memberTeams   the teams the user belongs to (their MARKETING_TEAM grants)
     * @param ledTeams      the teams the user leads
     * @param matrices      the active Booking approval matrices of the business unit
     * @param roleIds       the user's active roles
     * @param approveVerb   whether the user holds Booking Approve (the default rule, where no matrix applies)
     * @param allTeams      every active team of the organization
     */
    public static AnalyticsScope resolve(Long userId, String username, boolean management,
                                         Set<Long> memberTeams, Set<Long> ledTeams, List<Matrix> matrices,
                                         Set<Long> roleIds, boolean approveVerb, Set<Long> allTeams) {
        List<AnalyticsView> views = new ArrayList<>(List.of(AnalyticsView.MINE));
        List<String> reasons = new ArrayList<>();
        if (management) {
            views.add(AnalyticsView.ALL);
            reasons.add("You see every marketing team");
            return new AnalyticsScope(userId, username, views, Set.of(), reasons);
        }

        Set<Long> teams = new LinkedHashSet<>();
        if (!ledTeams.isEmpty()) {
            teams.addAll(ledTeams);
            reasons.add("You lead " + (ledTeams.size() == 1 ? "a team" : ledTeams.size() + " teams"));
        }

        Set<Long> withOwnMatrix = new LinkedHashSet<>();
        Matrix unitWide = null;
        for (Matrix m : matrices) {
            if (m.teamId() == null) unitWide = m;
            else withOwnMatrix.add(m.teamId());
        }
        Set<Long> approved = new LinkedHashSet<>();
        for (Matrix m : matrices) {
            if (m.teamId() == null) continue;
            for (MatrixLevel level : m.levels()) {
                boolean named = level.userId() != null && level.userId().equals(userId);
                boolean byRole = level.roleId() != null && roleIds.contains(level.roleId()) && memberTeams.contains(m.teamId());
                if (named || byRole) approved.add(m.teamId());
            }
        }
        // Teams with no matrix of their own follow the unit-wide one, or the default rule without it.
        Set<Long> fallbackTeams = new LinkedHashSet<>();
        if (unitWide != null) {
            for (MatrixLevel level : unitWide.levels()) {
                if (level.userId() != null && level.userId().equals(userId)) fallbackTeams.addAll(allTeams);
                if (level.roleId() != null && roleIds.contains(level.roleId())) fallbackTeams.addAll(memberTeams);
            }
        } else if (approveVerb) {
            fallbackTeams.addAll(memberTeams);
        }
        fallbackTeams.removeAll(withOwnMatrix);
        approved.addAll(fallbackTeams);
        if (!approved.isEmpty()) {
            teams.addAll(approved);
            reasons.add("You approve bookings for " + (approved.size() == 1 ? "a team" : approved.size() + " teams"));
        }

        if (!teams.isEmpty()) views.add(AnalyticsView.TEAM);
        if (reasons.isEmpty()) reasons.add("You see the bookings you raised");
        return new AnalyticsScope(userId, username, views, teams, reasons);
    }
}

package com.asg.fabricerp.analytics;

import com.asg.fabricerp.analytics.AnalyticsScope.Matrix;
import com.asg.fabricerp.analytics.AnalyticsScope.MatrixLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Who sees whose bookings: members their own, supervisors and approvers their teams, management all. */
class AnalyticsScopeTest {

    private static final Long ME = 7L;
    private static final Set<Long> ALL_TEAMS = Set.of(1L, 2L, 3L, 4L);

    private static AnalyticsScope resolve(boolean management, Set<Long> member, Set<Long> led, List<Matrix> matrices,
                                          Set<Long> roles, boolean approveVerb) {
        return AnalyticsScope.resolve(ME, "me", management, member, led, matrices, roles, approveVerb, ALL_TEAMS);
    }

    @Test
    void managementSeesEveryTeamAndOpensOnIt() {
        AnalyticsScope s = resolve(true, Set.of(), Set.of(), List.of(), Set.of(), false);
        assertEquals(List.of(AnalyticsView.MINE, AnalyticsView.ALL), s.views());
        assertEquals(AnalyticsView.ALL, s.defaultView());
    }

    @Test
    void aTeamMemberSeesOnlyTheirOwnBookings() {
        AnalyticsScope s = resolve(false, Set.of(1L), Set.of(), List.of(), Set.of(), false);
        assertEquals(List.of(AnalyticsView.MINE), s.views());
        assertTrue(s.teamIds().isEmpty());
        assertFalse(s.offers(AnalyticsView.TEAM));
        assertFalse(s.offers(AnalyticsView.ALL));
    }

    @Test
    void aTeamLeaderSeesTheTeamsTheyLead() {
        AnalyticsScope s = resolve(false, Set.of(1L), Set.of(1L, 2L), List.of(), Set.of(), false);
        assertEquals(AnalyticsView.TEAM, s.defaultView());
        assertEquals(Set.of(1L, 2L), s.teamIds());
    }

    @Test
    void aPersonNamedOnAnotherTeamsMatrixSeesThatTeam() {
        List<Matrix> matrices = List.of(new Matrix(3L, List.of(new MatrixLevel(null, ME))));
        AnalyticsScope s = resolve(false, Set.of(1L), Set.of(), matrices, Set.of(), false);
        assertEquals(Set.of(3L), s.teamIds());
    }

    @Test
    void aRoleOnATeamMatrixCountsOnlyForThatTeamsOwnMembers() {
        List<Matrix> matrices = List.of(new Matrix(3L, List.of(new MatrixLevel(50L, null))),
                                        new Matrix(1L, List.of(new MatrixLevel(50L, null))));
        AnalyticsScope s = resolve(false, Set.of(1L), Set.of(), matrices, Set.of(50L), false);
        assertEquals(Set.of(1L), s.teamIds(), "the same role in team 3 approves team 3, not this person");
    }

    @Test
    void theUnitWideMatrixCoversOnlyTeamsWithoutTheirOwn() {
        List<Matrix> matrices = List.of(new Matrix(null, List.of(new MatrixLevel(50L, null))),
                                        new Matrix(2L, List.of(new MatrixLevel(60L, null))));
        AnalyticsScope s = resolve(false, Set.of(1L, 2L), Set.of(), matrices, Set.of(50L), false);
        assertEquals(Set.of(1L), s.teamIds(), "team 2 is approved under its own matrix");
    }

    @Test
    void withNoMatrixTheApproveVerbCoversTheirOwnTeams() {
        AnalyticsScope s = resolve(false, Set.of(2L), Set.of(), List.of(), Set.of(), true);
        assertEquals(Set.of(2L), s.teamIds());
        assertTrue(s.offers(AnalyticsView.TEAM));
    }
}

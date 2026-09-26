package com.asg.fabricerp.analytics;

import com.asg.fabricerp.analytics.BookingAnalyticsSql.Criteria;
import com.asg.fabricerp.analytics.BookingAnalyticsSql.Grain;
import com.asg.fabricerp.analytics.BookingAnalyticsSql.Query;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The statements' shape: scope and filters are always bound parameters, never spliced text; the
 * current revision alone is counted; the period comparison is like for like. (Every statement was
 * also run against PostgreSQL with the project's migrations while this was written.)
 */
class BookingAnalyticsSqlTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1), TO = LocalDate.of(2026, 3, 31);

    private static Criteria criteria(AnalyticsView view, List<Long> teams, String currency, String search) {
        return new Criteria(1, 10, FROM, TO, view, "rahim", 7L, teams, null, null, null, currency, null, null, search);
    }

    @Test
    void myBookingsAreTheOnesIRaisedOrAmTheMarketingPersonOn() {
        Query q = BookingAnalyticsSql.kpis(criteria(AnalyticsView.MINE, List.of(), "USD", null));
        assertTrue(q.sql().contains("d.created_by = :username OR d.marketing_person_id = :userId"));
        assertEquals("rahim", q.params().get("username"));
        assertEquals(7L, q.params().get("userId"));
    }

    @Test
    void aTeamViewWithNoTeamsMatchesNothingRatherThanEverything() {
        Query q = BookingAnalyticsSql.kpis(criteria(AnalyticsView.TEAM, List.of(), null, null));
        assertEquals(List.of(-1L), q.params().get("teamIds"));
    }

    @Test
    void theAllTeamsViewAddsNoTeamCondition() {
        Query q = BookingAnalyticsSql.kpis(criteria(AnalyticsView.ALL, List.of(), null, null));
        assertFalse(q.sql().contains("marketing_team_id IN"));
        assertFalse(q.sql().contains(":username"));
    }

    @Test
    void onlyTheLatestRevisionOfABookingCounts() {
        Query q = BookingAnalyticsSql.kpis(criteria(AnalyticsView.ALL, List.of(), null, null));
        assertTrue(q.sql().contains("n.revision_of_id = COALESCE(d.revision_of_id, d.id)"));
        assertTrue(q.sql().contains("n.revision_no > d.revision_no"));
    }

    @Test
    void filterValuesAreBoundNeverSpliced() {
        String hostile = "USD' OR 1=1 --";
        Query q = BookingAnalyticsSql.kpis(criteria(AnalyticsView.ALL, List.of(), hostile, "x'); DROP TABLE t; --"));
        assertFalse(q.sql().contains(hostile));
        assertFalse(q.sql().contains("DROP TABLE"));
        assertEquals(hostile, q.params().get("currency"));
    }

    @Test
    void searchWildcardsAreEscaped() {
        Query q = BookingAnalyticsSql.registerCount(criteria(AnalyticsView.ALL, List.of(), null, "50%_off"));
        assertEquals("%50\\%\\_off%", q.params().get("search"));
    }

    @Test
    void anUnknownSortColumnFallsBackToTheBookingDate() {
        Query q = BookingAnalyticsSql.register(criteria(AnalyticsView.ALL, List.of(), null, null), "1; DROP TABLE x", true, 25, 0);
        assertTrue(q.sql().contains("ORDER BY b.document_date ASC"));
        assertFalse(q.sql().contains("DROP TABLE"));
    }

    @Test
    void thePreviousPeriodIsTheSameLengthEndingTheDayBefore() {
        Criteria prev = criteria(AnalyticsView.ALL, List.of(), null, null).previousPeriod();
        assertEquals(LocalDate.of(2025, 10, 3), prev.from());
        assertEquals(LocalDate.of(2025, 12, 31), prev.to());
    }

    @Test
    void theTrendGrainFollowsThePeriodLength() {
        assertEquals(Grain.DAY, Grain.forPeriod(FROM, FROM.plusDays(30)));
        assertEquals(Grain.WEEK, Grain.forPeriod(FROM, FROM.plusDays(120)));
        assertEquals(Grain.MONTH, Grain.forPeriod(FROM, FROM.plusDays(364)));
    }

    @Test
    void forwardLookingViewsIgnoreTheBookingPeriod() {
        Query q = BookingAnalyticsSql.deliverySchedule(criteria(AnalyticsView.ALL, List.of(), null, null), TO);
        assertFalse(q.sql().contains("d.document_date BETWEEN"));
    }
}

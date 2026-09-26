package com.asg.fabricerp.analytics;

/**
 * Whose bookings an analytics view covers. Which of these a person may pick is decided by
 * {@link AnalyticsScopeResolver} from who they are, never by the request.
 */
public enum AnalyticsView {
    /** The bookings they raised or are the marketing person on - every team member. */
    MINE("My bookings"),
    /** The teams they lead or approve for - supervisors and approvers. */
    TEAM("My teams"),
    /** Every team in the business unit - management. */
    ALL("All teams");

    private final String label;

    AnalyticsView(String label) { this.label = label; }

    public String label() { return label; }
}

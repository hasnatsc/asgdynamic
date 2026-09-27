package com.asg.fabricerp.approval;

/** What happened, in one {@link ApprovalHistory} row per event. */
public enum ApprovalAction {
    SUBMITTED,
    /** Submitted again after a Reject, corrected by the maker. */
    RESUBMITTED,
    /**
     * A level before the last signed on a type with a checker stage - the legacy PI/LC/CI checker.
     * Whoever checked a request may sign no later level of it.
     */
    CHECKED,
    APPROVED,
    /** Sent back to the maker as a draft to correct - asfl-erp's RETURNED. */
    RETURNED,
    REJECTED,
    /** A store document posted to the stock ledger in one step - it is not approved, it happened. */
    POSTED,
    /** Cancelled with a reason; a posted document's stock moves are reversed. */
    CANCELLED,
    /** A line's remaining balance given up, with a reason. */
    SHORT_CLOSED,
    /** A completed document closed by hand, or a dyeing batch closed with its loss measured. */
    CLOSED,
    /** A revision took over from this version once it was approved. */
    SUPERSEDED
}

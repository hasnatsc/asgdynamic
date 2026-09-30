package com.asg.fabricerp.approval;

/** What happened, in one {@link ApprovalHistory} row per event. */
public enum ApprovalAction {
    SUBMITTED,
    /** Submitted again after a Reject, corrected by the maker. */
    RESUBMITTED,
    APPROVED,
    /** Sent back to the maker as a draft to correct - asfl-erp's RETURNED. */
    RETURNED,
    REJECTED,
    /** A level's time limit ran out and it was handed to its escalation role or person. */
    ESCALATED,
    /** A level's time limit ran out; it is still waiting for the same approver. */
    OVERDUE,
    /** A store document posted to the stock ledger in one step - it is not approved, it happened. */
    POSTED,
    /** Cancelled with a reason; a posted document's stock moves are reversed. */
    CANCELLED,
    /** A line's remaining balance given up, with a reason. */
    SHORT_CLOSED,
    /** A completed document closed by hand, or a dyeing batch closed with its loss measured. */
    CLOSED,
    /** A revision took over from this version once it was approved. */
    SUPERSEDED,
    /** Posted to the ledger when approved - or why it was not (an export CI's invoice). */
    ACCOUNTED,
    /** An export CI's final payment received: it is realized. */
    REALIZED,
    /** A CI's final payment taken back, with a reason; its receipt is reversed. */
    REALIZATION_UNDONE
}

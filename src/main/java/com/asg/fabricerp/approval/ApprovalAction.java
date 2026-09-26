package com.asg.fabricerp.approval;

/** What happened, in one {@link ApprovalHistory} row per event. */
public enum ApprovalAction {
    SUBMITTED,
    /** Submitted again after a Reject, corrected by the maker. */
    RESUBMITTED,
    APPROVED,
    /** Sent back to the maker as a draft to correct - asfl-erp's RETURNED. */
    RETURNED,
    REJECTED
}

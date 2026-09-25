package com.asg.fabricerp.approval;

/** What happened, in one {@link ApprovalHistory} row per event. */
public enum ApprovalAction {
    SUBMITTED,
    APPROVED,
    /** Sent back to the maker as a draft to correct - asfl-erp's RETURNED. */
    RETURNED,
    REJECTED
}

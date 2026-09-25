package com.asg.fabricerp.approval;

/**
 * What an approver decides at one level - asfl-erp's {@code ApprovalDecision}.
 *
 * <p>{@link #APPROVED} moves the request to its next level, or approves the document at the last.
 * {@link #RETURNED} sends it back to the maker as a draft to correct; {@link #REJECTED} refuses it.
 * Both refusals end the request and must say why: the maker has no other way to know what to change.
 */
public enum ApprovalDecision {
    APPROVED,
    RETURNED,
    REJECTED;

    public boolean isRefusal() {
        return this != APPROVED;
    }
}

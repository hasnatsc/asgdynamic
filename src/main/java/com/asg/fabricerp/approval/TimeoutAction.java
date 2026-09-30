package com.asg.fabricerp.approval;

/**
 * What happens when a level's time limit runs out with nobody having decided it.
 *
 * <p>Whatever the action, the level's approvers and the maker are told it is overdue, and the
 * document's timeline records what was done and why, signed {@code system}.
 */
public enum TimeoutAction {
    /** Tell everyone it is overdue and keep waiting for the same approver. */
    REMIND("Remind and keep waiting"),
    /** Hand the level to another role or person, who decides it from then on. */
    ESCALATE("Escalate"),
    /** The system signs the level; at the last level the document is approved. */
    AUTO_APPROVE("Approve automatically"),
    /** Back to the maker as a draft, as a Return would. */
    AUTO_RETURN("Return to the maker"),
    /** Refused, as a Reject would. */
    AUTO_REJECT("Reject automatically");

    private final String label;

    TimeoutAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The decision the system takes on the level, when the action is one. */
    public ApprovalDecision decision() {
        return switch (this) {
            case AUTO_APPROVE -> ApprovalDecision.APPROVED;
            case AUTO_RETURN  -> ApprovalDecision.RETURNED;
            case AUTO_REJECT  -> ApprovalDecision.REJECTED;
            default -> null;
        };
    }
}

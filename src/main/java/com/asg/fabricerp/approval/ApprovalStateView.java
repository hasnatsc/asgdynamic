package com.asg.fabricerp.approval;

/**
 * Where one document's approval stands, as its screen shows it - asfl-erp's {@code ApprovalState}.
 *
 * @param requestId     the request, or null when never submitted (or submitted before the engine)
 * @param pending       whether a decision is still awaited
 * @param level         the level awaiting a decision (the last one reached, once settled)
 * @param totalLevels   how many levels the document's amount needs
 * @param matrixName    the matrix it was submitted under, or null for the default rule
 * @param scope         "Team London", "Business unit" or "Default rule"
 * @param approver      who must decide the current level, in words
 * @param canAct        whether the signed-in user may decide it now
 * @param waitingReason why not, when they may not
 * @param outcome       the settled decision, once there is one
 */
public record ApprovalStateView(Long requestId, boolean pending, int level, int totalLevels,
                                String matrixName, String scope, String approver,
                                boolean canAct, String waitingReason, ApprovalDecision outcome) {

    public static final ApprovalStateView NONE =
        new ApprovalStateView(null, false, 0, 0, null, null, null, false, null, null);

    public static ApprovalStateView settled(Long requestId, ApprovalDecision outcome, int totalLevels,
                                            String matrixName, String scope) {
        return new ApprovalStateView(requestId, false, totalLevels, totalLevels, matrixName, scope, null, false, null, outcome);
    }
}

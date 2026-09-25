package com.asg.fabricerp.marketing;

import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Team-wise approval - the legacy system routed approval by marketing group, and asfl-erp's V27
 * restored that with a per-team approval matrix.
 *
 * <p>A document stamped with a team that has approvers may be approved or rejected only by one of
 * them. A team with no approvers - and a document with no team - falls back to the business-wide
 * rule, which {@code ApprovalService} applies either way: the screen's {@code APPROVE} verb, sight
 * of the document, and never the creator approving their own. This rule only ever narrows who
 * may decide; it never lets in someone that rule keeps out.
 *
 * <p>Evaluated at decision time against the team the document was stamped with, not the
 * approver's own team: a director covering for another team decides under that team's list.
 */
@Component
public class TeamApprovalRule {

    private final MarketingTeamApproverRepository approvers;
    private final OrgContext context;

    public TeamApprovalRule(MarketingTeamApproverRepository approvers, OrgContext context) {
        this.approvers = approvers;
        this.context = context;
    }

    /** The usernames that decide this document, or empty when the business-wide rule applies. */
    public List<String> approversOf(BusinessDocument doc) {
        MarketingTeam team = doc.getMarketingTeam();
        return team == null ? List.of() : approvers.activeApproverUsernames(team.getId());
    }

    public void assertMayDecide(BusinessDocument doc) {
        List<String> allowed = approversOf(doc);
        if (allowed.isEmpty()) {
            return;
        }
        String current = context.username();
        if (current == null || allowed.stream().noneMatch(current::equalsIgnoreCase)) {
            throw new AccessDeniedException(
                "%s belongs to the %s team and is approved by that team's approvers: %s"
                    .formatted(doc.getDocumentNo(), doc.getMarketingTeam().getName(), String.join(", ", allowed)));
        }
    }
}

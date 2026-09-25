package com.asg.fabricerp.marketing;

import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.BusinessDocument;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Team-wise approval: a team with approvers is decided by them alone, a team without falls back
 * to the business-wide rule, and an unteamed document is never narrowed.
 */
class TeamApprovalRuleTest {

    private final MarketingTeamApproverRepository approvers = mock(MarketingTeamApproverRepository.class);

    private TeamApprovalRule ruleFor(String username) {
        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return 1L; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return null; }
            @Override public String username()         { return username; }
            @Override public RowScope rowScope()       { return RowScope.unrestrictedScope(); }
        };
        return new TeamApprovalRule(approvers, context);
    }

    private static BusinessDocument bookingOf(Long teamId) {
        BusinessDocument doc = new BusinessDocument();
        doc.setDocumentNo("BKAF000031");
        if (teamId != null) {
            MarketingTeam team = new MarketingTeam("3", "London");
            team.setId(teamId);
            doc.stampMarketingTeam(team);
        }
        return doc;
    }

    @Test
    void aTeamsOwnApproverMayDecide() {
        when(approvers.activeApproverUsernames(3L)).thenReturn(List.of("rahim", "karim"));

        assertThatCode(() -> ruleFor("karim").assertMayDecide(bookingOf(3L))).doesNotThrowAnyException();
    }

    @Test
    void usernamesMatchWhateverTheirCase() {
        when(approvers.activeApproverUsernames(3L)).thenReturn(List.of("Rahim"));

        assertThatCode(() -> ruleFor("rahim").assertMayDecide(bookingOf(3L))).doesNotThrowAnyException();
    }

    @Test
    void anyoneElseIsRefusedAndToldWhoDecides() {
        when(approvers.activeApproverUsernames(3L)).thenReturn(List.of("rahim", "karim"));

        assertThatThrownBy(() -> ruleFor("director").assertMayDecide(bookingOf(3L)))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("London")
            .hasMessageContaining("rahim, karim");
    }

    @Test
    void aTeamWithNoApproversFallsBackToTheBusinessWideRule() {
        when(approvers.activeApproverUsernames(3L)).thenReturn(List.of());

        assertThatCode(() -> ruleFor("director").assertMayDecide(bookingOf(3L))).doesNotThrowAnyException();
    }

    @Test
    void aDocumentWithNoTeamIsNeverNarrowed() {
        assertThatCode(() -> ruleFor("director").assertMayDecide(bookingOf(null))).doesNotThrowAnyException();
        verifyNoInteractions(approvers);
    }
}

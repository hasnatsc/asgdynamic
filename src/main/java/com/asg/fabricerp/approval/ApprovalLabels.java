package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.RoleRepository;
import com.asg.fabricerp.security.Screen;
import org.springframework.stereotype.Component;

/**
 * Approvals in words: "role Merchandising Manager", "Rahim Uddin", "anyone with Approve on
 * Booking"; "Team London" or "Business unit". Kept apart from the engine so the engine's decisions
 * never depend on how something is labelled.
 */
@Component
public class ApprovalLabels {

    private final RoleRepository roles;
    private final FabricUserRepository users;
    private final MarketingTeamRepository teams;
    private final ApprovalMatrixRepository matrices;
    private final OrgContext context;

    public ApprovalLabels(RoleRepository roles, FabricUserRepository users, MarketingTeamRepository teams,
                          ApprovalMatrixRepository matrices, OrgContext context) {
        this.roles = roles;
        this.users = users;
        this.teams = teams;
        this.matrices = matrices;
        this.context = context;
    }

    public String approver(Approver approver, DocumentType type) {
        if (approver == null) return null;
        return switch (approver.kind()) {
            case ROLE -> "role " + roles.findById(approver.roleId()).map(r -> r.getName()).orElse("#" + approver.roleId());
            case USER -> userName(approver.userId());
            case AUTHORITY -> "anyone with Approve on " + screenLabel(type);
        };
    }

    public String userName(Long userId) {
        return userId == null ? null : users.findScoped(userId, context.requireOrganizationId())
            .map(ApprovalLabels::display).orElse("user #" + userId);
    }

    public String matrixName(Long matrixId) {
        return matrixId == null ? null
            : matrices.findScoped(matrixId, context.requireOrganizationId()).map(ApprovalMatrix::getName).orElse(null);
    }

    /** Which matrix governed: the team's own, the unit-wide one, or no matrix at all. */
    public String scope(ApprovalRequest request) {
        if (request.getMatrixId() == null) return "Default rule";
        return matrices.findScoped(request.getMatrixId(), context.requireOrganizationId())
            .map(m -> m.isTeamWise() ? "Team " + teamName(m.getMarketingTeamId()) : "Business unit")
            .orElse("Business unit");
    }

    public String teamName(Long teamId) {
        return teamId == null ? null
            : teams.findScoped(teamId, context.requireOrganizationId()).map(t -> t.getName()).orElse("#" + teamId);
    }

    public ApprovalRowView row(ApprovalRequest request, BusinessDocument doc, Approver required) {
        return new Batch(this).row(request, doc, required);
    }

    /**
     * Labels for one page of rows, each name looked up once: a page of fifty requests from three
     * teams, two roles and a dozen makers asks for seventeen names, not two hundred.
     */
    public static final class Batch {
        private final ApprovalLabels labels;
        private final java.util.Map<Long, String> teams = new java.util.HashMap<>();
        private final java.util.Map<Long, String> users = new java.util.HashMap<>();
        private final java.util.Map<Long, String> scopes = new java.util.HashMap<>();
        private final java.util.Map<String, String> approvers = new java.util.HashMap<>();

        public Batch(ApprovalLabels labels) {
            this.labels = labels;
        }

        public ApprovalRowView row(ApprovalRequest request, BusinessDocument doc, Approver required) {
            DocumentType type = doc.getDocumentType();
            return new ApprovalRowView(request.getId(), doc.getId(), type.name(), type.label(),
                doc.getDocumentNo(), doc.getParty() == null ? null : doc.getParty().getName(),
                request.getOwningTeamId() == null ? null
                    : teams.computeIfAbsent(request.getOwningTeamId(), labels::teamName),
                request.getAmount(), doc.getCurrencyCode(),
                request.getCurrentLevel(), request.getTotalLevels(),
                required == null ? null
                    : approvers.computeIfAbsent(required + "|" + type, k -> labels.approver(required, type)),
                request.getMatrixId() == null ? "Default rule"
                    : scopes.computeIfAbsent(request.getMatrixId(), k -> labels.scope(request)),
                request.getRaisedByUserId() != null
                    ? users.computeIfAbsent(request.getRaisedByUserId(), labels::userName) : request.getRaisedBy(),
                request.getCreatedAt(), request.isPending(), request.getOutcome(), request.getSettledAt(),
                screenPath(type));
        }
    }

    /** The screen a type's documents live on; null for a type with no screen yet. */
    public static String screenPath(DocumentType type) {
        Screen screen = screenOf(type);
        return screen == null ? null : screen.path();
    }

    static String screenLabel(DocumentType type) {
        Screen screen = screenOf(type);
        return screen == null ? type.label() : screen.label();
    }

    private static Screen screenOf(DocumentType type) {
        try {
            return Screen.valueOf(type.roleRoot());
        } catch (RuntimeException noScreen) {
            return null;
        }
    }

    private static String display(FabricUser user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName();
    }
}

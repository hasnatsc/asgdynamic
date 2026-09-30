package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The Approval matrices screen - asfl-erp's {@code ApprovalMatrixService}.
 *
 * <p>A matrix's document type and team are fixed once it exists: requests already decided under it
 * name it, and a matrix that could turn into another type's or another team's would rewrite what
 * those requests were approved against. Its name, levels and active flag may change; a request
 * in flight keeps the levels count it was submitted with.
 */
@Service
public class ApprovalMatrixService {

    private final ApprovalMatrixRepository matrices;
    private final ApprovalRequestRepository requests;
    private final RoleRepository roles;
    private final FabricUserRepository users;
    private final MarketingTeamRepository teams;
    private final OrgContext context;

    public ApprovalMatrixService(ApprovalMatrixRepository matrices, ApprovalRequestRepository requests,
                                 RoleRepository roles, FabricUserRepository users,
                                 MarketingTeamRepository teams, OrgContext context) {
        this.matrices = matrices;
        this.requests = requests;
        this.roles = roles;
        this.users = users;
        this.teams = teams;
        this.context = context;
    }

    /** What the editor submits. Levels are in signing order; their sequence is their position. */
    public record MatrixRequest(Long id, DocumentType documentType, Long marketingTeamId, String name,
                                Boolean active, List<LevelRequest> levels) { }

    /**
     * One level. {@code timeLimitMinutes} null is no limit; {@code timeoutAction} null is
     * {@link TimeoutAction#REMIND}; the escalation role or user is read only when escalating.
     */
    public record LevelRequest(Long roleId, Long userId, BigDecimal minAmount, BigDecimal maxAmount,
                               Integer timeLimitMinutes, TimeoutAction timeoutAction,
                               Long escalateRoleId, Long escalateUserId) {

        public LevelRequest(Long roleId, Long userId, BigDecimal minAmount, BigDecimal maxAmount) {
            this(roleId, userId, minAmount, maxAmount, null, null, null, null);
        }
    }

    /** A time limit is at most a year: anything longer is no limit, and should say so. */
    static final int MAX_LIMIT_MINUTES = 366 * 24 * 60;

    /** The document types a matrix can govern: those with a screen to submit them from. */
    public static List<DocumentType> approvableTypes() {
        return Arrays.stream(DocumentType.values())
            .filter(t -> ApprovalLabels.screenPath(t) != null)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<ApprovalMatrix> search(DocumentType type, String q, Pageable pageable) {
        return matrices.search(context.requireOrganizationId(), context.requireBusinessUnitId(), type, q, pageable);
    }

    @Transactional(readOnly = true)
    public ApprovalMatrix get(Long id) {
        return matrices.findScoped(id, context.requireOrganizationId())
            .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
            .orElseThrow(() -> new IllegalArgumentException("Approval matrix not found: " + id));
    }

    @Transactional
    public ApprovalMatrix save(MatrixRequest submitted) {
        Long orgId = context.requireOrganizationId();
        String name = submitted.name() == null ? null : submitted.name().trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Give the matrix a name");
        }
        List<LevelRequest> levels = submitted.levels() == null ? List.of() : submitted.levels();
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("A matrix needs at least one level - a matrix nobody signs approves nothing");
        }

        ApprovalMatrix target;
        if (submitted.id() == null) {
            DocumentType type = submitted.documentType();
            if (type == null || !approvableTypes().contains(type)) {
                throw new IllegalArgumentException("Choose a document type that is approved on a screen");
            }
            Long teamId = submitted.marketingTeamId();
            if (teamId != null && teams.lookup(orgId).stream().noneMatch(t -> t.getId().equals(teamId))) {
                throw new IllegalArgumentException("No active marketing team with id " + teamId);
            }
            Long unitId = context.requireBusinessUnitId();
            Optional<ApprovalMatrix> clash = teamId == null
                ? matrices.findUnitWide(orgId, unitId, type)
                : matrices.findTeamWise(orgId, unitId, type, teamId);
            if (clash.isPresent()) {
                throw new IllegalStateException(("“%s” already governs %s%s in this business unit - edit it rather than "
                    + "adding a second.").formatted(clash.get().getName(), type.label(), teamId == null ? "" : " for this team"));
            }
            target = new ApprovalMatrix(orgId, unitId, teamId, type, name);
        } else {
            target = get(submitted.id());
            target.setName(name);
        }
        target.setActive(submitted.active() == null || submitted.active());
        List<ApprovalLevel> built = new java.util.ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            built.add(level(orgId, i + 1, levels.get(i)));
        }
        target.replaceLevels(built);
        return matrices.save(target);
    }

    /** Deleted only if nothing was ever submitted under it; otherwise it is deactivated, and its history stands. */
    @Transactional
    public void delete(Long id) {
        ApprovalMatrix matrix = get(id);
        if (requests.existsByMatrixId(id)) {
            throw new IllegalStateException(("Documents were submitted under “%s”, so it cannot be deleted - its "
                + "approvals still refer to it. Deactivate it instead.").formatted(matrix.getName()));
        }
        matrix.markDeleted();
        matrices.save(matrix);
    }

    private ApprovalLevel level(Long orgId, int sequence, LevelRequest l) {
        requireRole(l.roleId());
        requireUser(orgId, l.userId());
        if (l.minAmount() != null && l.minAmount().signum() < 0 || l.maxAmount() != null && l.maxAmount().signum() < 0) {
            throw new IllegalArgumentException("Amount bands cannot be negative");
        }
        Integer limit = l.timeLimitMinutes();
        if (limit != null && (limit < 1 || limit > MAX_LIMIT_MINUTES)) {
            throw new IllegalArgumentException("Level %d: a time limit is between one minute and a year".formatted(sequence));
        }
        TimeoutAction action = l.timeoutAction() == null ? TimeoutAction.REMIND : l.timeoutAction();
        if (limit == null && action != TimeoutAction.REMIND) {
            throw new IllegalArgumentException(("Level %d: “%s” needs a time limit - without one the level "
                + "never runs out of time").formatted(sequence, action.label()));
        }
        Long escalateRole = null;
        Long escalateUser = null;
        if (action == TimeoutAction.ESCALATE) {
            escalateRole = l.escalateRoleId();
            escalateUser = l.escalateRoleId() == null ? l.escalateUserId() : null;
            if (escalateRole == null && escalateUser == null) {
                throw new IllegalArgumentException("Level %d: choose the role or person it escalates to".formatted(sequence));
            }
            requireRole(escalateRole);
            requireUser(orgId, escalateUser);
            boolean same = escalateRole != null ? escalateRole.equals(l.roleId()) : escalateUser.equals(l.userId());
            if (same) {
                throw new IllegalArgumentException(("Level %d escalates to the approver it already waits for - "
                    + "choose someone else, or remind instead").formatted(sequence));
            }
        }
        return new ApprovalLevel(l.roleId(), l.userId(), l.minAmount(), l.maxAmount(),
            limit, action, escalateRole, escalateUser);
    }

    private void requireRole(Long roleId) {
        if (roleId != null && roles.findById(roleId).filter(r -> !Boolean.FALSE.equals(r.getActive())).isEmpty()) {
            throw new IllegalArgumentException("No active role with id " + roleId);
        }
    }

    private void requireUser(Long orgId, Long userId) {
        if (userId != null && users.findScoped(userId, orgId).isEmpty()) {
            throw new IllegalArgumentException("No user with id " + userId);
        }
    }
}

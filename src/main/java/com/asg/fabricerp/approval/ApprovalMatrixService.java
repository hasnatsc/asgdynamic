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

    public record LevelRequest(Long roleId, Long userId, BigDecimal minAmount, BigDecimal maxAmount) { }

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
        target.replaceLevels(levels.stream().map(l -> level(orgId, l)).toList());
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

    private ApprovalLevel level(Long orgId, LevelRequest l) {
        if (l.roleId() != null && roles.findById(l.roleId()).filter(r -> !Boolean.FALSE.equals(r.getActive())).isEmpty()) {
            throw new IllegalArgumentException("No active role with id " + l.roleId());
        }
        if (l.userId() != null && users.findScoped(l.userId(), orgId).isEmpty()) {
            throw new IllegalArgumentException("No user with id " + l.userId());
        }
        if (l.minAmount() != null && l.minAmount().signum() < 0 || l.maxAmount() != null && l.maxAmount().signum() < 0) {
            throw new IllegalArgumentException("Amount bands cannot be negative");
        }
        return new ApprovalLevel(l.roleId(), l.userId(), l.minAmount(), l.maxAmount());
    }
}

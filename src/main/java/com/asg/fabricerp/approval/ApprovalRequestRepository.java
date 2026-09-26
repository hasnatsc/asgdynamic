package com.asg.fabricerp.approval;

import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByDocumentIdAndPendingTrue(Long documentId);

    /** The live requests of one page of documents - a list shows progress without a query per row. */
    List<ApprovalRequest> findByDocumentIdInAndPendingTrue(Collection<Long> documentIds);

    Optional<ApprovalRequest> findFirstByDocumentIdOrderByIdDesc(Long documentId);

    boolean existsByMatrixId(Long matrixId);

    /**
     * The centralised inbox: every pending request in the unit whose CURRENT level waits for this
     * person, oldest first, one page at a time - one indexed query however many are pending.
     *
     * <ul>
     *   <li>a level naming this user - theirs wherever the document's team;</li>
     *   <li>a level naming a role they hold, or the default rule (no matrix) for a type whose
     *       Approve verb they hold - only for the teams their row scope lets them see
     *       ({@code allTeams}, {@code teamIds}), so one team's approvers never see another's
     *       documents;</li>
     *   <li>never one they raised (four-eyes).</li>
     * </ul>
     *
     * The service re-checks everything when a decision is posted; this only decides what is listed.
     * {@code roleIds} and {@code defaultTypes} are never empty: the service passes a placeholder
     * with the matching flag off, since an empty IN list is not portable JPQL.
     */
    @Query("""
           select r from ApprovalRequest r
           where r.organizationId = :orgId and r.businessUnitId = :unitId and r.pending = true
             and (r.raisedByUserId is null or r.raisedByUserId <> :userId)
             and (r.raisedBy is null or lower(r.raisedBy) <> lower(cast(:username as string)))
             and (:type is null or r.documentType = :type)
             and (r.currentUserId = :userId
                  or ((:allTeams = true or r.owningTeamId in :teamIds)
                      and ((:hasRoles = true and r.currentRoleId in :roleIds)
                           or (:hasDefault = true and r.matrixId is null and r.documentType in :defaultTypes))))
             and (:q is null or exists (
                  select d.id from BusinessDocument d left join d.party p
                  where d.id = r.documentId
                    and (lower(d.documentNo) like lower(concat('%', cast(:q as string), '%'))
                         or lower(coalesce(d.referenceNo, '')) like lower(concat('%', cast(:q as string), '%'))
                         or lower(coalesce(p.name, '')) like lower(concat('%', cast(:q as string), '%')))))
           """)
    Page<ApprovalRequest> inbox(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                @Param("userId") Long userId, @Param("username") String username,
                                @Param("type") DocumentType type, @Param("q") String q,
                                @Param("allTeams") boolean allTeams, @Param("teamIds") List<Long> teamIds,
                                @Param("hasRoles") boolean hasRoles, @Param("roleIds") Collection<Long> roleIds,
                                @Param("hasDefault") boolean hasDefault,
                                @Param("defaultTypes") Collection<DocumentType> defaultTypes,
                                Pageable pageable);

    /**
     * The requests report: pending, settled or both, newest first - narrowed in the query to the
     * teams the caller may see, so a page is a full page and the total is the real total.
     */
    @Query("""
           select r from ApprovalRequest r
           where r.organizationId = :orgId and r.businessUnitId = :unitId
             and (:pending is null or r.pending = :pending)
             and (:type is null or r.documentType = :type)
             and (:allTeams = true or r.owningTeamId in :teamIds)
             and (:q is null or exists (
                  select d.id from BusinessDocument d left join d.party p
                  where d.id = r.documentId
                    and (lower(d.documentNo) like lower(concat('%', cast(:q as string), '%'))
                         or lower(coalesce(d.referenceNo, '')) like lower(concat('%', cast(:q as string), '%'))
                         or lower(coalesce(p.name, '')) like lower(concat('%', cast(:q as string), '%')))))
           """)
    Page<ApprovalRequest> report(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                 @Param("pending") Boolean pending, @Param("type") DocumentType type,
                                 @Param("q") String q,
                                 @Param("allTeams") boolean allTeams, @Param("teamIds") List<Long> teamIds,
                                 Pageable pageable);
}

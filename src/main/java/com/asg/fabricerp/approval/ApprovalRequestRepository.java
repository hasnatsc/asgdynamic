package com.asg.fabricerp.approval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByDocumentIdAndPendingTrue(Long documentId);

    /** The live requests of one page of documents - a list shows progress without a query per row. */
    List<ApprovalRequest> findByDocumentIdInAndPendingTrue(java.util.Collection<Long> documentIds);

    Optional<ApprovalRequest> findFirstByDocumentIdOrderByIdDesc(Long documentId);

    boolean existsByMatrixId(Long matrixId);

    /** Everything awaiting a decision in one unit; the service narrows it to what the caller may act on. */
    @Query("""
           select r from ApprovalRequest r
           where r.organizationId = :orgId and r.businessUnitId = :unitId and r.pending = true
           order by r.createdAt
           """)
    List<ApprovalRequest> findPending(@Param("orgId") Long orgId, @Param("unitId") Long unitId);

    /** The requests report: pending, settled or both, newest first. */
    @Query("""
           select r from ApprovalRequest r
           where r.organizationId = :orgId and r.businessUnitId = :unitId
             and (:pending is null or r.pending = :pending)
           """)
    Page<ApprovalRequest> report(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                 @Param("pending") Boolean pending, Pageable pageable);
}

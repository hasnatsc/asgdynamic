package com.asg.fabricerp.approval;

import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalMatrixRepository extends JpaRepository<ApprovalMatrix, Long> {

    /** A team's own matrix for a type, active or not - the caller decides whether inactive counts. */
    @EntityGraph(attributePaths = "levels")
    @Query("""
           select m from ApprovalMatrix m
           where m.organizationId = :orgId and m.businessUnitId = :unitId and m.documentType = :type
             and m.marketingTeamId = :teamId and m.deleted = false
           """)
    Optional<ApprovalMatrix> findTeamWise(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                         @Param("type") DocumentType type, @Param("teamId") Long teamId);

    /** The unit-wide matrix for a type. */
    @EntityGraph(attributePaths = "levels")
    @Query("""
           select m from ApprovalMatrix m
           where m.organizationId = :orgId and m.businessUnitId = :unitId and m.documentType = :type
             and m.marketingTeamId is null and m.deleted = false
           """)
    Optional<ApprovalMatrix> findUnitWide(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                          @Param("type") DocumentType type);

    /** One matrix with its levels - including a deactivated one still governing a request in flight. */
    @EntityGraph(attributePaths = "levels")
    @Query("select m from ApprovalMatrix m where m.id = :id and m.organizationId = :orgId")
    Optional<ApprovalMatrix> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** The Approval matrices grid. */
    @Query("""
           select m from ApprovalMatrix m
           where m.organizationId = :orgId and m.businessUnitId = :unitId and m.deleted = false
             and (:type is null or m.documentType = :type)
             and (:q is null or lower(m.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<ApprovalMatrix> search(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                @Param("type") DocumentType type, @Param("q") String q, Pageable pageable);

    /** Every active matrix for a type in a unit - team-wise and unit-wide - with its levels (analytics scope). */
    @EntityGraph(attributePaths = "levels")
    @Query("""
           select distinct m from ApprovalMatrix m
           where m.organizationId = :orgId and m.businessUnitId = :unitId and m.documentType = :type
             and m.deleted = false and m.active = true
           """)
    List<ApprovalMatrix> findActiveForType(@Param("orgId") Long orgId, @Param("unitId") Long unitId,
                                           @Param("type") DocumentType type);

    /** The team-wise matrices a marketing team has, for the team screen. */
    @Query("""
           select count(m) from ApprovalMatrix m
           where m.marketingTeamId = :teamId and m.deleted = false and m.active = true
           """)
    long countActiveForTeam(@Param("teamId") Long teamId);
}

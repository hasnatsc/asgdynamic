package com.asg.fabricerp.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketingTeamRepository extends JpaRepository<MarketingTeam, Long> {

    /** Dropdown feed: the User admin form's scope grants and the Booking team picker. */
    @Query("""
           select t from MarketingTeam t
           where t.organizationId = :orgId and t.active = true and t.deleted = false
           order by t.name
           """)
    List<MarketingTeam> lookup(@Param("orgId") Long orgId);

    /** One team of this organization, active or not - a retired team still resolves on old documents. */
    @Query("""
           select t from MarketingTeam t
           where t.id = :id and t.organizationId = :orgId and t.deleted = false
           """)
    Optional<MarketingTeam> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** The Marketing teams grid: every team not deleted, active or retired. */
    @Query("""
           select t from MarketingTeam t
           where t.organizationId = :orgId and t.deleted = false
             and (:active is null or t.active = :active)
             and (:q is null
                  or lower(t.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(t.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<MarketingTeam> search(@Param("orgId") Long orgId, @Param("q") String q,
                               @Param("active") Boolean active, Pageable pageable);

    @Query("""
           select count(t) > 0 from MarketingTeam t
           where t.organizationId = :orgId and t.deleted = false
             and lower(t.code) = lower(:code) and (:exceptId is null or t.id <> :exceptId)
           """)
    boolean codeTaken(@Param("orgId") Long orgId, @Param("code") String code, @Param("exceptId") Long exceptId);

    /** Documents stamped with the team - a team that owns any is retired, never deleted (ADM-7). */
    @Query("""
           select count(d) from com.asg.fabricerp.global.documents.BusinessDocument d
           where d.marketingTeam.id = :teamId and d.deleted = false
           """)
    long countDocuments(@Param("teamId") Long teamId);
}

package com.asg.fabricerp.global.terms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TermsConditionRepository extends JpaRepository<TermsCondition, Long> {

    /** What a new document starts with: active default clauses of one type, in order. */
    @Query("""
           select t from TermsCondition t
           where t.organizationId = :orgId
             and t.conditionType = :type
             and t.isDefault = true
             and t.active = true
             and t.deleted = false
           order by t.sortOrder asc, t.id asc
           """)
    List<TermsCondition> defaults(@Param("orgId") Long orgId, @Param("type") ConditionType type);

    /** Every active clause of one type - the library a user picks extra clauses from. */
    @Query("""
           select t from TermsCondition t
           where t.organizationId = :orgId
             and t.conditionType = :type
             and t.active = true
             and t.deleted = false
           order by t.isDefault desc, t.sortOrder asc, t.id asc
           """)
    List<TermsCondition> library(@Param("orgId") Long orgId, @Param("type") ConditionType type);

    /** Grid feed: inactive rows included so they can be switched back on. */
    @Query("""
           select t from TermsCondition t
           where t.organizationId = :orgId
             and t.deleted = false
             and (:type is null or t.conditionType = :type)
             and (:q is null
                  or lower(t.caption)  like lower(concat('%', cast(:q as string), '%'))
                  or lower(t.bodyText) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<TermsCondition> search(@Param("orgId") Long orgId, @Param("type") ConditionType type,
                                @Param("q") String q, Pageable pageable);

    @Query("""
           select t from TermsCondition t
           where t.id = :id and t.organizationId = :orgId and t.deleted = false
           """)
    Optional<TermsCondition> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}

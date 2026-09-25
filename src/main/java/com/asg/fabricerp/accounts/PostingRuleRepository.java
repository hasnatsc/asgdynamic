package com.asg.fabricerp.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostingRuleRepository extends JpaRepository<PostingRule, Long> {

    @Query("""
           select distinct r from PostingRule r left join fetch r.lines
           where r.organizationId = :orgId and r.eventType = :event and r.active = true and r.deleted = false
           """)
    List<PostingRule> forEvent(@Param("orgId") Long orgId, @Param("event") String eventType);

    @Query("""
           select distinct r from PostingRule r left join fetch r.lines
           where r.organizationId = :orgId and r.deleted = false
           order by r.eventType, r.effectiveFrom desc
           """)
    List<PostingRule> all(@Param("orgId") Long orgId);

    @Query("select r from PostingRule r where r.organizationId = :orgId and r.code = :code and r.deleted = false")
    Optional<PostingRule> findByCode(@Param("orgId") Long orgId, @Param("code") String code);
}

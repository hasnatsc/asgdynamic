package com.asg.fabricerp.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MarketingTeamRepository extends JpaRepository<MarketingTeam, Long> {

    /** Dropdown feed for the User admin form's scope grants. */
    @Query("""
           select t from MarketingTeam t
           where t.organizationId = :orgId and t.active = true and t.deleted = false
           order by t.name
           """)
    List<MarketingTeam> lookup(@Param("orgId") Long orgId);
}

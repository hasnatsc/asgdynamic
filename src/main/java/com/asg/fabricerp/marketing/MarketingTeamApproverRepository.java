package com.asg.fabricerp.marketing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketingTeamApproverRepository extends JpaRepository<MarketingTeamApprover, Long> {

    @Query("""
           select a from MarketingTeamApprover a
           where a.teamId = :teamId and a.deleted = false
           order by a.id
           """)
    List<MarketingTeamApprover> findByTeam(@Param("teamId") Long teamId);

    Optional<MarketingTeamApprover> findByTeamIdAndUserId(Long teamId, Long userId);

    /**
     * The usernames that decide a team's documents - active, unlocked accounts only. A locked or
     * deleted approver cannot sign in to approve, so counting them would leave a team with an
     * approver list nobody can act on instead of falling back.
     */
    @Query("""
           select u.username from MarketingTeamApprover a, com.asg.fabricerp.security.FabricUser u
           where a.teamId = :teamId and a.deleted = false and a.active = true
             and u.id = a.userId and u.deleted = false and u.active = true and u.accountLocked = false
           order by u.username
           """)
    List<String> activeApproverUsernames(@Param("teamId") Long teamId);
}

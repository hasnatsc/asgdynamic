package com.asg.fabricerp.security;

import com.asg.fabricerp.common.ScopeDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface DataScopeRepository extends JpaRepository<DataScope, Long> {

    /** Every grant, open or closed — the principal filters to those held today. */
    List<DataScope> findByUserIdOrderByGrantedFromDesc(Long userId);

    /**
     * Which of these users hold at least one grant on {@code on} — the user grid marks the rest
     * of the restricted ones as unable to sign in. One query per page of users, not one per row.
     */
    @Query("""
           select distinct s.userId from DataScope s
           where s.userId in :userIds and s.grantedFrom <= :on
             and (s.revokedFrom is null or s.revokedFrom > :on)
           """)
    Set<Long> findUserIdsHoldingScopeOn(@Param("userIds") Collection<Long> userIds, @Param("on") LocalDate on);

    /**
     * The grants that put users on one value of a dimension on a date - for a marketing team,
     * its members (ADM-4). Open-ended and future-dated grants are both "held" only once they
     * have started, so a grant from next month is not yet a member.
     */
    @Query("""
           select s from DataScope s
           where s.dimension = :dimension and s.scopeValueId = :valueId
             and s.grantedFrom <= :on and (s.revokedFrom is null or s.revokedFrom > :on)
           order by s.grantedFrom
           """)
    List<DataScope> findHoldersOn(@Param("dimension") ScopeDimension dimension,
                                  @Param("valueId") Long valueId, @Param("on") LocalDate on);
}

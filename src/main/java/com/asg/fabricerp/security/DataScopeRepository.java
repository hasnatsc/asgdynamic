package com.asg.fabricerp.security;

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
}

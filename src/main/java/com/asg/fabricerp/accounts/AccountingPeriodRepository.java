package com.asg.fabricerp.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    /** Periods are generated one per fiscal month, so at most one covers a date. */
    @Query("""
           select p from AccountingPeriod p
           where p.organizationId = :orgId and p.deleted = false
             and p.startsOn <= :on and p.endsOn >= :on
           """)
    Optional<AccountingPeriod> findCovering(@Param("orgId") Long orgId, @Param("on") LocalDate on);

    @Query("""
           select p from AccountingPeriod p
           where p.organizationId = :orgId and p.fiscalYear = :year and p.deleted = false
           order by p.periodNo
           """)
    List<AccountingPeriod> ofYear(@Param("orgId") Long orgId, @Param("year") int year);

    @Query("select distinct p.fiscalYear from AccountingPeriod p where p.organizationId = :orgId and p.deleted = false order by p.fiscalYear desc")
    List<Integer> years(@Param("orgId") Long orgId);

    @Query("select p from AccountingPeriod p where p.id = :id and p.organizationId = :orgId and p.deleted = false")
    Optional<AccountingPeriod> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}

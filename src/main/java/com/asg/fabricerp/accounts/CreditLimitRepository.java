package com.asg.fabricerp.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    @Query("""
           select c from CreditLimit c
           where c.organizationId = :orgId and c.partyId = :partyId and c.active = true and c.deleted = false
           order by c.effectiveFrom desc
           """)
    List<CreditLimit> forParty(@Param("orgId") Long orgId, @Param("partyId") Long partyId);

    /** The current limit of every customer that has one. */
    @Query("""
           select c from CreditLimit c
           where c.organizationId = :orgId and c.effectiveTo is null and c.active = true and c.deleted = false
           """)
    List<CreditLimit> current(@Param("orgId") Long orgId);

    @Query("select c from CreditLimit c where c.id = :id and c.organizationId = :orgId and c.deleted = false")
    Optional<CreditLimit> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}

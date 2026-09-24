package com.asg.fabricerp.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, Long> {

    /** Dropdown feed for the User admin form. */
    @Query("""
           select b from BusinessUnit b
           where b.organizationId = :orgId and b.active = true and b.deleted = false
           order by b.name
           """)
    List<BusinessUnit> lookup(@Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);
}

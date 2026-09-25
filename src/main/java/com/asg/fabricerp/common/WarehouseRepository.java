package com.asg.fabricerp.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    /** Dropdown feed for the User admin form. */
    @Query("""
           select w from Warehouse w
           where w.organizationId = :orgId and w.active = true and w.deleted = false
           order by w.name
           """)
    List<Warehouse> lookup(@Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);

    @Query("""
           select w from Warehouse w
           where w.id = :id and w.organizationId = :orgId and w.deleted = false
           """)
    java.util.Optional<Warehouse> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}

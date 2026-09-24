package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    @Query("""
           select e from UnitOfMeasure e
           where e.id = :id and e.organizationId = :orgId and e.deleted = false
           """)
    Optional<UnitOfMeasure> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** Grid feed: includes inactive rows so they can be re-enabled. */
    @Query("""
           select e from UnitOfMeasure e
           where e.organizationId = :orgId
             and e.deleted = false
             and (:q is null
                  or lower(e.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<UnitOfMeasure> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);

    /** Dropdown feed: active rows only. */
    @Query("""
           select e from UnitOfMeasure e
           where e.organizationId = :orgId and e.active = true and e.deleted = false
           order by e.category asc, e.name asc
           """)
    List<UnitOfMeasure> lookup(@Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);

    /** Another live base unit in the same category, other than {@code excludeId}. */
    @Query("""
           select count(e) > 0 from UnitOfMeasure e
           where e.organizationId = :orgId and e.category = :category and e.baseUnit = true
             and e.deleted = false and (:excludeId is null or e.id <> :excludeId)
           """)
    boolean baseUnitExists(@Param("orgId") Long orgId, @Param("category") UomCategory category,
                           @Param("excludeId") Long excludeId);
}

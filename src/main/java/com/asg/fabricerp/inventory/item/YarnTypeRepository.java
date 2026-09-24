package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YarnTypeRepository extends JpaRepository<YarnType, Long> {

    @Query("""
           select e from YarnType e
           where e.id = :id and e.organizationId = :orgId and e.deleted = false
           """)
    Optional<YarnType> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** Grid feed: includes inactive rows so they can be re-enabled. */
    @Query("""
           select e from YarnType e
           where e.organizationId = :orgId
             and e.deleted = false
             and (:q is null
                  or lower(e.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<YarnType> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);

    /** Dropdown feed: active rows only. */
    @Query("""
           select e from YarnType e
           where e.organizationId = :orgId and e.active = true and e.deleted = false
           order by e.name asc
           """)
    List<YarnType> lookup(@Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);
}

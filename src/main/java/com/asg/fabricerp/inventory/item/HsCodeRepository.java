package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HsCodeRepository extends JpaRepository<HsCode, Long> {

    @Query("""
           select e from HsCode e
           where e.id = :id and e.organizationId = :orgId and e.deleted = false
           """)
    Optional<HsCode> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query("""
           select e from HsCode e
           where e.organizationId = :orgId
             and e.deleted = false
             and (:q is null
                  or lower(e.hsCode) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.description) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.shortDescription) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<HsCode> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);

    @Query("""
           select e from HsCode e
           where e.organizationId = :orgId and e.active = true and e.deleted = false
           order by e.hsCode asc
           """)
    List<HsCode> lookup(@Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndHsCodeIgnoreCaseAndDeletedFalse(Long organizationId, String hsCode);
}

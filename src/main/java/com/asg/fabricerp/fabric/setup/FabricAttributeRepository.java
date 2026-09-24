package com.asg.fabricerp.fabric.setup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FabricAttributeRepository extends JpaRepository<FabricAttribute, Long> {

    /** Dropdown feed: active rows only, in display order. */
    @Query("""
           select a from FabricAttribute a
           where a.organizationId = :orgId
             and a.attributeType = :type
             and a.active = true
             and a.deleted = false
           order by a.displayOrder asc, a.name asc
           """)
    List<FabricAttribute> lookup(@Param("orgId") Long orgId,
                                 @Param("type") AttributeType type);

    /** Grid feed: includes inactive rows so they can be re-enabled. */
    @Query("""
           select a from FabricAttribute a
           where a.organizationId = :orgId
             and a.attributeType = :type
             and a.deleted = false
             and (:q is null
                  or lower(a.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(a.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<FabricAttribute> search(@Param("orgId") Long orgId,
                                 @Param("type") AttributeType type,
                                 @Param("q") String q,
                                 Pageable pageable);

    @Query("""
           select a from FabricAttribute a
           where a.id = :id and a.organizationId = :orgId and a.deleted = false
           """)
    Optional<FabricAttribute> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndAttributeTypeAndCodeAndDeletedFalse(
            Long organizationId, AttributeType attributeType, String code);
}

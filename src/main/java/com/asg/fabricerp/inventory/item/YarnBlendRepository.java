package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YarnBlendRepository extends JpaRepository<YarnBlend, Long> {

    @Query("""
           select e from YarnBlend e
           where e.id = :id and e.organizationId = :orgId and e.deleted = false
           """)
    Optional<YarnBlend> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** Grid feed: includes inactive rows so they can be re-enabled. */
    @Query("""
           select e from YarnBlend e
           where e.organizationId = :orgId
             and e.deleted = false
             and (:q is null
                  or lower(e.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<YarnBlend> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);

    /** Dropdown feed: active rows only. */
    @Query("""
           select e from YarnBlend e
           where e.organizationId = :orgId and e.active = true and e.deleted = false
           order by e.name asc
           """)
    List<YarnBlend> lookup(@Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);

    /** Another live blend with this name, other than {@code excludeId}. */
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query("""
           select count(b) > 0 from YarnBlend b
           where b.organizationId = :orgId and lower(b.name) = lower(:name)
             and b.deleted = false and (:excludeId is null or b.id <> :excludeId)
           """)
    boolean nameTaken(@Param("orgId") Long orgId, @Param("name") String name, @Param("excludeId") Long excludeId);

    /** Blends that use {@code fiberItemId} as a component. */
    @Query("""
           select count(b) > 0 from YarnBlend b join b.components c
           where c.fiber.id = :fiberItemId and b.deleted = false
           """)
    boolean usesFiber(@Param("fiberItemId") Long fiberItemId);
}

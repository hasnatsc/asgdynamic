package com.asg.fabricerp.fabric.quality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConstructionRepository extends JpaRepository<Construction, Long> {

    @Query("""
           select c from Construction c
           where c.id = :id and c.organizationId = :orgId and c.deleted = false
           """)
    Optional<Construction> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** The register: code or name contains {@code q} (a LookupPage.like pattern). */
    @Query(value = """
           select c from Construction c
           where c.organizationId = :orgId and c.deleted = false
             and (:includeInactive = true or c.active = true)
             and (lower(c.code) like :q escape '\\' or lower(coalesce(c.name, '')) like :q escape '\\')
           """,
           countQuery = """
           select count(c) from Construction c
           where c.organizationId = :orgId and c.deleted = false
             and (:includeInactive = true or c.active = true)
             and (lower(c.code) like :q escape '\\' or lower(coalesce(c.name, '')) like :q escape '\\')
           """)
    Page<Construction> search(@Param("orgId") Long orgId, @Param("q") String q,
                              @Param("includeInactive") boolean includeInactive, Pageable pageable);

    /** The picker: active constructions only, newest code last. */
    @Query("""
           select c from Construction c
           where c.organizationId = :orgId and c.deleted = false and c.active = true
             and (lower(c.code) like :q escape '\\' or lower(coalesce(c.name, '')) like :q escape '\\')
           order by c.code asc
           """)
    Slice<Construction> lookup(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);
}

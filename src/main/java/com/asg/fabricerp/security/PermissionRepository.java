package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** Global — permissions aren't tenant-scoped, see {@link Permission}'s javadoc. */
    @Query("""
           select p from Permission p
           where :q is null
              or lower(p.name) like lower(concat('%', :q, '%'))
              or lower(p.module) like lower(concat('%', :q, '%'))
              or lower(p.description) like lower(concat('%', :q, '%'))
           """)
    Page<Permission> search(@Param("q") String q, Pageable pageable);
}

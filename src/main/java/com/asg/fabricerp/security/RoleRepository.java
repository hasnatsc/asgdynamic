package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "screenGrants")
    Optional<Role> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Global — roles aren't tenant-scoped, see {@link Role}'s javadoc. {@code hasGrants} separates
     * roles that grant something from V11's 71 empty shells, which otherwise bury them.
     */
    @Query("""
           select r from Role r
           where (:q is null
                  or lower(r.name) like lower(concat('%', :q, '%'))
                  or lower(r.description) like lower(concat('%', :q, '%')))
             and (:hasGrants is null
                  or (:hasGrants = true and exists (select g.id from RoleScreenGrant g where g.role = r))
                  or (:hasGrants = false and not exists (select g.id from RoleScreenGrant g where g.role = r)))
           """)
    Page<Role> search(@Param("q") String q, @Param("hasGrants") Boolean hasGrants, Pageable pageable);

    @Query("select count(r) from Role r where r.active = true")
    long countActive();

    @Query("select count(r) from Role r where not exists (select g.id from RoleScreenGrant g where g.role = r)")
    long countWithoutGrants();

    @EntityGraph(attributePaths = "screenGrants")
    Optional<Role> findWithGrantsById(Long id);
}

package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FabricUserRepository extends JpaRepository<FabricUser, Long> {

    /** Roles are @EAGER already, but the graph avoids a second round trip regardless. */
    @EntityGraph(attributePaths = "roles")
    Optional<FabricUser> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Used by {@code RoleService.delete} to block deleting a role still in use. */
    boolean existsByRolesId(Long roleId);

    /** Admin grid feed: includes locked/inactive rows so they can be managed. */
    @Query("""
           select u from FabricUser u
           where u.organizationId = :orgId
             and u.deleted = false
             and (:q is null
                  or lower(u.username) like lower(concat('%', :q, '%'))
                  or lower(u.fullName) like lower(concat('%', :q, '%')))
           """)
    Page<FabricUser> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable pageable);

    @Query("""
           select u from FabricUser u
           where u.id = :id and u.organizationId = :orgId and u.deleted = false
           """)
    Optional<FabricUser> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}

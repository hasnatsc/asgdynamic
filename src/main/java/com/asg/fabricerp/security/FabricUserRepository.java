package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // ---------------------------------------------------------------------------------------
    // Lockout — ADM-10 adjacent. Bulk updates, not entity edits: the counter has to be right
    // when two wrong passwords arrive together, and a read-modify-write on the entity counts
    // them as one. Each bumps the version so an administrator's concurrent save of the same
    // user fails loudly instead of silently unlocking an account the counter just locked.
    // ---------------------------------------------------------------------------------------

    @Modifying
    @Query("""
           update FabricUser u
              set u.failedLoginCount = u.failedLoginCount + 1,
                  u.lastFailedLoginAt = :at,
                  u.version = u.version + 1
            where lower(u.username) = lower(:username) and u.deleted = false
           """)
    int recordFailedLogin(@Param("username") String username, @Param("at") LocalDateTime at);

    @Modifying
    @Query("""
           update FabricUser u
              set u.accountLocked = true,
                  u.lockedAt = :at,
                  u.lockedReason = :reason,
                  u.version = u.version + 1
            where lower(u.username) = lower(:username) and u.deleted = false
              and u.accountLocked = false
              and u.failedLoginCount >= :threshold
           """)
    int lockIfOverThreshold(@Param("username") String username, @Param("threshold") int threshold,
                            @Param("reason") String reason, @Param("at") LocalDateTime at);

    @Modifying
    @Query("""
           update FabricUser u
              set u.failedLoginCount = 0,
                  u.lastLoginAt = :at,
                  u.version = u.version + 1
            where u.id = :id
           """)
    int recordSuccessfulLogin(@Param("id") Long id, @Param("at") LocalDateTime at);

    /** Used alongside an administrator's unlock, so the next typo does not relock at once. */
    @Modifying
    @Query("update FabricUser u set u.failedLoginCount = 0 where u.id = :id")
    int resetFailedLogins(@Param("id") Long id);
}

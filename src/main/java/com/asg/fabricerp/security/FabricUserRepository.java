package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FabricUserRepository extends JpaRepository<FabricUser, Long> {

    /** Roles are @EAGER already, but the graph avoids a second round trip regardless. */
    @EntityGraph(attributePaths = "roles")
    Optional<FabricUser> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Used by {@code RoleService.delete} to block deleting a role still in use. */
    boolean existsByRolesId(Long roleId);

    /**
     * Admin grid feed: includes locked/inactive rows so they can be managed. Each flag narrows
     * only when non-null, so the grid's status filter is one query rather than one per filter.
     */
    @Query("""
           select u from FabricUser u
           where u.organizationId = :orgId
             and u.deleted = false
             and (:q is null
                  or lower(u.username) like lower(concat('%', :q, '%'))
                  or lower(u.fullName) like lower(concat('%', :q, '%')))
             and (:locked is null or u.accountLocked = :locked)
             and (:mustChange is null or u.mustChangePassword = :mustChange)
             and (:unrestricted is null or u.unrestricted = :unrestricted)
           """)
    Page<FabricUser> search(@Param("orgId") Long orgId, @Param("q") String q,
                            @Param("locked") Boolean locked, @Param("mustChange") Boolean mustChange,
                            @Param("unrestricted") Boolean unrestricted, Pageable pageable);

    /** The overview's headline numbers in one pass over the table, not one query each. */
    @Query("""
           select new com.asg.fabricerp.security.UserCounts(
                  count(u),
                  coalesce(sum(case when u.accountLocked = true then 1L else 0L end), 0L),
                  coalesce(sum(case when u.mustChangePassword = true then 1L else 0L end), 0L),
                  coalesce(sum(case when u.unrestricted = true then 1L else 0L end), 0L))
           from FabricUser u
           where u.organizationId = :orgId and u.deleted = false
           """)
    UserCounts countSummary(@Param("orgId") Long orgId);

    /**
     * Restricted, unlocked accounts holding no scope today — ADM-3's "not configured": the login
     * refuses them, so they are exactly who an administrator needs to look at.
     */
    @Query("""
           select u from FabricUser u
           where u.organizationId = :orgId and u.deleted = false
             and u.unrestricted = false and u.accountLocked = false
             and not exists (select s.id from DataScope s
                             where s.userId = u.id and s.grantedFrom <= :today
                               and (s.revokedFrom is null or s.revokedFrom > :today))
           order by u.username
           """)
    List<FabricUser> findWithoutScope(@Param("orgId") Long orgId, @Param("today") LocalDate today, Pageable pageable);

    @Query("""
           select count(u) from FabricUser u
           where u.organizationId = :orgId and u.deleted = false
             and u.unrestricted = false and u.accountLocked = false
             and not exists (select s.id from DataScope s
                             where s.userId = u.id and s.grantedFrom <= :today
                               and (s.revokedFrom is null or s.revokedFrom > :today))
           """)
    long countWithoutScope(@Param("orgId") Long orgId, @Param("today") LocalDate today);

    @Query("""
           select u from FabricUser u
           where u.organizationId = :orgId and u.deleted = false and u.accountLocked = true
           order by u.lockedAt desc nulls last
           """)
    List<FabricUser> findLocked(@Param("orgId") Long orgId, Pageable pageable);

    /** {@code [roleId, userCount]} for the roles grid — one grouped query for the whole page. */
    @Query("""
           select r.id, count(u) from FabricUser u join u.roles r
           where r.id in :roleIds and u.organizationId = :orgId and u.deleted = false
           group by r.id
           """)
    List<Object[]> countUsersByRole(@Param("roleIds") Collection<Long> roleIds, @Param("orgId") Long orgId);

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

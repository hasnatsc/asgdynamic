package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The log has no organization column: a failed login for a username that does not exist
 * belongs to no tenant. Every read here is therefore narrowed through the user it names —
 * {@code userId in (this organization's users)} — which is also what keeps one tenant's
 * administrator from reading usernames somebody typed at another tenant's login.
 */
public interface AccessLogRepository extends JpaRepository<AccessLogEntry, Long>,
                                             JpaSpecificationExecutor<AccessLogEntry> {

    /** {@code [event, count]} since a moment, for the overview's last-24-hours tiles. */
    @Query("""
           select e.event, count(e) from AccessLogEntry e
           where e.occurredAt >= :since
             and e.userId in (select u.id from FabricUser u where u.organizationId = :orgId)
           group by e.event
           """)
    List<Object[]> countByEventSince(@Param("orgId") Long orgId, @Param("since") LocalDateTime since);

    @Query("""
           select e from AccessLogEntry e
           where e.event in :events
             and e.userId in (select u.id from FabricUser u where u.organizationId = :orgId)
           order by e.occurredAt desc
           """)
    List<AccessLogEntry> findRecent(@Param("orgId") Long orgId, @Param("events") List<Event> events,
                                    Pageable pageable);
}

package com.asg.fabricerp.security;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.security.AccessLogEntry.Event;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the security overview screen answers at a glance: who cannot work right now (locked, or
 * restricted with no scope), who is part-way through a reset, and what the last day's refusals
 * look like. Every number is organization-scoped; roles are global and counted as such.
 *
 * <p>Nine queries, each an aggregate or a short capped list — the page costs the same with ten
 * users as with ten thousand.
 */
@Service
public class SecurityOverviewService {

    /** How many rows each "needs attention" list shows before pointing at the full grid. */
    static final int ATTENTION_LIMIT = 8;
    static final int RECENT_EVENTS = 10;

    /** The events worth an administrator's eye; routine successful logins are not among them. */
    private static final List<Event> NOTABLE = List.of(
        Event.LOGIN_FAILED, Event.ACCOUNT_LOCKED, Event.LOGIN_REFUSED_INACTIVE,
        Event.ACCESS_DENIED, Event.SESSION_ENDED, Event.PASSWORD_CHANGED);

    private final FabricUserRepository users;
    private final RoleRepository roles;
    private final AccessLogRepository accessLog;
    private final OrgContext context;

    public SecurityOverviewService(FabricUserRepository users, RoleRepository roles,
                                   AccessLogRepository accessLog, OrgContext context) {
        this.users = users;
        this.roles = roles;
        this.accessLog = accessLog;
        this.context = context;
    }

    public record RoleCounts(long total, long active, long withoutGrants) { }

    public record Overview(UserCounts users, RoleCounts roles, Map<Event, Long> last24h,
                           List<FabricUser> locked, long withoutScopeCount, List<FabricUser> withoutScope,
                           List<AccessLogEntry> recentEvents) {

        public long last24h(Event event) {
            return last24h.getOrDefault(event, 0L);
        }

        // Named accessors for the template: Thymeleaf 3.1 restricts T(...) static access.
        public long failedLogins24h() { return last24h(Event.LOGIN_FAILED); }
        public long lockouts24h()     { return last24h(Event.ACCOUNT_LOCKED); }
        public long accessDenied24h() { return last24h(Event.ACCESS_DENIED); }
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Long orgId = context.requireOrganizationId();

        Map<Event, Long> last24h = new EnumMap<>(Event.class);
        for (Object[] row : accessLog.countByEventSince(orgId, LocalDateTime.now().minusHours(24))) {
            last24h.put((Event) row[0], (Long) row[1]);
        }

        LocalDate today = LocalDate.now();
        return new Overview(
            users.countSummary(orgId),
            new RoleCounts(roles.count(), roles.countActive(), roles.countWithoutGrants()),
            last24h,
            users.findLocked(orgId, PageRequest.of(0, ATTENTION_LIMIT)),
            users.countWithoutScope(orgId, today),
            users.findWithoutScope(orgId, today, PageRequest.of(0, ATTENTION_LIMIT)),
            accessLog.findRecent(orgId, NOTABLE, PageRequest.of(0, RECENT_EVENTS)));
    }
}

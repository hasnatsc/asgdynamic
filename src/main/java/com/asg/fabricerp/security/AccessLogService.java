package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes — and, for the administration screens, reads — the ADM-11 access log.
 *
 * <p>{@code REQUIRES_NEW}, always, for writes. A refusal is usually followed by an exception, and a log row
 * written inside the caller's transaction is rolled back by exactly the failure it records —
 * the control would appear to work while the log stayed permanently empty. asfl-erp's
 * {@code CredentialServiceImpl} records the same trap on its {@code noRollbackFor}.
 */
@Service
public class AccessLogService {

    private final AccessLogRepository repository;

    public AccessLogService(AccessLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String username, Event event, String target, String detail) {
        repository.save(new AccessLogEntry(userId, username, event, target, detail,
            sourceAddress(), LocalDateTime.now()));
    }

    /**
     * The access-log grid. Every filter is optional; the organization is not — see
     * {@link AccessLogRepository} for why that narrows through the user rather than a column.
     *
     * @param to inclusive: the whole of that day
     */
    @Transactional(readOnly = true)
    public Page<AccessLogEntry> search(Long organizationId, String username, Event event,
                                       LocalDate from, LocalDate to, Pageable pageable) {
        Specification<AccessLogEntry> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            Subquery<Long> orgUsers = query.subquery(Long.class);
            var user = orgUsers.from(FabricUser.class);
            orgUsers.select(user.get("id")).where(cb.equal(user.get("organizationId"), organizationId));
            where.add(root.get("userId").in(orgUsers));

            if (username != null && !username.isBlank()) {
                where.add(cb.like(cb.lower(root.get("username")), "%" + username.trim().toLowerCase() + "%"));
            }
            if (event != null) {
                where.add(cb.equal(root.get("event"), event));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay()));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    /**
     * The client address. {@code server.forward-headers-strategy=framework} is set, so behind a
     * proxy this is already the forwarded client rather than the proxy.
     */
    private static String sourceAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}

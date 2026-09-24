package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * Writes the ADM-11 access log.
 *
 * <p>{@code REQUIRES_NEW}, always. A refusal is usually followed by an exception, and a log row
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

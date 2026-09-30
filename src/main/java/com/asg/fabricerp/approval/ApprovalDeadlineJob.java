package com.asg.fabricerp.approval;

import com.asg.fabricerp.security.SystemActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Watches the clock on every pending approval level that has a time limit: reminds its approvers at
 * three quarters of the limit and takes the level's {@link TimeoutAction} when it runs out.
 *
 * <p>Each request is handled in its own transaction as {@code system} in the request's own
 * organization and unit ({@link SystemActor}), so one that fails is logged, marked and passed over
 * rather than holding up the rest - and optimistic locking on the request means a second node
 * running the same job cannot act on the same level twice.
 *
 * <p>{@code app.approval.deadlines.enabled=false} switches it off (a test database, say);
 * {@code app.approval.deadlines.interval-ms} sets how often it looks - once a minute by default.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.approval.deadlines.enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalDeadlineJob {

    private static final Logger log = LoggerFactory.getLogger(ApprovalDeadlineJob.class);

    /** At most this many of each per run; the rest wait a minute. */
    private static final int BATCH = 100;

    private final ApprovalRequestRepository requests;
    private final ApprovalService approvals;
    private final SystemActor system;

    public ApprovalDeadlineJob(ApprovalRequestRepository requests, ApprovalService approvals, SystemActor system) {
        this.requests = requests;
        this.approvals = approvals;
        this.system = system;
    }

    @Scheduled(initialDelayString = "${app.approval.deadlines.initial-delay-ms:30000}",
               fixedDelayString = "${app.approval.deadlines.interval-ms:60000}")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> reminders = requests.findRemindersDue(now, PageRequest.of(0, BATCH));
        for (Long id : reminders) {
            inSystem(id, () -> approvals.remind(id), null);
        }
        List<Long> timeouts = requests.findTimeoutsDue(now, PageRequest.of(0, BATCH));
        for (Long id : timeouts) {
            inSystem(id, () -> approvals.timeOut(id), error -> approvals.timeOutFailed(id, error));
        }
        if (!reminders.isEmpty() || !timeouts.isEmpty()) {
            log.info("Approval deadlines: {} reminder(s), {} timeout(s) handled", reminders.size(), timeouts.size());
        }
    }

    private void inSystem(Long requestId, Runnable work, java.util.function.Consumer<String> onFailure) {
        ApprovalRequest request = requests.findById(requestId).orElse(null);
        if (request == null) return;
        try {
            system.run(request.getOrganizationId(), request.getBusinessUnitId(), work);
        } catch (RuntimeException e) {
            log.warn("Approval request {}: deadline action failed - {}", requestId, e.getMessage());
            if (onFailure == null) return;
            try {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                system.run(request.getOrganizationId(), request.getBusinessUnitId(),
                    () -> onFailure.accept(reason.endsWith(".") ? reason : reason + "."));
            } catch (RuntimeException again) {
                log.error("Approval request {}: could not record the failed deadline action", requestId, again);
            }
        }
    }
}

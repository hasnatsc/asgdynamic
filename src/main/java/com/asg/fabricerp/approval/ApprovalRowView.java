package com.asg.fabricerp.approval;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One request in the Approvals inbox or report. {@code screenPath} opens the document on its own screen;
 * {@code dueAt} is when its current level runs out of time (null with no limit), {@code escalated}
 * whether that level was handed on because it did.
 */
public record ApprovalRowView(Long requestId, Long documentId, String documentType, String documentTypeLabel,
                              String documentNo, String partyName, String teamName, BigDecimal amount, String currency,
                              int level, int totalLevels, String approver, String scope,
                              String raisedBy, LocalDateTime submittedAt, boolean pending,
                              ApprovalDecision outcome, LocalDateTime settledAt, String screenPath,
                              LocalDateTime dueAt, boolean escalated) { }

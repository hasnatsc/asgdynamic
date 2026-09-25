package com.asg.fabricerp.approval;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One request in the Approvals inbox or report. {@code screenPath} opens the document on its own screen. */
public record ApprovalRowView(Long requestId, Long documentId, String documentType, String documentTypeLabel,
                              String documentNo, String partyName, String teamName, BigDecimal amount, String currency,
                              int level, int totalLevels, String approver, String scope,
                              String raisedBy, LocalDateTime submittedAt, boolean pending,
                              ApprovalDecision outcome, LocalDateTime settledAt, String screenPath) { }

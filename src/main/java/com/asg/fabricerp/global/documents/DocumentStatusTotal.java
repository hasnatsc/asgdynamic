package com.asg.fabricerp.global.documents;

import java.math.BigDecimal;

/**
 * One status's share of a list screen ({@link BusinessDocumentRepository#statusTotals}): how many
 * documents, their total quantity, their value in BDT (amount × rate to BDT, so import POs in USD
 * add up with local ones), and how many are past their required date.
 *
 * <p>Boxed because that is how the query's aggregates arrive; a sum over no values is null, read
 * here as zero.
 */
public record DocumentStatusTotal(BusinessDocumentStatus status, Long documents, BigDecimal quantity,
                                  BigDecimal value, Long overdue) {

    public DocumentStatusTotal {
        documents = documents == null ? 0L : documents;
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        value = value == null ? BigDecimal.ZERO : value;
        overdue = overdue == null ? 0L : overdue;
    }
}

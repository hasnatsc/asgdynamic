package com.asg.fabricerp.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Ask the ledger to post a business event.
 *
 * <p>The caller supplies the <em>event</em> and the amounts, never account codes - that is the
 * point of the rule table. A module that names an account has taken a decision belonging to
 * Accounts, and the next chart reorganisation will find it.
 *
 * @param amounts named amounts the rule legs draw from: usually a single {@code amount}; an invoice
 *                carries {@code net} and {@code vat} so the rule can split them
 * @param fxRate  rate to BDT; {@code null} means 1
 */
public record PostingCommand(
        String docTypeCode,
        Long documentId,
        String eventType,
        LocalDate postingDate,
        String currencyCode,
        BigDecimal fxRate,
        Map<String, BigDecimal> amounts,
        Long partyId,
        String costCentreCode,
        String narration) {

    public PostingCommand {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(postingDate, "postingDate");
        amounts = Map.copyOf(Objects.requireNonNull(amounts, "amounts"));
        if (amounts.isEmpty()) {
            throw new IllegalArgumentException("A posting must carry at least one amount.");
        }
        amounts.forEach((key, value) -> {
            if (value == null || value.signum() <= 0) {
                throw new IllegalArgumentException("Posting amount '" + key + "' must be positive, got " + value);
            }
        });
    }

    /** The single-amount, BDT case - most of the rule table. */
    public static PostingCommand of(String docTypeCode, Long documentId, String eventType, LocalDate postingDate,
                                    BigDecimal amount, Long partyId, String narration) {
        return new PostingCommand(docTypeCode, documentId, eventType, postingDate, "BDT", BigDecimal.ONE,
            Map.of("amount", amount), partyId, null, narration);
    }
}

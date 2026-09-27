package com.asg.fabricerp.commercial;

import com.asg.fabricerp.accounts.GeneralLedgerService;
import com.asg.fabricerp.accounts.GlEntry;
import com.asg.fabricerp.accounts.PostingCommand;
import com.asg.fabricerp.accounts.PostingEvent;
import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.global.documents.BusinessDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an export CI means to the ledger, through the posting rules - never naming an account here.
 * Approved, it raises the buyer's receivable ({@link PostingEvent#INVOICE}); its final payment clears
 * it ({@link PostingEvent#RECEIPT}); both in the CI's currency at its rate to taka, the buyer on the
 * control line. Cancelling the CI or undoing the payment reverses the entry.
 *
 * <p>Posted only where Accounts is set up for the date, and only with a rule that takes one figure
 * (an export invoice carries no VAT to split): otherwise the document's history says why not.
 */
@Component
public class CommercialAccounting {

    /** The amount keys a single export figure can answer. */
    private static final Set<String> SINGLE = Set.of("amount", "net");

    private final GeneralLedgerService ledger;
    private final NamedParameterJdbcTemplate jdbc;

    public CommercialAccounting(GeneralLedgerService ledger, NamedParameterJdbcTemplate jdbc) {
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    public String invoice(BusinessDocument ci) {
        return post(PostingEvent.INVOICE, ci, ci.getSubtotalAmount(), ci.getDocumentDate(), "Export CI " + ci.getDocumentNo());
    }

    public String receipt(BusinessDocument ci, BigDecimal amount, LocalDate on) {
        return post(PostingEvent.RECEIPT, ci, amount, on, "Realization of " + ci.getDocumentNo());
    }

    private String post(String event, BusinessDocument doc, BigDecimal amount, LocalDate on, String narration) {
        if (amount == null || amount.signum() <= 0) return "Not posted to accounts: nothing to post";
        if (!ledger.canPost(event, on)) {
            return "Not posted to accounts: no %s rule or no open accounting period for %s".formatted(event, on);
        }
        Set<String> keys = ledger.amountKeys(event, on);
        if (!SINGLE.containsAll(keys)) {
            return "Not posted to accounts: the %s rule splits %s; post it by journal".formatted(event, String.join(", ", keys));
        }
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        keys.forEach(k -> amounts.put(k, amount));
        GlEntry entry = ledger.post(new PostingCommand(doc.getDocumentType().name(), doc.getId(), event, on, doc.getCurrencyCode(),
            doc.getExchangeRate(), amounts, AuditableEntity.idOf(doc.getParty()), null,
            narration + (doc.getParty() == null ? "" : " - " + doc.getParty().getName())));
        return "Posted to accounts as %s (%s %s)".formatted(entry.getEntryNo(), amount.stripTrailingZeros().toPlainString(), doc.getCurrencyCode());
    }

    /** Reverses what the document posted for {@code event}; returns a note, or null if it posted nothing. */
    public String reverse(BusinessDocument doc, String event, String reason) {
        List<Long> entries = jdbc.queryForList("""
            SELECT id FROM acc_gl_entries
            WHERE organization_id = :org AND doc_type_code = :type AND document_id = :doc AND event_type = :event
              AND reverses_entry_id IS NULL AND reversed_by_id IS NULL
            """, new MapSqlParameterSource("org", doc.getOrganizationId()).addValue("type", doc.getDocumentType().name())
                .addValue("doc", doc.getId()).addValue("event", event), Long.class);
        if (entries.isEmpty()) return null;
        StringBuilder note = new StringBuilder("Accounts reversed:");
        for (Long id : entries) note.append(' ').append(ledger.reverse(id, reason).getEntryNo());
        return note.toString();
    }
}

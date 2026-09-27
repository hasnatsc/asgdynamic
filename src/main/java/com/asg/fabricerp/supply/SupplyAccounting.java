package com.asg.fabricerp.supply;

import com.asg.fabricerp.accounts.GeneralLedgerService;
import com.asg.fabricerp.accounts.GlEntry;
import com.asg.fabricerp.accounts.PostingCommand;
import com.asg.fabricerp.accounts.PostingEvent;
import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * What a posted MRR and a purchase return mean to the ledger, through the posting rules - never
 * naming an account here. An MRR raises the goods-received liability to its supplier
 * ({@link PostingEvent#GRN}); a return takes it back ({@link PostingEvent#PURCHASE_RETURN}); both at
 * the purchase price in taka. Cancelling either reverses its entry.
 *
 * <p>Posted only where Accounts is set up for the date (a rule for the event and an open
 * accounting period): a unit that keeps its books elsewhere still runs its stores. The document's
 * history says which way it went.
 */
@Component
public class SupplyAccounting {

    private final GeneralLedgerService ledger;
    private final NamedParameterJdbcTemplate jdbc;

    public SupplyAccounting(GeneralLedgerService ledger, NamedParameterJdbcTemplate jdbc) {
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    /** Posts the document's purchase value; returns a note for its history. */
    public String post(SupplyStep step, BusinessDocument doc) {
        String event = switch (step) {
            case MRR -> PostingEvent.GRN;
            case PRT -> PostingEvent.PURCHASE_RETURN;
            default -> null;
        };
        if (event == null) return null;
        BigDecimal amount = purchaseValue(doc);
        if (amount.signum() <= 0) return "Not posted to accounts: the goods carry no price";
        if (!ledger.canPost(event, doc.getDocumentDate())) {
            return "Not posted to accounts: no %s rule or no open accounting period for %s".formatted(event, doc.getDocumentDate());
        }
        GlEntry entry = ledger.post(PostingCommand.of(doc.getDocumentType().name(), doc.getId(), event, doc.getDocumentDate(),
            amount, AuditableEntity.idOf(doc.getParty()), "%s %s%s".formatted(step.label(), doc.getDocumentNo(),
                doc.getParty() == null ? "" : " - " + doc.getParty().getName())));
        return "Posted to accounts as %s (%s BDT)".formatted(entry.getEntryNo(), amount.stripTrailingZeros().toPlainString());
    }

    /** Reverses whatever the document posted; returns a note for its history, or null if it posted nothing. */
    public String reverse(BusinessDocument doc, String reason) {
        List<Long> entries = jdbc.queryForList("""
            SELECT id FROM acc_gl_entries
            WHERE organization_id = :org AND doc_type_code = :type AND document_id = :doc
              AND reverses_entry_id IS NULL AND reversed_by_id IS NULL
            """, new MapSqlParameterSource("org", doc.getOrganizationId()).addValue("type", doc.getDocumentType().name())
                .addValue("doc", doc.getId()), Long.class);
        if (entries.isEmpty()) return null;
        StringBuilder note = new StringBuilder("Accounts reversed:");
        for (Long id : entries) note.append(' ').append(ledger.reverse(id, reason).getEntryNo());
        return note.toString();
    }

    /** Quantity × price × the document's rate to taka, over every line. */
    static BigDecimal purchaseValue(BusinessDocument doc) {
        BigDecimal fx = doc.getExchangeRate() == null ? BigDecimal.ONE : doc.getExchangeRate();
        BigDecimal total = BigDecimal.ZERO;
        for (BusinessDocumentColorLine l : SupplyDraws.lines(doc)) {
            total = total.add(l.getQuantity().multiply(l.getRate()));
        }
        return total.multiply(fx).setScale(2, RoundingMode.HALF_UP);
    }
}

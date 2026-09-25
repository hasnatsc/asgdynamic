package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Posting to the general ledger - the one way entries are created.
 *
 * <p>Resolve the rule, check the period, build the entry, prove it balances, save - in that order,
 * and the order is the design: a posting into a closed period leaves no trace at all, and the
 * balance check runs before the save because there is no draft state.
 *
 * <p>Entry numbers come from the voucher series of the entry's {@link VoucherType} (Document numbering >
 * Accounts): JV, PV, RV, CV, SV, PUV, PDV.
 */
@Service
@Transactional(readOnly = true)
public class GeneralLedgerService {

    private final PostingRuleRepository rules;
    private final AccountRepository accounts;
    private final AccountingPeriodRepository periods;
    private final GlEntryRepository entries;
    private final BusinessNumberService numbers;
    private final OrgContext context;

    public GeneralLedgerService(PostingRuleRepository rules, AccountRepository accounts,
                                AccountingPeriodRepository periods, GlEntryRepository entries,
                                BusinessNumberService numbers, OrgContext context) {
        this.rules = rules;
        this.accounts = accounts;
        this.periods = periods;
        this.entries = entries;
        this.numbers = numbers;
        this.context = context;
    }

    /** A manual journal line: account code, side, amount, optional cost centre and narration. */
    public record JournalLine(String accountCode, Side side, BigDecimal amount, String costCentreCode, String narration) { }

    /**
     * Derives an entry from the posting rule for this event and posts it.
     *
     * @return the ledger entry
     * @throws NoPostingRuleException   when no rule covers the event on the posting date
     * @throws ClosedPeriodException    when the date falls in a closed period, or none
     * @throws UnbalancedEntryException when the rule does not balance for these amounts
     * @throws ControlAccountException  when a control-account line names no party
     */
    @Transactional
    public GlEntry post(PostingCommand command) {
        Long orgId = context.requireOrganizationId();
        AccountingPeriod period = openPeriodFor(orgId, command.postingDate());
        PostingRule rule = findRule(orgId, command.eventType(), command.postingDate())
            .orElseThrow(() -> new NoPostingRuleException(command.eventType(), command.postingDate()));
        rule.requireBalanced(command.amounts());

        GlEntry entry = newEntry(orgId, VoucherType.forEvent(command.eventType()), command.docTypeCode(), command.documentId(),
            command.eventType(), command.postingDate(), period, command.currencyCode(), command.fxRate(), command.narration());
        for (PostingRuleLine leg : rule.getLines()) {
            addLine(orgId, entry, leg.getAccountCode(), leg.getSide(), rule.amountFor(leg, command.amounts()),
                command.partyId(), command.costCentreCode(),
                leg.getNarration() == null ? command.narration() : leg.getNarration(), false);
        }
        entry.requireBalanced();
        return entries.save(entry);
    }

    /**
     * Posts a hand-entered journal. It names its own accounts - and therefore may not touch a control
     * account, which is the whole reason a manual journal is distinguishable from a rule posting.
     */
    @Transactional
    public GlEntry postManualJournal(LocalDate postingDate, String narration, List<JournalLine> lines) {
        return postManualJournal(VoucherType.JOURNAL, postingDate, narration, lines);
    }

    /**
     * As above, numbered as a journal, payment, receipt or contra voucher. The type changes the number
     * series only; the control-account rule applies to all four.
     */
    @Transactional
    public GlEntry postManualJournal(VoucherType type, LocalDate postingDate, String narration, List<JournalLine> lines) {
        VoucherType voucher = type == null ? VoucherType.JOURNAL : type;
        if (!voucher.manual()) {
            throw new IllegalArgumentException(voucher.label() + "s are posted from their documents, not by hand.");
        }
        Long orgId = context.requireOrganizationId();
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("A journal needs at least one debit and one credit line.");
        }
        AccountingPeriod period = openPeriodFor(orgId, postingDate);
        GlEntry entry = newEntry(orgId, voucher, GlEntry.MANUAL, null, PostingEvent.MANUAL_JOURNAL, postingDate, period,
            "BDT", BigDecimal.ONE, narration);
        for (JournalLine line : lines) {
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new IllegalArgumentException("Every journal line needs a positive amount.");
            }
            addLine(orgId, entry, line.accountCode(), line.side(), line.amount(), null, line.costCentreCode(),
                line.narration() == null || line.narration().isBlank() ? narration : line.narration(), true);
        }
        entry.requireBalanced();
        return entries.save(entry);
    }

    /**
     * Reverses an entry by posting its mirror image, dated today. Not a delete and not an edit: the
     * original stays, the reversal references it, and the pair nets to zero - so someone six months
     * later can see that a correction happened rather than wondering why a number changed.
     */
    @Transactional
    public GlEntry reverse(long entryId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reversal needs a reason; it stays on the ledger.");
        }
        Long orgId = context.requireOrganizationId();
        GlEntry original = entries.findScoped(entryId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("No ledger entry " + entryId + "."));
        if (original.isReversed()) {
            throw new IllegalStateException("Entry " + original.getEntryNo() + " has already been reversed.");
        }
        if (original.getReversesEntryId() != null) {
            throw new IllegalStateException("Entry " + original.getEntryNo()
                + " is itself a reversal. Post a new entry instead of reversing a reversal.");
        }
        LocalDate today = LocalDate.now();
        AccountingPeriod period = openPeriodFor(orgId, today);
        GlEntry reversal = newEntry(orgId, original.getVoucherType(), original.getDocTypeCode(), original.getDocumentId(), original.getEventType(),
            today, period, original.getCurrencyCode(), original.getFxRate(),
            "Reversal of " + original.getEntryNo() + ": " + reason.trim());
        // Same lines, opposite sides - not negated amounts: a negative debit and a credit read the
        // same to a person, and only one survives a sum over the side column.
        for (GlEntryLine line : original.getLines()) {
            reversal.addLine(line.getAccountCode(), line.getSide().opposite(), line.getAmount(),
                line.getPartyId(), line.getCostCentreCode(), "Reversal");
        }
        reversal.markReversalOf(original.getId());
        reversal.requireBalanced();
        GlEntry saved = entries.save(reversal);
        original.markReversedBy(saved.getId());
        return saved;
    }

    /** Whether an event can currently be posted - for a caller that wants to check first. */
    public boolean canPost(String eventType, LocalDate on) {
        Long orgId = context.requireOrganizationId();
        return findRule(orgId, eventType, on).isPresent()
            && periods.findCovering(orgId, on).filter(AccountingPeriod::acceptsPostings).isPresent();
    }

    // ------------------------------------------------------------------------------------------

    private GlEntry newEntry(Long orgId, VoucherType voucher, String docType, Long documentId, String event, LocalDate date,
                             AccountingPeriod period, String currency, BigDecimal fxRate, String narration) {
        GlEntry entry = new GlEntry(context.businessUnitId(), numbers.next(voucher.series(), date),
            docType, documentId, event, date, period.getId(), currency, fxRate, narration).asVoucher(voucher);
        entry.setOrganizationId(orgId);
        return entry;
    }

    private void addLine(Long orgId, GlEntry entry, String accountCode, Side side, BigDecimal amount, Long partyId,
                         String costCentreCode, String narration, boolean manual) {
        Account account = accounts.findByCode(orgId, accountCode)
            .orElseThrow(() -> new IllegalStateException("Account " + accountCode + " is not in the chart of accounts."));
        if (!account.acceptsPostings()) {
            throw new IllegalStateException("Account " + accountCode + " (" + account.getName() + ") "
                + (account.isSummary() ? "has sub-accounts and takes no entries: post to one of them."
                                       : "is inactive and takes no entries."));
        }
        if (account.isControl()) {
            if (manual) throw ControlAccountException.manualJournal(accountCode);
            if (partyId == null) throw ControlAccountException.missingParty(accountCode);
        }
        entry.addLine(accountCode, side, amount, account.isControl() ? partyId : null,
            costCentreCode == null || costCentreCode.isBlank() ? null : costCentreCode.trim(), narration);
    }

    private AccountingPeriod openPeriodFor(Long orgId, LocalDate on) {
        AccountingPeriod period = periods.findCovering(orgId, on)
            .orElseThrow(() -> new ClosedPeriodException(on,
                "no accounting period covers that date - generate the fiscal year under Accounting setup"));
        if (!period.acceptsPostings()) {
            throw new ClosedPeriodException(on, "period " + period.getName() + " is closed");
        }
        return period;
    }

    private Optional<PostingRule> findRule(Long orgId, String eventType, LocalDate on) {
        // Latest effective rule wins; the unique index keeps at most one open-ended rule per event.
        return rules.forEvent(orgId, eventType).stream()
            .filter(rule -> rule.coversDate(on))
            .max(Comparator.comparing(PostingRule::getEffectiveFrom));
    }
}

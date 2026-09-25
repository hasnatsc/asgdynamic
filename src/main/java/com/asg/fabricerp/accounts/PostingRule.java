package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One row of the event-to-entry table: an event, and the entry it produces.
 *
 * <p>Nobody types a voucher for a routine operation. A yarn issue to weaving debits WIP and credits
 * yarn stock because a rule row says so - not because a service method names two account codes.
 * Account codes in rows mean a renumbered chart is an afternoon of configuration, and the mill's
 * accountant can read the mapping.
 *
 * <p>Effective-dated: a posting made in March used March's mapping, and a June reorganisation of
 * the chart must not change what re-deriving it would produce.
 */
@Entity
@Table(name = "acc_posting_rules")
public class PostingRule extends BaseOrgEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(length = 500)
    private String description;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<PostingRuleLine> lines = new ArrayList<>();

    protected PostingRule() { }

    public PostingRule(String code, String name, String eventType, LocalDate effectiveFrom) {
        this.code = code;
        this.name = name;
        this.eventType = eventType;
        this.effectiveFrom = effectiveFrom;
    }

    public PostingRuleLine addLine(Side side, String accountCode, String amountKey) {
        PostingRuleLine line = new PostingRuleLine(this, lines.size() + 1, side, accountCode, amountKey);
        lines.add(line);
        return line;
    }

    /** The two-sided case, which is most of the table. */
    public PostingRule debitAndCredit(String debitAccountCode, String creditAccountCode) {
        addLine(Side.DEBIT, debitAccountCode, "amount");
        addLine(Side.CREDIT, creditAccountCode, "amount");
        return this;
    }

    public boolean coversDate(LocalDate on) {
        return !on.isBefore(effectiveFrom) && (effectiveTo == null || !on.isAfter(effectiveTo));
    }

    /** Ends this rule the day before its successor starts. Rules are superseded, never edited. */
    public void supersededFrom(LocalDate successorFrom) {
        if (!successorFrom.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("A new rule for " + eventType + " must start after "
                + effectiveFrom + ", when the current one began.");
        }
        this.effectiveTo = successorFrom.minusDays(1);
    }

    /**
     * Proves the rule balances when every named amount is 1 - i.e. each amount key is debited as
     * much as it is credited. Caught when the rule is saved, not months later when a rare event
     * first tries to post through it.
     */
    public void requireSelfBalancing() {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Posting rule " + code + " has no lines, so it posts nothing.");
        }
        // Per amount key, as many debit legs as credit legs: then the rule balances whatever the
        // amounts are. An invoice is Dr receivable (net) + Dr receivable (vat) against
        // Cr sales (net) + Cr VAT output (vat).
        Map<String, Integer> legBalance = new java.util.TreeMap<>();
        for (PostingRuleLine line : lines) {
            legBalance.merge(line.getAmountKey(), line.getSide() == Side.DEBIT ? 1 : -1, Integer::sum);
        }
        legBalance.forEach((key, balance) -> {
            if (balance != 0) {
                throw new IllegalArgumentException("Posting rule " + code + " does not balance for amount '" + key
                    + "': it needs as many debit lines as credit lines drawing that amount.");
            }
        });
    }

    /** Checks the rule balances for the amounts a posting actually carries. */
    public void requireBalanced(Map<String, BigDecimal> amounts) {
        requireSelfBalancing();
        BigDecimal debits = sideTotal(Side.DEBIT, amounts);
        BigDecimal credits = sideTotal(Side.CREDIT, amounts);
        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedEntryException(null, debits, credits, "posting rule " + code);
        }
    }

    private BigDecimal sideTotal(Side side, Map<String, BigDecimal> amounts) {
        return lines.stream().filter(l -> l.getSide() == side)
            .map(l -> amountFor(l, amounts))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The amount a leg draws. A rule asking for "vat" of a posting that has none fails, not zero. */
    BigDecimal amountFor(PostingRuleLine line, Map<String, BigDecimal> amounts) {
        BigDecimal amount = amounts.get(line.getAmountKey());
        if (amount == null) {
            throw new IllegalArgumentException("Posting rule " + code + " line " + line.getSequence()
                + " needs amount '" + line.getAmountKey() + "', which this posting does not carry. It has: "
                + amounts.keySet());
        }
        return amount;
    }

    public void describe(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getCode()                { return code; }
    public String getName()                { return name; }
    public String getEventType()           { return eventType; }
    public LocalDate getEffectiveFrom()    { return effectiveFrom; }
    public LocalDate getEffectiveTo()      { return effectiveTo; }
    public String getDescription()         { return description; }
    public List<PostingRuleLine> getLines() { return List.copyOf(lines); }
}

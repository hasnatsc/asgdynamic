package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One leg of a posting rule. Lines rather than a debit/credit pair, because real entries are not
 * always two-sided: an invoice is Dr receivable, Cr sales <em>and</em> Cr VAT output.
 */
@Entity
@Table(name = "acc_posting_rule_lines")
public class PostingRuleLine extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false)
    private PostingRule rule;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Side side;

    /** By code, so a chart can be rebuilt without rewriting every rule. */
    @Column(name = "account_code", nullable = false, length = 40)
    private String accountCode;

    /** Which of the posting's named amounts this leg takes; "amount" is the whole of it. */
    @Column(name = "amount_key", nullable = false, length = 40)
    private String amountKey = "amount";

    @Column(length = 200)
    private String narration;

    protected PostingRuleLine() { }

    PostingRuleLine(PostingRule rule, int sequence, Side side, String accountCode, String amountKey) {
        this.rule = rule;
        this.sequence = sequence;
        this.side = side;
        this.accountCode = accountCode;
        this.amountKey = amountKey == null || amountKey.isBlank() ? "amount" : amountKey.trim();
        setOrganizationId(rule.getOrganizationId());
    }

    public PostingRule getRule()    { return rule; }
    public int getSequence()        { return sequence; }
    public Side getSide()           { return side; }
    public String getAccountCode()  { return accountCode; }
    public String getAmountKey()    { return amountKey; }
    public String getNarration()    { return narration; }
}

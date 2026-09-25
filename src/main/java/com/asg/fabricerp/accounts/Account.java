package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.AccountType;
import com.asg.fabricerp.accounts.AccountFlags.AccountUsage;
import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One account in the chart.
 *
 * <p><b>Summary accounts take no entries.</b> A node with children is {@link AccountUsage#SUMMARY}:
 * the moment an entry lands on a parent as well as on its children, the parent's balance stops
 * being the sum of its children and every statement built from the tree is wrong by that amount.
 *
 * <p><b>Control accounts.</b> Receivables and payables are {@link AccountUsage#CONTROL}: every line
 * names its party and manual journals are refused, so the party ledgers reconcile continuously.
 */
@Entity
@Table(name = "acc_accounts")
public class Account extends BaseOrgEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20, updatable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 20)
    private AccountUsage usage = AccountUsage.GENERAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parent;

    /** Books are in BDT; an account may be restricted to one currency (e.g. a USD bank account). */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(length = 500)
    private String description;

    protected Account() { }

    public Account(String code, String name, AccountType accountType) {
        this.code = code;
        this.name = name;
        this.accountType = accountType;
    }

    /** The side a positive balance on this account falls on. */
    public Side naturalSide()        { return accountType.naturalSide(); }
    public boolean isControl()       { return usage == AccountUsage.CONTROL; }
    public boolean isSummary()       { return usage == AccountUsage.SUMMARY; }
    public boolean acceptsPostings() { return usage != AccountUsage.SUMMARY && Boolean.TRUE.equals(getActive()); }

    /**
     * Places this account under a parent, which becomes a summary node.
     *
     * @throws IllegalArgumentException when the parent is of another type or the move is a cycle
     */
    public Account under(Account parent) {
        if (parent == null) {
            this.parent = null;
            return this;
        }
        if (parent.accountType != accountType) {
            throw new IllegalArgumentException("Account " + code + " is " + accountType.label()
                + " and cannot sit under " + parent.code + ", which is " + parent.accountType.label() + ".");
        }
        for (Account at = parent; at != null; at = at.parent) {
            if (at == this || (at.getId() != null && at.getId().equals(getId()))) {
                throw new IllegalArgumentException("Placing " + code + " under " + parent.code + " would be a loop.");
            }
        }
        if (parent.isControl()) {
            throw new IllegalArgumentException("Control account " + parent.code
                + " takes postings by party and cannot also be a summary of other accounts.");
        }
        parent.usage = AccountUsage.SUMMARY;
        this.parent = parent;
        return this;
    }

    /** Marks this a party subledger control account. */
    public Account asControl(boolean control) {
        if (control && usage == AccountUsage.SUMMARY) {
            throw new IllegalStateException("Account " + code + " has sub-accounts and cannot be a control account.");
        }
        if (usage != AccountUsage.SUMMARY) {
            this.usage = control ? AccountUsage.CONTROL : AccountUsage.GENERAL;
        }
        return this;
    }

    /** A summary node whose last child left takes postings again. */
    void releaseSummary() {
        if (usage == AccountUsage.SUMMARY) this.usage = AccountUsage.GENERAL;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Account inCurrency(String currencyCode) {
        this.currencyCode = currencyCode == null || currencyCode.isBlank() ? null : currencyCode.trim().toUpperCase();
        return this;
    }

    public Account describedAs(String description) {
        this.description = description;
        return this;
    }

    public String getCode()               { return code; }
    public String getName()               { return name; }
    public AccountType getAccountType()   { return accountType; }
    public AccountUsage getUsage()        { return usage; }
    public Account getParent()            { return parent; }
    public String getCurrencyCode()       { return currencyCode; }
    public String getDescription()        { return description; }
}

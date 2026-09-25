package com.asg.fabricerp.party;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;

/**
 * An account a party holds at a bank - where the bank is itself a {@link Party}. The legacy
 * {@code stakeholder} row encoded one bank each for three fixed roles in fifteen columns; a party
 * with two accounts could not be expressed. Here the bank's own name and SWIFT live once, on the
 * bank's party record, and branch-level details live on the account.
 */
@Entity
@Table(
    name = "pty_party_bank_accounts",
    uniqueConstraints = @UniqueConstraint(name = "uk_pty_account_per_bank",
        columnNames = {"party_id", "bank_party_id", "account_number"}),
    indexes = @Index(name = "ix_pty_account_bank", columnList = "bank_party_id"))
public class PartyBankAccount extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    /** A party holding {@code BANK}, checked on the way in by {@link Party#addBankAccount}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_party_id", nullable = false)
    private Party bank;

    @Column(name = "account_name", nullable = false, length = 200)
    private String accountName;

    @Column(name = "account_number", nullable = false, length = 60)
    private String accountNumber;

    @Column(name = "branch_name", length = 200)
    private String branchName;

    @Column(name = "routing_number", length = 40)
    private String routingNumber;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected PartyBankAccount() { }

    PartyBankAccount(Party party, Party bank, String accountName, String accountNumber) {
        this.party = party;
        this.bank = bank;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        setOrganizationId(party.getOrganizationId());
    }

    public PartyBankAccount at(String branchName, String routingNumber, String swiftCode) {
        this.branchName = branchName;
        this.routingNumber = routingNumber;
        this.swiftCode = swiftCode;
        return this;
    }

    public PartyBankAccount denominatedIn(String currency) {
        this.currencyCode = currency;
        return this;
    }

    void makePrimary()         { this.primary = true; }
    void setPrimary(boolean v) { this.primary = v; }

    /** @throws PartyRoleNotHeldException if {@code newBank} does not hold {@code BANK} */
    void update(Party newBank, String newAccountName, String newAccountNumber) {
        newBank.requireRole(PartyRoleType.BANK);
        if (newBank == party) {
            throw new IllegalArgumentException("A party cannot hold an account at itself.");
        }
        this.bank = newBank;
        this.accountName = newAccountName;
        this.accountNumber = newAccountNumber;
    }

    public Party getParty()            { return party; }
    public Party getBank()             { return bank; }
    public String getAccountName()     { return accountName; }
    public String getAccountNumber()   { return accountNumber; }
    public String getBranchName()      { return branchName; }
    public String getRoutingNumber()   { return routingNumber; }
    public String getSwiftCode()       { return swiftCode; }
    public String getCurrencyCode()    { return currencyCode; }
    public boolean isPrimary()         { return primary; }
}

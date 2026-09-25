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

import java.math.BigDecimal;

/**
 * One line of a ledger entry: a side and a positive amount, never separate debit and credit columns
 * (two columns invite a row with both filled or both empty). {@code partyId} is the subledger
 * link: the party ledger is a view over this table, not a second set of books that drifts from it.
 */
@Entity
@Table(name = "acc_gl_entry_lines")
public class GlEntryLine extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private GlEntry entry;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Column(name = "account_code", nullable = false, length = 40, updatable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private Side side;

    /** Always positive, in the entry currency. The sign lives in {@link #side}. */
    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    /** The same amount in BDT. */
    @Column(name = "functional_amount", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal functionalAmount;

    @Column(name = "party_id", updatable = false)
    private Long partyId;

    @Column(name = "cost_centre_code", length = 40, updatable = false)
    private String costCentreCode;

    @Column(length = 500, updatable = false)
    private String narration;

    protected GlEntryLine() { }

    GlEntryLine(GlEntry entry, int sequence, String accountCode, Side side, BigDecimal amount,
                BigDecimal functionalAmount, Long partyId, String costCentreCode, String narration) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A ledger line carries a positive amount, got " + amount
                + ". The side carries the sign.");
        }
        this.entry = entry;
        this.sequence = sequence;
        this.accountCode = accountCode;
        this.side = side;
        this.amount = amount;
        this.functionalAmount = functionalAmount;
        this.partyId = partyId;
        this.costCentreCode = costCentreCode;
        this.narration = narration;
        setOrganizationId(entry.getOrganizationId());
    }

    /** Debit positive, credit negative - for summing. */
    public BigDecimal signedFunctional() { return side == Side.DEBIT ? functionalAmount : functionalAmount.negate(); }

    public GlEntry getEntry()              { return entry; }
    public int getSequence()               { return sequence; }
    public String getAccountCode()         { return accountCode; }
    public Side getSide()                  { return side; }
    public BigDecimal getAmount()          { return amount; }
    public BigDecimal getFunctionalAmount() { return functionalAmount; }
    public Long getPartyId()               { return partyId; }
    public String getCostCentreCode()      { return costCentreCode; }
    public String getNarration()           { return narration; }
}

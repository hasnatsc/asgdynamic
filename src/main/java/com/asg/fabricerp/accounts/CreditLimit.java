package com.asg.fabricerp.accounts;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a customer may owe.
 *
 * <p><b>Secured exposure is not exposure.</b> {@link #securedAmount} - covered by an LC or bank
 * guarantee - is deducted before the limit applies, so LC-backed business is not refused.
 *
 * <p><b>A hold is not a zero limit.</b> "We decided not to ship" and "nobody has set a limit yet"
 * need opposite responses, so the hold is its own flag with a reason.
 *
 * <p><b>Effective-dated, never edited.</b> A booking confirmed in March was judged against March's
 * limit; changing the limit in June supersedes it rather than rewriting it.
 */
@Entity
@Table(name = "acc_credit_limits")
public class CreditLimit extends BaseOrgEntity {

    @Column(name = "party_id", nullable = false, updatable = false)
    private Long partyId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "BDT";

    @Column(name = "limit_amount", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal limitAmount;

    @Column(name = "secured_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal securedAmount = BigDecimal.ZERO;

    @Column(name = "on_hold", nullable = false)
    private boolean onHold;

    @Column(name = "hold_reason", length = 300)
    private String holdReason;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** When this limit should next be looked at. A stale limit is a limit nobody owns. */
    @Column(name = "review_on")
    private LocalDate reviewOn;

    @Column(length = 500)
    private String remarks;

    protected CreditLimit() { }

    public CreditLimit(Long partyId, String currencyCode, BigDecimal limitAmount, LocalDate effectiveFrom) {
        if (partyId == null) throw new IllegalArgumentException("A credit limit is granted to a named customer.");
        if (limitAmount == null || limitAmount.signum() < 0) {
            throw new IllegalArgumentException("A credit limit cannot be negative. Zero means cash in advance.");
        }
        this.partyId = partyId;
        this.currencyCode = currencyCode == null || currencyCode.isBlank() ? "BDT" : currencyCode;
        this.limitAmount = limitAmount;
        this.effectiveFrom = effectiveFrom;
    }

    public boolean coversDate(LocalDate on) {
        return Boolean.TRUE.equals(getActive()) && !on.isBefore(effectiveFrom)
            && (effectiveTo == null || !on.isAfter(effectiveTo));
    }

    /** What may still be committed, given exposure already incurred. */
    public BigDecimal headroomAgainst(BigDecimal exposure) {
        return limitAmount.subtract(exposure.subtract(securedAmount).max(BigDecimal.ZERO));
    }

    public boolean isOverdueForReview(LocalDate on) { return reviewOn != null && on.isAfter(reviewOn); }

    public void placeOnHold(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A credit hold stops a customer shipping and needs a stated reason.");
        }
        this.onHold = true;
        this.holdReason = reason.trim();
    }

    public void releaseHold() {
        this.onHold = false;
        this.holdReason = null;
    }

    public void securedBy(BigDecimal securedAmount) {
        if (securedAmount == null || securedAmount.signum() < 0) {
            throw new IllegalArgumentException("Secured cover cannot be negative.");
        }
        this.securedAmount = securedAmount;
    }

    public void supersededFrom(LocalDate successorFrom) {
        if (!successorFrom.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("A new limit must start after " + effectiveFrom + ", when this one began.");
        }
        this.effectiveTo = successorFrom.minusDays(1);
    }

    public CreditLimit reviewedOn(LocalDate reviewOn) { this.reviewOn = reviewOn; return this; }
    public CreditLimit noting(String remarks)         { this.remarks = remarks; return this; }

    public Long getPartyId()             { return partyId; }
    public String getCurrencyCode()      { return currencyCode; }
    public BigDecimal getLimitAmount()   { return limitAmount; }
    public BigDecimal getSecuredAmount() { return securedAmount; }
    public boolean isOnHold()            { return onHold; }
    public String getHoldReason()        { return holdReason; }
    public LocalDate getEffectiveFrom()  { return effectiveFrom; }
    public LocalDate getEffectiveTo()    { return effectiveTo; }
    public LocalDate getReviewOn()       { return reviewOn; }
    public String getRemarks()           { return remarks; }
}

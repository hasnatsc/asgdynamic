package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.PeriodStatus;
import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One month of the organization's fiscal year.
 *
 * <p>The year is stored rather than derived from the dates, so a change of year end does not
 * renumber closed history. Closing is one-way; reopening is a recorded event with a reason, kept
 * distinguishable ({@link PeriodStatus#TEMPORARILY_OPEN}) from a period never closed.
 */
@Entity
@Table(name = "acc_periods")
public class AccountingPeriod extends BaseOrgEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "fiscal_year", nullable = false, updatable = false)
    private int fiscalYear;

    /** 1 is the first month of the fiscal year. */
    @Column(name = "period_no", nullable = false, updatable = false)
    private int periodNo;

    @Column(name = "starts_on", nullable = false, updatable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false, updatable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_status", nullable = false, length = 20)
    private PeriodStatus periodStatus = PeriodStatus.OPEN;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    protected AccountingPeriod() { }

    public AccountingPeriod(String code, String name, int fiscalYear, int periodNo, LocalDate startsOn, LocalDate endsOn) {
        if (periodNo < 1 || periodNo > 12) {
            throw new IllegalArgumentException("Period number must be 1-12, got " + periodNo);
        }
        if (!endsOn.isAfter(startsOn)) {
            throw new IllegalArgumentException("Period " + code + " must end after it starts.");
        }
        this.code = code;
        this.name = name;
        this.fiscalYear = fiscalYear;
        this.periodNo = periodNo;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public boolean covers(LocalDate date)  { return !date.isBefore(startsOn) && !date.isAfter(endsOn); }
    public boolean acceptsPostings()       { return periodStatus.acceptsPostings(); }

    public void close(LocalDate on, String by) {
        if (periodStatus == PeriodStatus.CLOSED) {
            throw new IllegalStateException("Period " + name + " is already closed.");
        }
        this.periodStatus = PeriodStatus.CLOSED;
        this.closedOn = on;
        this.closedBy = by;
    }

    /**
     * Reopens a closed period. The close checklist no longer describes the books afterwards, so
     * the next close has to know why.
     */
    public void reopen(String reason) {
        if (periodStatus != PeriodStatus.CLOSED) {
            throw new IllegalStateException("Period " + name + " is not closed.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reopening " + name + " needs a stated reason.");
        }
        this.periodStatus = PeriodStatus.TEMPORARILY_OPEN;
        this.reopenReason = reason.trim();
    }

    public String getCode()               { return code; }
    public String getName()               { return name; }
    public int getFiscalYear()            { return fiscalYear; }
    public int getPeriodNo()              { return periodNo; }
    public LocalDate getStartsOn()        { return startsOn; }
    public LocalDate getEndsOn()          { return endsOn; }
    public PeriodStatus getPeriodStatus() { return periodStatus; }
    public LocalDate getClosedOn()        { return closedOn; }
    public String getClosedBy()           { return closedBy; }
    public String getReopenReason()       { return reopenReason; }
}

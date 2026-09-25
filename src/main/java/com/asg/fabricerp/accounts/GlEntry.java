package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.OrgScoped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One general ledger entry.
 *
 * <p><b>Append-only.</b> A correction is a reversing entry; V18's trigger refuses UPDATE and DELETE
 * (except linking an entry to its reversal). An editable ledger cannot be reconciled against
 * anything, because what it was reconciled against last week may not be what it says today.
 *
 * <p><b>Balance is structural.</b> {@link #requireBalanced()} runs before the entry is saved and
 * there is no draft state in which an unbalanced entry exists. It is checked in the entry currency
 * and again in BDT: a rounding difference in the conversion is a real imbalance in the books.
 */
@Entity
@Table(name = "acc_gl_entries")
public class GlEntry extends AuditableEntity implements OrgScoped {

    /** The doc type code of a hand-entered journal. */
    public static final String MANUAL = "MANUAL";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "business_unit_id", updatable = false)
    private Long businessUnitId;

    @Column(name = "entry_no", nullable = false, length = 60, updatable = false)
    private String entryNo;

    @Column(name = "doc_type_code", nullable = false, length = 40, updatable = false)
    private String docTypeCode;

    @Column(name = "document_id", updatable = false)
    private Long documentId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "posting_date", nullable = false, updatable = false)
    private LocalDate postingDate;

    @Column(name = "period_id", nullable = false, updatable = false)
    private Long periodId;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    /** Rate to BDT, six decimals. */
    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal fxRate = BigDecimal.ONE;

    @Column(length = 1000, updatable = false)
    private String narration;

    @Column(name = "reverses_entry_id", updatable = false)
    private Long reversesEntryId;

    /** The one column that changes after posting: set once, when this entry is reversed. */
    @Column(name = "reversed_by_id")
    private Long reversedById;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    private List<GlEntryLine> lines = new ArrayList<>();

    protected GlEntry() { }

    public GlEntry(Long businessUnitId, String entryNo, String docTypeCode, Long documentId, String eventType,
                   LocalDate postingDate, Long periodId, String currencyCode, BigDecimal fxRate, String narration) {
        this.businessUnitId = businessUnitId;
        this.entryNo = entryNo;
        this.docTypeCode = docTypeCode;
        this.documentId = documentId;
        this.eventType = eventType;
        this.postingDate = postingDate;
        this.periodId = periodId;
        this.currencyCode = currencyCode == null ? "BDT" : currencyCode;
        this.fxRate = fxRate == null ? BigDecimal.ONE : fxRate;
        this.narration = narration;
    }

    public GlEntryLine addLine(String accountCode, Side side, BigDecimal amount,
                               Long partyId, String costCentreCode, String narration) {
        BigDecimal functional = amount.multiply(fxRate).setScale(2, RoundingMode.HALF_UP);
        GlEntryLine line = new GlEntryLine(this, lines.size() + 1, accountCode, side,
            amount.setScale(2, RoundingMode.HALF_UP), functional, partyId, costCentreCode, narration);
        lines.add(line);
        return line;
    }

    public BigDecimal totalDebits()  { return total(Side.DEBIT, GlEntryLine::getAmount); }
    public BigDecimal totalCredits() { return total(Side.CREDIT, GlEntryLine::getAmount); }

    private BigDecimal total(Side side, Function<GlEntryLine, BigDecimal> of) {
        return lines.stream().filter(l -> l.getSide() == side).map(of).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Debits equal credits, in the entry currency and in BDT. */
    public void requireBalanced() {
        if (lines.size() < 2) {
            throw new UnbalancedEntryException(entryNo, totalDebits(), totalCredits(), currencyCode + " (an entry needs at least two lines)");
        }
        if (totalDebits().compareTo(totalCredits()) != 0) {
            throw new UnbalancedEntryException(entryNo, totalDebits(), totalCredits(), currencyCode);
        }
        BigDecimal debitsBdt = total(Side.DEBIT, GlEntryLine::getFunctionalAmount);
        BigDecimal creditsBdt = total(Side.CREDIT, GlEntryLine::getFunctionalAmount);
        if (debitsBdt.compareTo(creditsBdt) != 0) {
            throw new UnbalancedEntryException(entryNo, debitsBdt, creditsBdt,
                "BDT after conversion at " + fxRate.toPlainString() + " - add an explicit rounding line");
        }
    }

    void markReversedBy(Long reversalId) {
        if (reversedById != null) {
            throw new IllegalStateException("Entry " + entryNo + " has already been reversed.");
        }
        this.reversedById = reversalId;
    }

    void markReversalOf(Long entryId) { this.reversesEntryId = entryId; }

    @Override public Long getOrganizationId()        { return organizationId; }
    @Override public void setOrganizationId(Long id) { this.organizationId = id; }
    public Long getBusinessUnitId()      { return businessUnitId; }
    public String getEntryNo()           { return entryNo; }
    public String getDocTypeCode()       { return docTypeCode; }
    public Long getDocumentId()          { return documentId; }
    public String getEventType()         { return eventType; }
    public LocalDate getPostingDate()    { return postingDate; }
    public Long getPeriodId()            { return periodId; }
    public String getCurrencyCode()      { return currencyCode; }
    public BigDecimal getFxRate()        { return fxRate; }
    public String getNarration()         { return narration; }
    public Long getReversesEntryId()     { return reversesEntryId; }
    public Long getReversedById()        { return reversedById; }
    public boolean isReversed()          { return reversedById != null; }
    public List<GlEntryLine> getLines()  { return List.copyOf(lines); }
}

package com.asg.fabricerp.accounts;

import com.asg.fabricerp.global.numbering.BusinessSeries;

/**
 * The kind of voucher an entry is, and therefore the numbering series it is drawn from (Document
 * numbering > Accounts). Rule-driven postings get their type from the event; a manual journal is
 * one of the four {@link #manual() manual} types the accountant chooses.
 */
public enum VoucherType {
    JOURNAL("Journal voucher", BusinessSeries.JOURNAL_VOUCHER, true),
    PAYMENT("Payment voucher", BusinessSeries.PAYMENT_VOUCHER, true),
    RECEIPT("Receipt voucher", BusinessSeries.RECEIPT_VOUCHER, true),
    CONTRA("Contra voucher", BusinessSeries.CONTRA_VOUCHER, true),
    SALES("Sales voucher", BusinessSeries.SALES_VOUCHER, false),
    PURCHASE("Purchase voucher", BusinessSeries.PURCHASE_VOUCHER, false),
    PRODUCTION("Production voucher", BusinessSeries.PRODUCTION_VOUCHER, false);

    private final String label;
    private final BusinessSeries series;
    private final boolean manual;

    VoucherType(String label, BusinessSeries series, boolean manual) {
        this.label = label;
        this.series = series;
        this.manual = manual;
    }

    public String label()          { return label; }
    public BusinessSeries series() { return series; }
    /** Whether an accountant may pick this type for a hand-entered journal. */
    public boolean manual()        { return manual; }

    /** The voucher type a rule-driven event posts as. */
    public static VoucherType forEvent(String eventType) {
        return switch (eventType) {
            case PostingEvent.SUPPLIER_PAYMENT -> PAYMENT;
            case PostingEvent.RECEIPT, PostingEvent.RECEIPT_DEEMED_EXPORT, PostingEvent.RECEIPT_LOCAL,
                 PostingEvent.RECEIPT_WASTAGE -> RECEIPT;
            case PostingEvent.GRN, PostingEvent.SUPPLIER_BILL -> PURCHASE;
            case PostingEvent.DELIVERY, PostingEvent.INVOICE, PostingEvent.INVOICE_DEEMED_EXPORT,
                 PostingEvent.INVOICE_LOCAL, PostingEvent.INVOICE_WASTAGE -> SALES;
            case PostingEvent.YARN_ISSUE_TO_WEAVING, PostingEvent.GREIGE_RECEIVED_FROM_WEAVING,
                 PostingEvent.GREIGE_ISSUE_TO_BATCH, PostingEvent.CHEMICAL_ISSUE_TO_BATCH,
                 PostingEvent.UTILITY_CONSUMED, PostingEvent.FINISHED_ROLL_RECEIVED,
                 PostingEvent.ABNORMAL_LOSS -> PRODUCTION;
            default -> JOURNAL;   // depreciation, manual journals and anything not listed
        };
    }
}

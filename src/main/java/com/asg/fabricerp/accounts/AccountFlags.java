package com.asg.fabricerp.accounts;

/**
 * The vocabulary of the chart of accounts and the ledger. Ported from asfl-erp's accounts module.
 */
public final class AccountFlags {

    private AccountFlags() { }

    /**
     * Where an account sits in the statements, and which way it naturally moves.
     *
     * <p>{@link #naturalSide} decides whether a positive balance is a debit or a credit, which is
     * what lets a trial balance be built without a hand-maintained list of sign flips.
     */
    public enum AccountType {
        ASSET(Side.DEBIT, "Asset"),
        LIABILITY(Side.CREDIT, "Liability"),
        EQUITY(Side.CREDIT, "Equity"),
        INCOME(Side.CREDIT, "Income"),
        EXPENSE(Side.DEBIT, "Expense");

        private final Side naturalSide;
        private final String label;

        AccountType(Side naturalSide, String label) {
            this.naturalSide = naturalSide;
            this.label = label;
        }

        public Side naturalSide()       { return naturalSide; }
        public String label()           { return label; }
        public boolean isBalanceSheet() { return this == ASSET || this == LIABILITY || this == EQUITY; }
    }

    /** Which side of a double entry a line falls on. */
    public enum Side {
        DEBIT, CREDIT;

        public Side opposite() { return this == DEBIT ? CREDIT : DEBIT; }
    }

    /**
     * What may post to an account.
     *
     * <p>A {@link #CONTROL} account - receivables, payables - is posted only by subledger: every
     * line must name the party it belongs to, and a manual journal to one is refused. Without
     * that, the party ledgers and the GL drift, and the difference is found at year end.
     */
    public enum AccountUsage {
        /** Ordinary posting account. */
        GENERAL,
        /** Subledger control: every line names a party, and no manual journals. */
        CONTROL,
        /** A node with children. Takes no entries; its balance is its children's sum. */
        SUMMARY
    }

    /** What a cost centre is for; decides where payroll and overhead post, not just a label. */
    public enum CostCentreType {
        /** A shed, a line, a machine group. Direct cost lands here. */
        PRODUCTION,
        /** Absorbs what production does not - the machine-hour pool. */
        OVERHEAD,
        /** Marketing, Accounts, Commercial. Period cost, never absorbed into a batch. */
        ADMINISTRATIVE,
        /** Utilities, maintenance, stores - charged out to the centres they serve. */
        SERVICE
    }

    /** Whether a period accepts postings. */
    public enum PeriodStatus {
        OPEN,
        /** Reopened after close, with a recorded reason. Expected to be brief. */
        TEMPORARILY_OPEN,
        CLOSED;

        public boolean acceptsPostings() { return this != CLOSED; }
    }
}

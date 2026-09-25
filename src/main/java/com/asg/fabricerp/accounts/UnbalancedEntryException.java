package com.asg.fabricerp.accounts;

import java.math.BigDecimal;

/** Debits and credits differ. An unbalanced entry has no draft state: it is never saved. */
public class UnbalancedEntryException extends IllegalStateException {

    public UnbalancedEntryException(String entryNo, BigDecimal debits, BigDecimal credits, String in) {
        super("Entry " + (entryNo == null ? "" : entryNo + " ") + "does not balance in " + in
            + ": debits " + debits.toPlainString() + ", credits " + credits.toPlainString());
    }
}

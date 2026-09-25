package com.asg.fabricerp.accounts;

/**
 * A control account (receivable, payable) is posted only through its party subledger: every line
 * names the party, and a manual journal to one is refused.
 */
public class ControlAccountException extends IllegalStateException {

    private ControlAccountException(String message) {
        super(message);
    }

    public static ControlAccountException manualJournal(String accountCode) {
        return new ControlAccountException("Account " + accountCode + " is a control account and takes no manual "
            + "journals. Post it through the customer or supplier transaction instead.");
    }

    public static ControlAccountException missingParty(String accountCode) {
        return new ControlAccountException("Account " + accountCode + " is a control account: every line on it "
            + "must name the customer or supplier.");
    }
}

package com.asg.fabricerp.accounts;

import java.time.LocalDate;

/** No posting rule covers the event on that date, so nothing can post - loudly, not silently. */
public class NoPostingRuleException extends IllegalStateException {

    public NoPostingRuleException(String eventType, LocalDate on) {
        super("No posting rule covers event " + eventType + " on " + on
            + ". Add one under Accounts > Accounting setup > Posting rules.");
    }
}

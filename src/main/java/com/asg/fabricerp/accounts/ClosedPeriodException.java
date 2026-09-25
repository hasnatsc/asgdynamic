package com.asg.fabricerp.accounts;

import java.time.LocalDate;

/** Nothing posts into a closed period, or on a date no period covers. */
public class ClosedPeriodException extends IllegalStateException {

    public ClosedPeriodException(LocalDate on, String why) {
        super("Cannot post on " + on + ": " + why + ".");
    }
}

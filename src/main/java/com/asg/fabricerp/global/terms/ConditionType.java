package com.asg.fabricerp.global.terms;

/**
 * Which kind of document a standard clause belongs to - the legacy {@code conditionType} list,
 * kept whole so clauses already written for PI, CI and LC have somewhere to live.
 */
public enum ConditionType {
    BOOKING("Booking"),
    SALES_ORDER("Sales order"),
    PROFORMA_INVOICE("Proforma invoice"),
    PI("PI"),
    CI("CI"),
    LC("LC");

    private final String label;

    ConditionType(String label) { this.label = label; }

    public String label() { return label; }
}

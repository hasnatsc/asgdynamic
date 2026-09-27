package com.asg.fabricerp.global.documents;

/** The legacy store requisition's {@code requisitionType}: who the goods are for. */
public enum RequisitionType {
    PERSONAL("Personal"),
    DEPARTMENTAL("Departmental"),
    PRODUCTION("Production");

    private final String label;

    RequisitionType(String label) { this.label = label; }

    public String label() { return label; }
}

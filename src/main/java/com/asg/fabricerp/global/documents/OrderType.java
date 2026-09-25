package com.asg.fabricerp.global.documents;

/** Legacy {@code specialType}, shown as "Type of order" on the Booking screen. */
public enum OrderType {
    EXPORT("Export"),
    JOB_WORK("Job Work"),
    LUNGI("Lungi");

    private final String label;

    OrderType(String label) { this.label = label; }

    public String label() { return label; }
}

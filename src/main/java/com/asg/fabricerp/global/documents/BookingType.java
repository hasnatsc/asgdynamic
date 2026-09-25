package com.asg.fabricerp.global.documents;

/** Legacy {@code bookingType}: a bulk order or a sample yardage. */
public enum BookingType {
    BULK("Bulk"),
    SAMPLE("Sample");

    private final String label;

    BookingType(String label) { this.label = label; }

    public String label() { return label; }
}

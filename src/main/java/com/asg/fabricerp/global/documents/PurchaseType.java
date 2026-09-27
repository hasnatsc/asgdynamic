package com.asg.fabricerp.global.documents;

/** The legacy purchase order's {@code poType}: how the goods are bought. */
public enum PurchaseType {
    /** From a local supplier on the usual terms. */
    DIRECT("Direct"),
    /** Bought for cash on the spot, typically small and urgent. */
    SPOT("Spot"),
    /** From abroad - a proforma invoice and an import LC follow. */
    IMPORT("Import");

    private final String label;

    PurchaseType(String label) { this.label = label; }

    public String label() { return label; }
}

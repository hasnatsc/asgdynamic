package com.asg.fabricerp.global.documents;

/** What a Dyeing work order does to the greige: one document type, the shop floor differs. */
public enum ProcessKind {
    DYE("Dye"), PRINT("Dye and print"), FINISH("Finish only"), REWORK("Rework");

    private final String label;
    ProcessKind(String label) { this.label = label; }
    public String label() { return label; }
}

package com.asg.fabricerp.global.documents;

/** Yarn preparation a route needs before weaving - flagged on the Weaving WO for now. */
public enum YarnPrep {
    NONE("None"), YARN_DYE("Dyed yarn"), INDIGO("Indigo warp");

    private final String label;
    YarnPrep(String label) { this.label = label; }
    public String label() { return label; }
}

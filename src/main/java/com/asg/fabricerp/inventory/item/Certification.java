package com.asg.fabricerp.inventory.item;

/** Sustainability / origin certification carried by one fiber in a {@link YarnBlend}. */
public enum Certification {
    BCI("BCI"),
    ORGANIC_OCS("Organic (OCS)"),
    USCJP("USCJP"),
    PSCP("PSCP"),
    ORGANIC_GOTS("Organic (GOTS)"),
    BCI_PHYSICAL("BCI (Physical)"),
    CMIA("CMIA (Cotton made in Africa)"),
    RECYCLE_RCS("Recycle (RCS)"),
    RECYCLE_GRS("Recycle (GRS)"),
    SUPIMA("Supima"),
    GIZA("Giza"),
    ECOVERO_LIVA("Ecovero Liva"),
    ECOVERO_LENZING("Ecovero Lenzing"),
    LENZING("Lenzing"),
    FSC("FSC"),
    EUROPEAN_FLAX("European Flax");

    private final String label;

    Certification(String label) { this.label = label; }

    public String label() { return label; }
}

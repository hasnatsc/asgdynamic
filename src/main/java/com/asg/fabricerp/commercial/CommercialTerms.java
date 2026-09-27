package com.asg.fabricerp.commercial;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The controlled vocabulary of commercial paper - every list the legacy PI, LC and CI screens
 * offered as a dropdown, kept as enums so a PI can never carry a tenure nobody can read. The
 * database CHECKs in V32 hold the same values.
 */
public final class CommercialTerms {

    private CommercialTerms() { }

    /** A labelled constant, for the screens' dropdowns. */
    public interface Labelled {
        String name();
        String label();
    }

    public enum Tenure implements Labelled {
        AT_SIGHT("At sight", 0), D30("30 days", 30), D60("60 days", 60), D90("90 days", 90),
        D120("120 days", 120), D150("150 days", 150), D180("180 days", 180), TT("TT", 0);

        private final String label;
        private final int days;

        Tenure(String label, int days) { this.label = label; this.days = days; }

        public String label() { return label; }
        /** Days from the payment's start (acceptance, delivery...) to maturity. */
        public int days()     { return days; }
    }

    /** From when the tenure runs - the legacy "Payment" list. */
    public enum PaymentTerms implements Labelled {
        AT_SIGHT("At sight"), DATE_OF_DELIVERY("Date of delivery"), DATE_OF_ACCEPTANCE("Date of acceptance"),
        DATE_OF_NEGOTIATION("Date of negotiation"), DATE_OF_SHIPMENT("Date of shipment"), TT("TT");

        private final String label;

        PaymentTerms(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** INCO terms. The legacy list offered CPT, CFR and FOB; the rest are the ones a mill meets. */
    public enum IncoTerms implements Labelled {
        EXW("EXW - Ex works"), FCA("FCA - Free carrier"), CPT("CPT - Carriage paid to"), CIP("CIP - Carriage and insurance paid"),
        FOB("FOB - Free on board"), CFR("CFR - Cost and freight"), CIF("CIF - Cost, insurance and freight"), DAP("DAP - Delivered at place");

        private final String label;

        IncoTerms(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** A CI invoices delivered fabric (regular), or bills ahead of delivery against the LC (advance). */
    public enum CiKind implements Labelled {
        REGULAR("Regular CI"), ADVANCE("Advance CI");

        private final String label;

        CiKind(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** How an import is paid: an LC, a telegraphic transfer, or against the supplier's invoice. */
    public enum ImportDocType implements Labelled {
        LC("LC"), TT("TT"), INVOICE("Invoice");

        private final String label;

        ImportDocType(String label) { this.label = label; }

        public String label() { return label; }
    }

    public enum LcType implements Labelled {
        AT_SIGHT("At sight"), DEFERRED("Deferred"), UPAS("UPAS"), UPAS_SPSM("UPAS/SPSM"), EDF("EDF");

        private final String label;

        LcType(String label) { this.label = label; }

        public String label() { return label; }
    }

    /**
     * An export CI's way to cash, in order. A step may only be recorded once the ones before it
     * are; Purchase (the bank discounting the accepted bill) is optional; Final payment realizes it.
     */
    public enum RealizationStep implements Labelled {
        DOC_SUBMISSION("Document submission", false, false),
        PARTY_ACCEPTANCE("Party acceptance", false, false),
        BANK_SUBMISSION("Bank submission", false, false),
        BANK_ACCEPTANCE("Bank acceptance", false, false),
        PURCHASE("Purchase (bill discounted)", true, true),
        BANK_MATURITY("Bank maturity", false, false),
        FINAL_PAYMENT("Final payment", true, false);

        private final String label;
        private final boolean amount;
        private final boolean optional;

        RealizationStep(String label, boolean amount, boolean optional) {
            this.label = label;
            this.amount = amount;
            this.optional = optional;
        }

        public String label()        { return label; }
        /** The step records money received (purchase proceeds, final payment). */
        public boolean hasAmount()   { return amount; }
        /** The step may be skipped. */
        public boolean isOptional()  { return optional; }

        /** The steps that must be recorded before this one. */
        public List<RealizationStep> required() {
            return Arrays.stream(values()).filter(s -> s.ordinal() < ordinal() && !s.optional).toList();
        }
    }

    /**
     * An import PI's checkpoints on its way to an LC - the legacy "PI Approval Section". Management
     * approval is the approval matrix itself; these are the ones the commercial desk ticks off.
     */
    public enum ImportMilestone implements Labelled {
        CED("CED approval"), CNF("C&F approval"), BOND("Bond approval"), PI_CORRECTED("PI corrected"),
        LC_DRAFT("LC drafted"), LC_CORRECTED("LC corrected");

        private final String label;

        ImportMilestone(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** What may be recorded against a document after it is raised. */
    public enum EventKind implements Labelled {
        UD("Utilization declaration (UD)"), UP("Utilization permission (UP)"), BTB_LC("Back-to-back LC"),
        SALES_CONTRACT("Sales contract"), REQUIRED_DOC("Required document"), COST("Cost"),
        REALIZATION("Realization"), MILESTONE("Milestone");

        private final String label;

        EventKind(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** Raw material a back-to-back LC buys - the legacy BTB list's "Material Type". */
    public static final List<String> MATERIAL_TYPES = List.of("Yarn", "Dyes", "Chemical", "Fabric", "Accessories");

    /** The ports a mill imports through. Free text is kept; these are offered. */
    public static final List<String> PORTS = List.of("Chittagong", "Dhaka Airport", "Benapole", "Mongla", "Pangaon ICT");

    /** A dropdown's options: value and label. */
    public static List<Map<String, String>> options(Labelled[] values) {
        return Arrays.stream(values).map(v -> Map.of("value", v.name(), "label", v.label())).toList();
    }
}

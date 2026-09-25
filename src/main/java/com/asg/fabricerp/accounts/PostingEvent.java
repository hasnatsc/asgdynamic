package com.asg.fabricerp.accounts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The business events a posting rule can answer, keyed by code.
 *
 * <p>Codes rather than free strings because they are the keys a rule is looked up by: a typo in
 * one is a document that silently posts nothing. A module raising a new kind of event adds a
 * constant here and a rule row; it does not add a branch anywhere.
 */
public final class PostingEvent {

    private PostingEvent() { }

    // --- Purchase to pay -----------------------------------------------------------------------
    public static final String GRN = "GRN";
    public static final String SUPPLIER_BILL = "SUPPLIER_BILL";
    public static final String SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT";

    // --- Production ----------------------------------------------------------------------------
    public static final String YARN_ISSUE_TO_WEAVING = "YARN_ISSUE_TO_WEAVING";
    public static final String GREIGE_RECEIVED_FROM_WEAVING = "GREIGE_RECEIVED_FROM_WEAVING";
    public static final String GREIGE_ISSUE_TO_BATCH = "GREIGE_ISSUE_TO_BATCH";
    public static final String CHEMICAL_ISSUE_TO_BATCH = "CHEMICAL_ISSUE_TO_BATCH";
    public static final String UTILITY_CONSUMED = "UTILITY_CONSUMED";
    public static final String FINISHED_ROLL_RECEIVED = "FINISHED_ROLL_RECEIVED";
    /** Re-dye or abnormal loss beyond tolerance. Never absorbed silently. */
    public static final String ABNORMAL_LOSS = "ABNORMAL_LOSS";

    // --- Order to cash -------------------------------------------------------------------------
    public static final String DELIVERY = "DELIVERY";
    public static final String INVOICE = "INVOICE";
    public static final String RECEIPT = "RECEIPT";

    // --- Assets ----------------------------------------------------------------------------------
    public static final String DEPRECIATION = "DEPRECIATION";

    /** A hand-entered journal. Has no rule by design, and may not touch a control account. */
    public static final String MANUAL_JOURNAL = "MANUAL_JOURNAL";

    /** Every rule-driven event with a one-line explanation, for the setup screen. */
    public static final Map<String, String> RULE_EVENTS;
    static {
        Map<String, String> events = new LinkedHashMap<>();
        events.put(GRN, "Goods received from a supplier (before the bill)");
        events.put(SUPPLIER_BILL, "Supplier bill matched to received goods");
        events.put(SUPPLIER_PAYMENT, "Payment made to a supplier");
        events.put(YARN_ISSUE_TO_WEAVING, "Yarn issued from store to weaving");
        events.put(GREIGE_RECEIVED_FROM_WEAVING, "Greige fabric received from weaving");
        events.put(GREIGE_ISSUE_TO_BATCH, "Greige fabric issued to a dyeing batch");
        events.put(CHEMICAL_ISSUE_TO_BATCH, "Dyes and chemicals issued to a batch");
        events.put(UTILITY_CONSUMED, "Gas, power and water charged to production");
        events.put(FINISHED_ROLL_RECEIVED, "Finished rolls received into store");
        events.put(ABNORMAL_LOSS, "Re-dye or abnormal loss written off");
        events.put(DELIVERY, "Fabric delivered to a customer (cost of sales)");
        events.put(INVOICE, "Customer invoiced (amounts: net, vat)");
        events.put(RECEIPT, "Payment received from a customer");
        events.put(DEPRECIATION, "Monthly depreciation of fixed assets");
        RULE_EVENTS = Collections.unmodifiableMap(events);   // keeps the flow order above
    }

    public static boolean isKnown(String eventType) {
        return RULE_EVENTS.containsKey(eventType);
    }
}

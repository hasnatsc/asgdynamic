package com.asg.fabricerp.party;

/**
 * What a party <em>is to us</em>. One company may hold several at once. Ported from asfl-erp's
 * {@code party.api.PartyRoleType}.
 *
 * <p>The set is deliberately small and closed: every value changes which documents may name the
 * party - that is the test for membership. "Buying house" and "brand" earn their place because a
 * booking distinguishes the buying house placing the order from the brand the fabric carries;
 * "important customer" would not, because nothing behaves differently for one.
 */
public enum PartyRoleType {

    /** Buys from us. Qualified {@code MARKETING} or {@code COMMERCIAL}. */
    CUSTOMER,

    /** Sells to us: yarn, dyes, chemicals, spares, services. */
    SUPPLIER,

    /** Local indenting agent standing between the mill and an importer. */
    AGENT,

    /** A bank. Referenced by {@link PartyBankAccount} and by every LC document. */
    BANK,

    /** Own staff - a party is the identity of anyone a document can name. */
    EMPLOYEE,

    /** The label the fabric ultimately carries. Named on bookings, not transacted with. */
    BRAND,

    /** Places orders on a brand's behalf. */
    BUYING_HOUSE,

    /** The factory the fabric is delivered to; often distinct from the buyer. */
    GARMENT_FACTORY;

    /** The two sides of the mill that deal with the same customers under different terms. */
    public static final String MARKETING = "MARKETING";
    public static final String COMMERCIAL = "COMMERCIAL";

    /**
     * Only {@code CUSTOMER} is qualified: marketing and commercial deal with the same companies
     * under different terms and teams, which is the fact the legacy system encoded as two
     * separate customer tables. As a qualifier on one role, the company stays one record.
     */
    public boolean isQualified() {
        return this == CUSTOMER;
    }
}

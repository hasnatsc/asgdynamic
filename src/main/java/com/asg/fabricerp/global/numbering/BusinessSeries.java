package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.party.PartyRoleType;

import java.util.Optional;

/**
 * The numbered things that are not a {@link com.asg.fabricerp.global.documents.DocumentType}:
 * master data, and the modules not built yet (vouchers, QC) so their numbering is configured and
 * waiting rather than invented per module.
 *
 * <p>The master prefixes are the ones their services used before numbering was centralized (PT,
 * ITM, IB, ...), so only the layout of those codes changed, not their recognizable head.
 * {@code maxLength} is the width of the column each code lands in.
 */
public enum BusinessSeries implements NumberSeries {

    PARTY("PT", "Party", 6, 40),
    CUSTOMER("CUS", "Customer code", 6, 40),
    SUPPLIER("SUP", "Supplier code", 6, 40),
    EMPLOYEE("EMP", "Employee code", 6, 40),

    /** Products and every other stocked item. */
    ITEM("ITM", "Item / product", 6, 50),
    ITEM_BRAND("IB", "Item brand", 4, 30),
    ITEM_MODEL("MD", "Item model", 4, 30),
    YARN_TYPE("YT", "Yarn type", 4, 30),
    YARN_COUNT("YC", "Yarn count", 4, 20),
    YARN_PLY("YP", "Yarn ply", 4, 20),
    YARN_BLEND("BL", "Yarn blend", 4, 30),

    QC_INSPECTION("QC", "QC inspection", 6, 60),
    VOUCHER("VCH", "Accounting voucher (general)", 6, 60),

    // Voucher types of the general ledger: every entry is numbered in the series of its kind, so
    // a payment reads PV-..., a receipt RV-..., and each can be numbered and restarted separately.
    JOURNAL_VOUCHER("JV", "Journal voucher", 6, 60),
    PAYMENT_VOUCHER("PV", "Payment voucher", 6, 60),
    RECEIPT_VOUCHER("RV", "Receipt voucher", 6, 60),
    CONTRA_VOUCHER("CV", "Contra voucher (bank / cash transfer)", 6, 60),
    SALES_VOUCHER("SV", "Sales voucher", 6, 60),
    PURCHASE_VOUCHER("PUV", "Purchase voucher", 6, 60),
    PRODUCTION_VOUCHER("PDV", "Production / inventory voucher", 6, 60);

    private final String prefix;
    private final String label;
    private final int width;
    private final int maxLength;

    BusinessSeries(String prefix, String label, int width, int maxLength) {
        this.prefix = prefix;
        this.label = label;
        this.width = width;
        this.maxLength = maxLength;
    }

    @Override public String seriesCode()    { return name(); }
    @Override public String label()         { return label; }
    @Override public String defaultPrefix() { return prefix; }
    @Override public int defaultWidth()     { return width; }
    @Override public int maxLength()        { return maxLength; }

    /**
     * The series a party role's code is drawn from. Only the capacities a code is printed under
     * are numbered; a bank or a brand is identified by its party code alone.
     */
    public static Optional<BusinessSeries> forRole(PartyRoleType role) {
        return switch (role) {
            case CUSTOMER -> Optional.of(CUSTOMER);
            case SUPPLIER -> Optional.of(SUPPLIER);
            case EMPLOYEE -> Optional.of(EMPLOYEE);
            default -> Optional.empty();
        };
    }
}

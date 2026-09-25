package com.asg.fabricerp.costing;

import java.util.List;

/**
 * The costing system's numeric ids, as names.
 *
 * <p>The costing API sends weave type, weave style, fabric type and LC tenure as bare ids. The
 * legacy Booking form resolved them against its own option lists, whose codes carry the same
 * number - {@code WT_7}, {@code WS_5}, {@code B_8}, {@code T_4} - so each list below is that form's
 * list in code order (business-logic-capture/master_data_catalog.json, {@code so_dtlSet_*}).
 * Checked against real costings and the bookings raised from them: weaveType 7 was booked as
 * "Dobby : Medium Float", weaveStyle 10 as "Medium Float", weaveType 1 / weaveStyle 5 as
 * "1/1" / "Plain", fabric 8 as "Solid Dyed Print", LcType 4 as "90 Days".
 *
 * <p>The names are the ones the fabric setup lists use, so a translated value selects the
 * matching dropdown entry. An id outside a list translates to null - the user picks it by hand -
 * rather than to a guess.
 */
public final class CostingCatalog {

    private CostingCatalog() { }

    /** {@code so_dtlSet_weaveType}, WT_1..WT_18. */
    static final List<String> WEAVE_TYPES = List.of(
        "1/1", "2/2", "2/1", "3/1", "4/1",
        "Dobby : Long Float", "Dobby : Medium Float", "Dobby : Short Float",
        "5/1", "6/1", "7/1", "1/4", "1/5", "1/6", "1/7", "1/8", "3/2", "4/2");

    /** {@code so_dtlSet_weaveStyles}, WS_1..WS_14. */
    static final List<String> WEAVE_STYLES = List.of(
        "S Twill", "Z Twill", "HBT", "Zig Zag Twill", "Plain", "Oxford", "Matt", "Cavalry Twill",
        "Long Float", "Medium Float", "Short Float", "Satin", "Ribstop", "Broken Twill");

    /** {@code so_dtlSet_fabricsType}, B_1..B_14. */
    static final List<String> FABRIC_TYPES = List.of(
        "Yarn Dyed", "Yarn Dyed Print", "Yarn Dyed Spandex", "Yarn Dyed Print Spandex",
        "Yarn Dyed LUNGI (Greige)", "Solid Dyed", "Solid Dyed Spandex", "Solid Dyed Print",
        "Solid Dyed Print Spandex", "Greige Yarn Dyed", "Greige Yarn Dyed Spandex",
        "Greige Solid Dyed", "Greige Solid Dyed Spandex", "Greige Solid Dyed (LUNGI)");

    /** {@code so_dtlSet_lcTenure}, T_1..T_6. */
    public static final List<String> LC_TENURES = List.of(
        "At Sight", "30 Days", "60 Days", "90 Days", "120 Days", "150 Days");

    /** {@code so_dtlSet_lcPaymentType}. Not sent by costing; listed here beside its sibling. */
    public static final List<String> LC_PAYMENT_TYPES = List.of(
        "At Sight", "Date of delivery", "Date of acceptance", "Negotiation");

    public static String weaveType(String id)  { return byId(WEAVE_TYPES, id); }
    public static String weaveStyle(String id) { return byId(WEAVE_STYLES, id); }
    public static String fabricType(String id) { return byId(FABRIC_TYPES, id); }
    public static String lcTenure(String id)   { return byId(LC_TENURES, id); }

    /** 1-based, as the upstream numbers them. */
    static String byId(List<String> names, String id) {
        if (id == null || id.isBlank()) return null;
        try {
            int n = Integer.parseInt(id.trim());
            return n >= 1 && n <= names.size() ? names.get(n - 1) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

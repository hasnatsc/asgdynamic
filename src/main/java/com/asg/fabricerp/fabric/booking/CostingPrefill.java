package com.asg.fabricerp.fabric.booking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * What entering a costing number on the Booking editor fills in.
 *
 * @param spec      the specification, keyed by {@code FabricSpec} property - the editor binds it
 *                  field for field
 * @param colours   the costing's colour plan, priced at the quoted price
 * @param warnings  what the user should know before relying on it: not finally approved,
 *                  already booked elsewhere
 */
public record CostingPrefill(
    String code,
    String preCostBuyer,
    String costingReference,
    BigDecimal costingOrderQty,
    BigDecimal breakEvenPriceMtr,
    String note,
    Map<String, Object> spec,
    List<Colour> colours,
    List<String> warnings
) {
    public record Colour(String colorName, BigDecimal quantity, BigDecimal rate) {}
}

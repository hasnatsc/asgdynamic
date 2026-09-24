package com.asg.fabricerp.inventory.item;

/**
 * The dimension a {@link UnitOfMeasure} measures. Conversion only makes sense within one
 * category, and each category has at most one base unit (enforced by
 * {@code ux_inv_uom_base_per_category}).
 */
public enum UomCategory {
    WEIGHT,   // KG, GM, TON
    COUNT,    // PCS, CONE, BOBBIN, BALE
    LENGTH,   // METER, YARD
    VOLUME,   // LITRE, ML
    AREA,     // SQM, SQFT
    PACKING,  // CARTON, BAG
    TIME      // HOUR, MINUTE
}

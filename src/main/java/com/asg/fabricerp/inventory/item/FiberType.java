package com.asg.fabricerp.inventory.item;

/** The fiber a {@link ItemType#FIBER} item is made of. */
public enum FiberType {
    // Natural
    COTTON, LINEN, WOOL, SILK,
    // Regenerated
    VISCOSE, MODAL, LYOCELL, BAMBOO,
    // Synthetic
    POLYESTER, NYLON, ACRYLIC, POLYPROPYLENE,
    // Specialty
    ELASTANE, ARAMID;

    /** "Cotton", "Polypropylene" - as a composition reads. */
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}

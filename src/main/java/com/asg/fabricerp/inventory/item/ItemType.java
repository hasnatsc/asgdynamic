package com.asg.fabricerp.inventory.item;

/**
 * What kind of thing an {@link InventoryItem} is. Ported from SpindleERP's
 * {@code inventory.setup.ItemType}; the type decides which of the item's type-specific
 * sections apply (fiber, yarn, chemicals, fixed asset) and filters the category picker.
 */
public enum ItemType {

    FIBER("Fiber"),
    YARN("Yarn"),
    FABRICS("Fabrics"),
    WORK_IN_PROGRESS("Work In Progress"),

    CHEMICALS("Dyes & Chemicals"),
    PACKAGING("Packaging Materials"),
    MRO("Maintenance, Repair & Operations"),

    WASTE("Waste"),
    SCRAP("Scrap"),
    BY_PRODUCT("By Product"),

    GENERAL("General Items"),
    SERVICE("Service"),
    FIXED_ASSET("Non-Current Fixed Assets");

    private final String label;

    ItemType(String label) { this.label = label; }

    public String label() { return label; }

    public boolean isRawMaterial()   { return this == FIBER || this == CHEMICALS; }
    public boolean isFinishedGoods() { return this == YARN || this == FABRICS; }
    public boolean isWasteRelated()  { return this == WASTE || this == SCRAP || this == BY_PRODUCT; }
    public boolean isStockItem()     { return this != SERVICE; }
}

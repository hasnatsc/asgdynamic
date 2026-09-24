package com.asg.fabricerp.inventory.item;

import java.util.List;

/**
 * The item-master reference screens, all governed by the one {@code ITEM_SETUP} screen grant.
 * {@code Navigation} builds the sidebar group from this list, so a new setup page appears in the
 * menu by being added here.
 */
public final class ItemSetupMenu {

    public record Page(String label, String path) { }

    public static final List<Page> PAGES = List.of(
        new Page("Categories", "/inventory/categories"),
        new Page("Units of measure", "/inventory/uoms"),
        new Page("HS codes", "/inventory/hs-codes"),
        new Page("Brands", "/inventory/brands"),
        new Page("Models", "/inventory/models"),
        new Page("Yarn types", "/inventory/yarn-types"),
        new Page("Yarn counts", "/inventory/yarn-counts"),
        new Page("Yarn plies", "/inventory/yarn-plies"),
        new Page("Yarn blends", "/inventory/yarn-blends"));

    private ItemSetupMenu() { }
}

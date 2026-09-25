package com.asg.fabricerp.web;

import com.asg.fabricerp.fabric.setup.AttributeType;
import com.asg.fabricerp.inventory.item.ItemSetupMenu;
import com.asg.fabricerp.security.Screen;
import com.asg.fabricerp.security.Verb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The sidebar, derived from the signed-in user's authorities: a screen appears exactly when its
 * {@code VIEW} authority is held. There is no separate menu table to fall out of step with the
 * {@code @PreAuthorize} checks — the legacy system's menu kept 14 entries for controllers that
 * no longer existed, and linked screens users could not open.
 *
 * <p>Hiding a link is presentation, not protection: every route still carries its own
 * {@code @PreAuthorize}.
 *
 * <p>Icons name symbols in {@code static/images/icons.svg}.
 */
public final class Navigation {

    public record Item(String label, String path, String icon, boolean active, List<Item> children) {

        static Item leaf(String label, String path, String icon, String currentPath) {
            return new Item(label, path, icon, matches(path, currentPath), List.of());
        }

        static Item group(String label, String icon, List<Item> children) {
            boolean anyActive = children.stream().anyMatch(Item::active);
            return new Item(label, children.getFirst().path(), icon, anyActive, children);
        }

        public boolean hasChildren() { return !children.isEmpty(); }
    }

    public record Section(String key, String label, String icon, List<Item> items) {

        public boolean active() { return items.stream().anyMatch(Item::active); }
    }

    private Navigation() { }

    public static List<Section> build(Set<String> authorities, String currentPath) {
        List<Section> sections = new ArrayList<>();
        for (Screen.Section section : Screen.Section.values()) {
            List<Item> items = Arrays.stream(Screen.values())
                .filter(screen -> screen.section() == section)
                .filter(screen -> authorities.contains(screen.authority(Verb.VIEW)))
                .map(screen -> itemFor(screen, currentPath))
                .toList();
            if (!items.isEmpty()) {
                sections.add(new Section(section.name().toLowerCase(), section.label(), iconFor(section), items));
            }
        }
        return sections;
    }

    private static Item itemFor(Screen screen, String currentPath) {
        String icon = iconFor(screen);
        return switch (screen) {
            case FABRIC_SETUP -> Item.group(screen.label(), icon, Arrays.stream(AttributeType.values())
                .map(type -> Item.leaf(type.label(), "/setup/fabric/" + type.slug(), null, currentPath))
                .toList());
            case ITEM_SETUP -> Item.group(screen.label(), icon, ItemSetupMenu.PAGES.stream()
                .map(page -> Item.leaf(page.label(), page.path(), null, currentPath))
                .toList());
            case SECURITY_ADMIN -> Item.group("Security", icon, List.of(
                Item.leaf("Overview", "/setup/security", null, currentPath),
                Item.leaf("Users", "/setup/users", null, currentPath),
                Item.leaf("Roles", "/setup/roles", null, currentPath),
                Item.leaf("Access log", "/setup/access-log", null, currentPath)));
            default -> Item.leaf(screen.label(), screen.path(), icon, currentPath);
        };
    }

    /** The sprite symbol drawn beside a screen in the sidebar, the dashboard and the command palette. */
    public static String iconFor(Screen screen) {
        return switch (screen) {
            case BOOKING        -> "booking";
            case RPI            -> "document";
            case DO             -> "clipboard-check";
            case FD             -> "truck";
            case BPO            -> "planning";
            case WWO            -> "loom";
            case PWO            -> "dyeing";
            case GR             -> "receive";
            case ITEM           -> "package";
            case ITEM_SETUP     -> "tag";
            case PARTY          -> "users";
            case FABRIC_SETUP   -> "swatch";
            case TERMS          -> "document";
            case QUALITY        -> "spool";
            case ACC_CHART      -> "ledger";
            case ACC_JOURNAL    -> "journal";
            case ACC_REPORTS    -> "chart-bar";
            case ACC_CREDIT     -> "credit-card";
            case ACC_SETUP      -> "calendar";
            case NUMBERING      -> "hash";
            case SECURITY_ADMIN -> "shield";
        };
    }

    static String iconFor(Screen.Section section) {
        return switch (section) {
            case SETUP          -> "master-data";
            case SALES          -> "sales";
            case INVENTORY      -> "inventory";
            case PRODUCTION     -> "production";
            case ACCOUNTS       -> "ledger";
            case ADMINISTRATION -> "admin";
        };
    }

    /** Labels from section down to the active screen, for the header breadcrumb; empty on the dashboard. */
    public static List<String> trail(List<Section> sections) {
        for (Section section : sections) {
            for (Item item : section.items()) {
                if (!item.active()) continue;
                List<String> trail = new ArrayList<>(List.of(section.label(), item.label()));
                item.children().stream().filter(Item::active).findFirst().ifPresent(child -> trail.add(child.label()));
                return trail;
            }
        }
        return List.of();
    }

    static boolean matches(String path, String currentPath) {
        return currentPath != null && (currentPath.equals(path) || currentPath.startsWith(path + "/"));
    }
}

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
 */
public final class Navigation {

    public record Item(String label, String path, boolean active, List<Item> children) {

        static Item leaf(String label, String path, String currentPath) {
            return new Item(label, path, matches(path, currentPath), List.of());
        }

        static Item group(String label, List<Item> children) {
            boolean anyActive = children.stream().anyMatch(Item::active);
            return new Item(label, children.getFirst().path(), anyActive, children);
        }

        public boolean hasChildren() { return !children.isEmpty(); }
    }

    public record Section(String label, List<Item> items) { }

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
                sections.add(new Section(section.label(), items));
            }
        }
        return sections;
    }

    private static Item itemFor(Screen screen, String currentPath) {
        return switch (screen) {
            case FABRIC_SETUP -> Item.group(screen.label(), Arrays.stream(AttributeType.values())
                .map(type -> Item.leaf(type.label(), "/setup/fabric/" + type.slug(), currentPath))
                .toList());
            case ITEM_SETUP -> Item.group(screen.label(), ItemSetupMenu.PAGES.stream()
                .map(page -> Item.leaf(page.label(), page.path(), currentPath))
                .toList());
            case SECURITY_ADMIN -> Item.group("Security", List.of(
                Item.leaf("Overview", "/setup/security", currentPath),
                Item.leaf("Users", "/setup/users", currentPath),
                Item.leaf("Roles", "/setup/roles", currentPath),
                Item.leaf("Access log", "/setup/access-log", currentPath)));
            default -> Item.leaf(screen.label(), screen.path(), currentPath);
        };
    }

    static boolean matches(String path, String currentPath) {
        return currentPath != null && (currentPath.equals(path) || currentPath.startsWith(path + "/"));
    }
}

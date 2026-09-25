package com.asg.fabricerp.web;

import com.asg.fabricerp.security.Screen;
import com.asg.fabricerp.security.Verb;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The mill's order-to-delivery chain as the dashboard draws it: planning, material requirement,
 * material issue, production, QC, packing, finished goods, delivery.
 *
 * <p>Each stage lists the screens that carry it, filtered like {@link Navigation} to those the user
 * holds {@code VIEW} on. A stage no screen implements yet is drawn as planned, never as a link — the
 * same no-dead-links rule the sidebar keeps.
 */
public final class ManufacturingFlow {

    public record Link(String label, String path) { }

    /**
     * @param built whether any screen implements this stage yet
     * @param links the implementing screens this user may open (empty when built but not granted)
     */
    public record Stage(int number, String title, String icon, String caption, boolean built, List<Link> links) { }

    private record Definition(String title, String icon, String caption, List<Screen> screens) { }

    private static final List<Definition> STAGES = List.of(
        new Definition("Production planning", "planning", "Bulk production orders from confirmed bookings",
            List.of(Screen.BPO)),
        new Definition("Material requirement", "calculator", "Yarn and chemical needs per order", List.of()),
        new Definition("Material issue", "transfer", "Store issue to the floor", List.of()),
        new Definition("Production", "production", "Weaving, greige receipt and processing",
            List.of(Screen.WWO, Screen.GR, Screen.PWO)),
        new Definition("Quality control", "qc", "Inspection and grading", List.of()),
        new Definition("Packing", "packing", "Rolls, packing lists and labels", List.of()),
        new Definition("Finished goods", "inventory", "Finished fabric into store", List.of()),
        new Definition("Delivery", "truck", "Delivery orders and dispatch",
            List.of(Screen.DO, Screen.FD)));

    private ManufacturingFlow() { }

    public static List<Stage> build(Set<String> authorities) {
        return IntStream.range(0, STAGES.size()).mapToObj(i -> {
            Definition stage = STAGES.get(i);
            List<Link> links = stage.screens().stream()
                .filter(screen -> authorities.contains(screen.authority(Verb.VIEW)))
                .map(screen -> new Link(screen.label(), screen.path()))
                .toList();
            return new Stage(i + 1, stage.title(), stage.icon(), stage.caption(), !stage.screens().isEmpty(), links);
        }).toList();
    }
}

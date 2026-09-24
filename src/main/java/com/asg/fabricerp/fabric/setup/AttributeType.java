package com.asg.fabricerp.fabric.setup;

/**
 * The fabric reference lists.
 *
 * <h2>Why one enum and one table instead of seven modules</h2>
 * asgdynamic ships a controller, screen and table per list — Weave Type, Weave Style,
 * Fabric Type, Finish Type, Selvedge, Light Source, Composition — and SpindleERP's
 * convention would add five files each (Controller, Service, ServiceImpl, DTO,
 * DataTableService). That is ~35 files to maintain seven lists of {code, name, active}.
 *
 * <p>These lists have no distinguishing columns and no behaviour of their own, so they
 * share one table and one set of files. The discriminator is this enum.
 *
 * <p><b>The rule for graduating:</b> the moment a list needs a column the others do not
 * have, or logic of its own, it becomes its own entity. {@code FabricComposition} is the
 * likely first candidate — if percentage components are ever modelled properly it stops
 * being a name and becomes a structure. Do not bend this table to accommodate that.
 */
public enum AttributeType {

    WEAVE_TYPE("Weave Type"),               // 1/1, 2/1 S Twill, 2/2 Matt ...
    WEAVE_STYLE("Weave Style"),             // Broken Twill, Cavalry Twill, HBT ...
    FABRIC_TYPE("Fabric Type"),             // Greige Solid Dyed, Greige Indigo Denim ...
    FINISH_TYPE("Finish Type"),             // Aero Finish, Both Side Peach, Brush ...
    SELVEDGE("Selvedge"),                   // 10 mm + 10 mm ...
    LIGHT_SOURCE("Light Source"),           // CWF, D65, TL83, Filament
    COMPOSITION("Fabric Composition");      // 100% Cotton, 70% Viscose 30% Linen ...

    private final String label;

    AttributeType(String label) { this.label = label; }

    public String label() { return label; }

    /** URL slug, e.g. WEAVE_TYPE -> "weave-type". */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }

    public static AttributeType fromSlug(String slug) {
        for (AttributeType t : values()) {
            if (t.slug().equalsIgnoreCase(slug)) return t;
        }
        throw new IllegalArgumentException("Unknown fabric attribute: " + slug);
    }
}

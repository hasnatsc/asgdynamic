package com.asg.fabricerp.security;

/**
 * Every screen a role grant can name — the asgdynamic equivalent of asfl-erp's admin module's
 * {@code screenCode}, code-defined rather than DB-driven (there is no menu table here; see
 * {@code Role}'s javadoc). One value per document-type controller, plus the setup, item-master
 * and admin screens that aren't a {@link com.asg.fabricerp.global.documents.DocumentType}.
 *
 * <p>Also carries what the navigation and the role editor show for it: a label, the menu
 * section it sits in, and its landing path. The sidebar lists exactly the screens the user holds
 * {@code VIEW} on, so menu and authorization cannot drift apart the way the legacy system's
 * URL table and menu did.
 */
public enum Screen {
    BOOKING("Booking", Section.SALES, "/booking"),
    RPI("Request for PI", Section.SALES, "/requestforpi"),
    DO("Delivery order", Section.SALES, "/delivery-order"),
    FD("Fabrics delivery", Section.SALES, "/fabrics-delivery"),
    BPO("Bulk production order", Section.PRODUCTION, "/bpo"),
    WWO("Weaving work order", Section.PRODUCTION, "/weaving-wo"),
    PWO("Processing work order", Section.PRODUCTION, "/processing-wo"),
    GR("Greige receive", Section.PRODUCTION, "/greige-receive"),
    ITEM("Items", Section.INVENTORY, "/inventory/items"),
    ITEM_SETUP("Item setup", Section.INVENTORY, "/inventory/categories"),
    PARTY("Parties", Section.SETUP, "/setup/parties"),
    FABRIC_SETUP("Fabric setup", Section.SETUP, "/setup/fabric/weave-type"),
    NUMBERING("Document numbering", Section.ADMINISTRATION, "/setup/numbering"),
    SECURITY_ADMIN("Security administration", Section.ADMINISTRATION, "/setup/security");

    public enum Section {
        SALES("Sales"),
        PRODUCTION("Production"),
        INVENTORY("Inventory"),
        SETUP("Setup"),
        ADMINISTRATION("Administration");

        private final String label;

        Section(String label) { this.label = label; }

        public String label() { return label; }
    }

    private final String label;
    private final Section section;
    private final String path;

    Screen(String label, Section section, String path) {
        this.label = label;
        this.section = section;
        this.path = path;
    }

    public String label()     { return label; }
    public Section section()  { return section; }
    public String path()      { return path; }

    /** The authority {@link FabricUserPrincipal} derives for one verb here, e.g. {@code SCREEN_BPO_VIEW}. */
    public String authority(Verb verb) {
        return "SCREEN_" + name() + "_" + verb.name();
    }
}

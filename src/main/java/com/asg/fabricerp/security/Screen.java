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
    TERMS("Terms & conditions", Section.SETUP, "/setup/terms"),
    QUALITY("Fabric qualities", Section.SETUP, "/setup/qualities"),
    ACC_CHART("Chart of accounts", Section.ACCOUNTS, "/accounts/chart"),
    ACC_JOURNAL("Journal entries", Section.ACCOUNTS, "/accounts/journals"),
    ACC_REPORTS("Financial reports", Section.ACCOUNTS, "/accounts/reports"),
    ACC_CREDIT("Credit control", Section.ACCOUNTS, "/accounts/credit"),
    ACC_SETUP("Accounting setup", Section.ACCOUNTS, "/accounts/setup"),
    NUMBERING("Document numbering", Section.ADMINISTRATION, "/setup/numbering"),
    SECURITY_ADMIN("Security administration", Section.ADMINISTRATION, "/setup/security"),
    MARKETING_TEAM("Marketing teams", Section.SETUP, "/setup/marketing-teams"),
    /** What is waiting for the signed-in user's signature, and every request in the unit. */
    APPROVALS("Approvals", Section.WORKFLOW, "/approvals"),
    APPROVAL_SETUP("Approval matrices", Section.ADMINISTRATION, "/setup/approval-matrices"),
    /**
     * Booking analytics & reports. VIEW opens it; what it shows is decided by who is looking - a
     * team member's own bookings, a supervisor's or approver's teams, management's whole unit.
     */
    BOOKING_ANALYTICS("Booking analytics", Section.ANALYTICS, "/analytics/booking");

    /** Declaration order is menu order: reference data first, then the order-to-delivery modules. */
    public enum Section {
        /** First: an approver opens the app to what is waiting for them. */
        WORKFLOW("Workflow"),
        /** Analytics & reports for every module, starting with Booking. */
        ANALYTICS("Analytics & reports"),
        SETUP("Master data"),
        SALES("Sales"),
        INVENTORY("Inventory"),
        PRODUCTION("Production"),
        ACCOUNTS("Accounts"),
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

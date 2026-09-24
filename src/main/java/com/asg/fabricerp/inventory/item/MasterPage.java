package com.asg.fabricerp.inventory.item;

import java.util.Arrays;
import java.util.List;

/**
 * Describes one simple reference-list screen - its grid columns and editor fields - for the
 * shared {@code inventory/master} template. SpindleERP ships a hand-written index template per
 * list; seven lists that differ only in their columns share one template here, the same way
 * the seven fabric reference lists share {@code setup/fabric-attribute}.
 *
 * <p>Serialized into the page as JSON; the template's script renders from it.
 */
public record MasterPage(String title, String subtitle, String api, String noun, boolean approvable,
                         List<Column> columns, List<Field> fields) {

    /** kind: text | mono | active | approval | number | yesno */
    public record Column(String key, String label, String sort, String kind) {
        static Column of(String key, String label)             { return new Column(key, label, key, "text"); }
        static Column mono(String key, String label)           { return new Column(key, label, key, "mono"); }
        static Column plain(String key, String label)          { return new Column(key, label, null, "text"); }
        static Column number(String key, String label)         { return new Column(key, label, key, "number"); }
        static Column yesNo(String key, String label)          { return new Column(key, label, null, "yesno"); }
        static Column active()                                 { return new Column("active", "Status", "active", "active"); }
        static Column approval()                               { return new Column("approved", "Approval", null, "approval"); }
    }

    /** type: text | textarea | number | checkbox | select | lookup */
    public record Field(String name, String label, String type, boolean required, Integer maxlength,
                        String step, String lookup, List<Choice> options, String hint, boolean wide) {

        static Field text(String name, String label, int max, boolean required) {
            return new Field(name, label, "text", required, max, null, null, null, null, false);
        }
        static Field area(String name, String label) {
            return new Field(name, label, "textarea", false, null, null, null, null, null, true);
        }
        static Field number(String name, String label, String step, boolean required) {
            return new Field(name, label, "number", required, null, step, null, null, null, false);
        }
        static Field check(String name, String label) {
            return new Field(name, label, "checkbox", false, null, null, null, null, null, false);
        }
        static Field select(String name, String label, List<Choice> options, boolean required) {
            return new Field(name, label, "select", required, null, null, null, options, null, false);
        }
        static Field lookup(String name, String label, String url, boolean required) {
            return new Field(name, label, "lookup", required, null, null, url, null, null, false);
        }
        Field hint(String text) {
            return new Field(name, label, type, required, maxlength, step, lookup, options, text, wide);
        }
    }

    public record Choice(String value, String label) {
        static <E extends Enum<E>> List<Choice> of(Class<E> type) {
            return Arrays.stream(type.getEnumConstants())
                .map(e -> new Choice(e.name(), e.name().charAt(0) + e.name().substring(1).toLowerCase().replace('_', ' ')))
                .toList();
        }
    }
}

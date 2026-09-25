package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.global.documents.DocumentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Anything {@link BusinessNumberService} numbers: every {@link DocumentType}, plus the masters and
 * not-yet-built modules in {@link BusinessSeries}. A future module either adds a constant there or
 * implements this on its own enum - {@code seriesCode} is the only key the tables ever see, so a
 * new series needs no migration: its {@code gbl_numbering_schemes} row is created from these
 * defaults the first time it is numbered, and can be reconfigured from then on.
 */
public interface NumberSeries {

    /** Stable key in {@code gbl_numbering_schemes}, counters and the issued-number ledger. Never rename one. */
    String seriesCode();

    /** Human label for the setup screen. */
    String label();

    /** Prefix a new organization starts with. Unique across all series; see {@link #known()}. */
    String defaultPrefix();

    /** Digits in {@code {SEQ}} before it grows. */
    default int defaultWidth() { return 6; }

    /** The widest number the owning column accepts - checked before a number is issued. */
    default int maxLength() { return 60; }

    /** Every series the application knows about, documents first. */
    static List<NumberSeries> known() {
        List<NumberSeries> all = new ArrayList<>();
        Collections.addAll(all, DocumentType.values());
        Collections.addAll(all, BusinessSeries.values());
        return all;
    }

    static Optional<NumberSeries> of(String seriesCode) {
        return known().stream().filter(s -> s.seriesCode().equals(seriesCode)).findFirst();
    }
}

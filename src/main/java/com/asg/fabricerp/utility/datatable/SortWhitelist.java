package com.asg.fabricerp.utility.datatable;

import java.util.Map;
import java.util.Optional;

/**
 * Maps a client-supplied column name to a safe entity property.
 *
 * <p>Sort keys become SQL identifiers, so they cannot be taken from the request as-is.
 * SpindleERP's {@code BaseDataTableService} builds its ORDER BY and WHERE by string
 * concatenation over raw JdbcTemplate; anything not on a list like this one reaches the
 * planner unchecked. An unknown column here simply falls back to the default.
 */
public final class SortWhitelist {

    private final Map<String, String> columns;

    private SortWhitelist(Map<String, String> columns) {
        this.columns = columns;
    }

    /** @param columns client column name -> entity property path */
    public static SortWhitelist of(Map<String, String> columns) {
        return new SortWhitelist(Map.copyOf(columns));
    }

    public Optional<String> resolve(String clientColumn) {
        if (clientColumn == null || clientColumn.isBlank()) return Optional.empty();
        return Optional.ofNullable(columns.get(clientColumn));
    }
}

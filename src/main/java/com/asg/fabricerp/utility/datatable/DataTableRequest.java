package com.asg.fabricerp.utility.datatable;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * DataTables query parameters, normalised.
 *
 * <p>asgdynamic served the grid JSON from the same {@code /{controller}/index} URL as the
 * HTML page, switching on a {@code conditionParams} value. That overloading is not carried
 * over: pages live at {@code /{entity}}, rows at {@code /api/{entity}}.
 */
public record DataTableRequest(
    int draw,
    int start,
    int length,
    String searchValue,
    String sortColumn,
    String sortDir
) {

    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 25;

    public DataTableRequest {
        if (length <= 0) length = DEFAULT_PAGE_SIZE;
        if (length > MAX_PAGE_SIZE) length = MAX_PAGE_SIZE;
        if (start < 0) start = 0;
    }

    public boolean hasSearch() {
        return searchValue != null && !searchValue.isBlank();
    }

    /** Null when absent, so a repository can treat it as "no filter". */
    public String searchOrNull() {
        return hasSearch() ? searchValue.trim() : null;
    }

    /**
     * @param allowed whitelist of sortable property names. A column not on it is ignored
     *                rather than passed through — sort keys reach the query planner as
     *                identifiers and must never come straight from the client.
     */
    public Pageable toPageable(SortWhitelist allowed, String defaultSort) {
        String property = allowed.resolve(sortColumn).orElse(defaultSort);
        Sort.Direction direction =
            "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(start / length, length, Sort.by(direction, property));
    }
}

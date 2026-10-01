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
        return searchOrNull(searchValue);
    }

    /** The same, for an endpoint that filters as a grid does without paging (a list's status totals). */
    public static String searchOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    /**
     * A document list's paging: newest code first. Codes are issued serially by
     * {@link com.asg.fabricerp.global.numbering.BusinessNumberService}, so code order is the order
     * documents were raised in - a back-dated document still sits where it was keyed in, which a
     * date sort cannot promise. A column the user sorts by comes first, with the code (newest first)
     * breaking its ties.
     *
     * @param codeProperty the entity property holding the code, e.g. {@code documentNo}
     */
    public Pageable toPageableCodeDesc(SortWhitelist allowed, String codeProperty) {
        Sort newestCode = Sort.by(Sort.Direction.DESC, codeProperty);
        String property = allowed.resolve(sortColumn).orElse(null);
        Sort sort;
        if (property == null) {
            sort = newestCode;
        } else if (property.equals(codeProperty)) {
            sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(Sort.Direction.ASC, codeProperty) : newestCode;
        } else {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, property).and(newestCode);
        }
        return PageRequest.of(start / length, length, sort);
    }
}

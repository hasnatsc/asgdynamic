package com.asg.fabricerp.utility.datatable;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Document lists open newest code first, and keep code order inside any other sort. */
class DataTableRequestTest {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "documentNo", "documentNo", "documentDate", "documentDate", "status", "status"));

    private static Pageable page(String column, String dir) {
        return new DataTableRequest(1, 50, 25, null, column, dir).toPageableCodeDesc(SORTABLE, "documentNo");
    }

    @Test
    void noSortAsked_isNewestCodeFirst() {
        Pageable p = page(null, null);
        assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "documentNo"));
        assertThat(p.getPageNumber()).isEqualTo(2);
    }

    @Test
    void anUnknownColumn_fallsBackToNewestCodeFirst() {
        assertThat(page("document_no; drop table x", "asc").getSort())
            .isEqualTo(Sort.by(Sort.Direction.DESC, "documentNo"));
    }

    @Test
    void theCodeColumn_canStillBeTurnedAscending() {
        assertThat(page("documentNo", "asc").getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "documentNo"));
        assertThat(page("documentNo", "desc").getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "documentNo"));
    }

    @Test
    void anotherColumn_isBrokenByNewestCode() {
        assertThat(page("documentDate", "asc").getSort()).isEqualTo(
            Sort.by(Sort.Direction.ASC, "documentDate").and(Sort.by(Sort.Direction.DESC, "documentNo")));
        assertThat(page("status", "desc").getSort()).isEqualTo(
            Sort.by(Sort.Direction.DESC, "status").and(Sort.by(Sort.Direction.DESC, "documentNo")));
    }
}

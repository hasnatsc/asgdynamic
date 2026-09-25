package com.asg.fabricerp.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The picker paging contract every searchable lookup shares. */
class LookupPageTest {

    @Test
    void pagesAreOneBasedAndSizesAreCapped() {
        assertThat(LookupPage.pageable(null, null)).isEqualTo(PageRequest.of(0, LookupPage.DEFAULT_SIZE));
        assertThat(LookupPage.pageable(3, 10)).isEqualTo(PageRequest.of(2, 10));
        assertThat(LookupPage.pageable(0, 5000)).isEqualTo(PageRequest.of(0, LookupPage.MAX_SIZE));
    }

    @Test
    void searchTextBecomesAnEscapedCaseInsensitivePattern() {
        assertThat(LookupPage.like(null)).isEqualTo("%");
        assertThat(LookupPage.like("  ")).isEqualTo("%");
        assertThat(LookupPage.like(" Cotton ")).isEqualTo("%cotton%");
        assertThat(LookupPage.like("50%_a\\b")).isEqualTo("%50\\%\\_a\\\\b%");
    }

    @Test
    void aSliceSaysWhetherMoreFollow() {
        LookupPage<String> page = LookupPage.of(new SliceImpl<>(List.of(1, 2), PageRequest.of(0, 2), true), String::valueOf);
        assertThat(page.results()).containsExactly("1", "2");
        assertThat(page.pagination().more()).isTrue();
    }
}

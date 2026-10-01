package com.asg.fabricerp.search;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Elasticsearch when it is current; PostgreSQL before that, and whenever it fails. */
class SearchServiceTest {

    private final SearchSql sql = mock(SearchSql.class);
    private final ElasticsearchClient es = mock(ElasticsearchClient.class);
    private final SearchIndexer indexer = mock(SearchIndexer.class);
    private final OrgContext context = mock(OrgContext.class);
    private final SearchService service = new SearchService(sql, es, indexer, context);

    @BeforeEach
    void setUp() {
        when(context.requireOrganizationId()).thenReturn(1L);
        when(context.businessUnitId()).thenReturn(10L);
        when(context.requireRowScope()).thenReturn(RowScope.unrestrictedScope());
        when(sql.search(any(), any(), any(), anyInt(), anyInt())).thenReturn(SearchResult.empty("database"));
        when(es.search(any(), any(), any(), anyInt(), anyInt())).thenReturn(SearchResult.empty("elasticsearch"));
    }

    @Test
    void anIndexNotYetServing_isNotAsked() {
        assertThat(service.search("bpo", null, 0, 20).engine()).isEqualTo("database");
        verify(es, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void aServingIndex_answers() {
        when(indexer.serving()).thenReturn(true);
        assertThat(service.search("bpo", null, 0, 20).engine()).isEqualTo("elasticsearch");
    }

    @Test
    void aFailingIndex_fallsBackToPostgres() {
        when(indexer.serving()).thenReturn(true);
        when(es.search(any(), any(), any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("connection refused"));
        assertThat(service.search("bpo", null, 0, 20).engine()).isEqualTo("database");
    }

    @Test
    void pageSize_isCapped() {
        service.search("bpo", null, 2, 10_000);
        verify(sql).search(eq("bpo"), eq(null), any(), eq(2 * SearchService.MAX_PAGE_SIZE), eq(SearchService.MAX_PAGE_SIZE));
    }
}

package com.asg.fabricerp.search;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.security.AuthorityChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central search: one box that finds any document, voucher, party or item by its code, its buyer or
 * supplier, its references or its narration - and only those the signed-in user's own screens would
 * list ({@link SearchScope}).
 *
 * <p>Elasticsearch answers when it is configured and its index is current ({@link SearchIndexer});
 * otherwise, or if it fails mid-request, PostgreSQL answers the same question from the same
 * projection. The result says which, so the page can tell the user.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    public static final int MAX_PAGE_SIZE = 50;

    private final SearchSql sql;
    private final ElasticsearchClient es;
    private final SearchIndexer indexer;
    private final OrgContext context;

    public SearchService(SearchSql sql, ElasticsearchClient es, SearchIndexer indexer, OrgContext context) {
        this.sql = sql;
        this.es = es;
        this.indexer = indexer;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public SearchResult search(String q, SearchKind kind, int page, int size) {
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int offset = Math.max(page, 0) * limit;
        SearchScope scope = SearchScope.of(context, AuthorityChecks.heldAuthorities());
        if (indexer.serving()) {
            try {
                return es.search(q, kind, scope, offset, limit);
            } catch (RuntimeException e) {
                log.warn("Elasticsearch search failed, answering from PostgreSQL: {}", e.getMessage());
            }
        }
        return sql.search(q, kind, scope, offset, limit);
    }
}

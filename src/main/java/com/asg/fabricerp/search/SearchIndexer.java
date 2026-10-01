package com.asg.fabricerp.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the Elasticsearch index in step with PostgreSQL by polling, not by hooking every save: each
 * run pushes the records {@link SearchSql#changedSince} the newest change already indexed. A save
 * anywhere - a service, an approval listener, a migration - stamps {@code updated_at}, so nothing
 * has to remember to tell search, and a node that was down catches up on its next run.
 *
 * <p>Each run starts {@link #OVERLAP} before the newest change it has seen: a transaction that
 * committed late, with an older timestamp than one already indexed, is still picked up. Indexing a
 * record twice is harmless - its id is its key.
 *
 * <p>Until the first full load has finished, {@link #serving()} is false and search answers from
 * PostgreSQL, so a half-built index never hides a document.
 */
@Configuration
@EnableScheduling
public class SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

    static final int BATCH = 500;
    static final java.time.Duration OVERLAP = java.time.Duration.ofMinutes(2);
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final SearchProperties props;
    private final ElasticsearchClient es;
    private final SearchSql sql;
    private final ReentrantLock running = new ReentrantLock();

    private volatile boolean serving;
    /** Null until the index has been checked on this node; then the newest change it holds. */
    private volatile LocalDateTime newest;
    private volatile LocalDateTime lastRun;
    private volatile String lastError;
    private volatile long indexedSinceStart;

    public SearchIndexer(SearchProperties props, ElasticsearchClient es, SearchSql sql) {
        this.props = props;
        this.es = es;
        this.sql = sql;
    }

    @Scheduled(initialDelayString = "${app.search.initial-delay:10s}", fixedDelayString = "${app.search.sync-interval:15s}")
    public void scheduled() {
        if (!props.elasticsearchEnabled() || !running.tryLock()) return;
        try {
            sync();
        } finally {
            running.unlock();
        }
    }

    /**
     * Drops the index and loads every record again - after a mapping change, or to be sure. Search
     * answers from PostgreSQL meanwhile.
     *
     * @return how many records were pushed
     */
    public long rebuild() {
        if (!props.elasticsearchEnabled()) throw new IllegalStateException("Elasticsearch is not configured (app.search.elasticsearch.url).");
        running.lock();
        try {
            serving = false;
            newest = null;
            es.deleteIndex();
            return sync();
        } finally {
            running.unlock();
        }
    }

    private long sync() {
        long pushed = 0;
        try {
            if (newest == null) {
                boolean created = es.ensureIndex();
                LocalDateTime held = created ? null : es.newestChange();
                newest = held == null ? EPOCH : held;
                if (created || held == null) log.info("Search index '{}' is empty: loading every record", props.elasticsearch().index());
            }
            LocalDateTime since = newest.equals(EPOCH) ? EPOCH : newest.minus(OVERLAP);
            String afterKey = null;
            List<SearchRecord> batch;
            do {
                batch = sql.changedSince(since, afterKey, BATCH);
                es.bulk(batch);
                pushed += batch.size();
                if (!batch.isEmpty()) {
                    SearchRecord last = batch.get(batch.size() - 1);
                    since = last.updatedAt();
                    afterKey = last.key();
                    if (since.isAfter(newest)) newest = since;
                }
            } while (batch.size() == BATCH);
            indexedSinceStart += pushed;
            lastRun = LocalDateTime.now();
            if (lastError != null || !serving) log.info("Search index '{}' is up to date", props.elasticsearch().index());
            lastError = null;
            serving = true;
        } catch (RuntimeException e) {
            // Logged once per outage, not every fifteen seconds; search falls back to PostgreSQL.
            if (lastError == null) log.warn("Search index sync failed, searching PostgreSQL until it recovers: {}", e.getMessage());
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            serving = false;
            newest = null;
        }
        return pushed;
    }

    /** Whether Elasticsearch is configured, loaded and current enough to answer searches. */
    public boolean serving() {
        return props.elasticsearchEnabled() && serving;
    }

    public Status status() {
        return new Status(props.elasticsearchEnabled(), serving(), props.elasticsearch().index(),
            lastRun, lastError, indexedSinceStart);
    }

    public record Status(boolean configured, boolean serving, String index, LocalDateTime lastSync,
                         String lastError, long indexedSinceStart) { }
}

package com.asg.fabricerp.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Central search. With {@code app.search.elasticsearch.url} blank, search runs on PostgreSQL alone;
 * with it set, records are indexed into Elasticsearch and searched there, PostgreSQL standing in
 * whenever the cluster cannot answer.
 *
 * <pre>
 *   app.search.elasticsearch.url=http://localhost:9200
 *   app.search.elasticsearch.index=fabricerp-search
 *   app.search.elasticsearch.username=    (optional, basic auth)
 *   app.search.elasticsearch.password=
 *   app.search.sync-interval=15s          how often changed records are pushed to the index
 * </pre>
 */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(Elasticsearch elasticsearch,
                               @DefaultValue("15s") Duration syncInterval) {

    public SearchProperties {
        if (elasticsearch == null) elasticsearch = new Elasticsearch(null, "fabricerp-search", null, null, Duration.ofSeconds(5));
    }

    public boolean elasticsearchEnabled() {
        return elasticsearch.url() != null && !elasticsearch.url().isBlank();
    }

    public record Elasticsearch(String url,
                                @DefaultValue("fabricerp-search") String index,
                                String username,
                                String password,
                                @DefaultValue("5s") Duration timeout) { }
}

package com.asg.fabricerp.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of central-search results.
 *
 * @param engine {@code elasticsearch} or {@code database} - which one answered, shown on the page
 *               so a stale index is never mistaken for missing data
 */
public record SearchResult(String engine, long total, List<Hit> hits) {

    public static SearchResult empty(String engine) {
        return new SearchResult(engine, 0, List.of());
    }

    public record Hit(String kind, String kindLabel, Long id, String type, String typeLabel, String code,
                      String title, String subtitle, String status, LocalDate date,
                      BigDecimal amount, String currency, String url) {

        static Hit of(SearchKind kind, Long id, String docType, String code, String title, String subtitle,
                      String status, LocalDate date, BigDecimal amount, String currency) {
            return new Hit(kind.name(), kind.label(), id, docType, SearchKind.typeLabel(kind, docType), code,
                title, subtitle, status, date, amount, currency, SearchKind.link(kind, docType, id));
        }
    }
}

package com.asg.fabricerp.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * One page of picker options, in the shape {@code App.RemoteSelect} reads - and Select2 too:
 * {@code {"results": [{id, text, code, sub}], "pagination": {"more": true}}}.
 *
 * <p>Every searchable picker feed returns this, so a list that grows past a few hundred rows is
 * searched and paged on the server instead of being shipped whole into a {@code <select>}. The
 * conventional request parameters are {@code q} (search text), {@code page} (1-based, as Select2
 * sends it) and {@code size}; {@code id} asks for one known option, to label a saved value.
 */
public record LookupPage<T>(List<T> results, Pagination pagination) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public record Pagination(boolean more) { }

    /**
     * A picker option: {@code text} is the label, {@code code} shows right-aligned in mono,
     * {@code sub} is a quieter second line (a path, a parent). Only {@code id} and {@code text}
     * are required.
     */
    public record Option(Long id, String code, String text, String sub) { }

    public static <T> LookupPage<T> of(List<T> results, boolean more) {
        return new LookupPage<>(results, new Pagination(more));
    }

    public static <T> LookupPage<T> single(T option) {
        return of(option == null ? List.of() : List.of(option), false);
    }

    public static <E, T> LookupPage<T> of(Slice<E> slice, Function<E, T> mapper) {
        return of(slice.getContent().stream().map(mapper).toList(), slice.hasNext());
    }

    /** A 1-based {@code page} and a {@code size} clamped to {@link #MAX_SIZE}, as a Pageable. */
    public static Pageable pageable(Integer page, Integer size) {
        return pageable(page, size, Sort.unsorted());
    }

    /** The same, sorted - for a query whose order is not written into it. */
    public static Pageable pageable(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 1 ? 0 : page - 1;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }

    /** {@code q} as a case-insensitive LIKE pattern; {@code "%"} (match all) when blank. */
    public static String like(String q) {
        if (q == null || q.isBlank()) return "%";
        String escaped = q.trim().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

package com.asg.fabricerp.utility.datatable;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** DataTables server-side response envelope. */
public record DataTableResponse<T>(
    int draw,
    long recordsTotal,
    long recordsFiltered,
    List<T> data
) {
    public static <E, D> DataTableResponse<D> from(int draw, Page<E> page, Function<E, D> mapper) {
        return new DataTableResponse<>(
            draw,
            page.getTotalElements(),
            page.getTotalElements(),
            page.getContent().stream().map(mapper).toList());
    }
}

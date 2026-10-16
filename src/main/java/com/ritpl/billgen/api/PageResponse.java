package com.ritpl.billgen.api;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A page of results, described the same way everywhere in this API: the caller needs to know the page it
 * asked for and whether another one exists, not the internals of a {@code Pageable}.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
        boolean last) {

    public static <S, T> PageResponse<T> of(Page<S> source, java.util.function.Function<S, T> mapping) {
        return new PageResponse<>(source.getContent().stream().map(mapping).toList(), source.getNumber(),
                source.getSize(), source.getTotalElements(), source.getTotalPages(), source.isLast());
    }
}

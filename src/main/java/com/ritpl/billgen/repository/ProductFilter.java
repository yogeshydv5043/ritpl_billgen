package com.ritpl.billgen.repository;

import com.ritpl.billgen.entity.ProductType;

/**
 * The filter state of a catalogue list. Optional fields only: a null field is not applied, so the
 * plain list, each filter chip and the Excel export all reuse the same query.
 */
public record ProductFilter(String search, Long categoryId, Boolean active, ProductType productType) {

    public static ProductFilter of(String search) {
        return new ProductFilter(search, null, null, null);
    }

    public boolean hasAnyFilter() {
        return (search != null && !search.isBlank()) || categoryId != null || active != null || productType != null;
    }
}

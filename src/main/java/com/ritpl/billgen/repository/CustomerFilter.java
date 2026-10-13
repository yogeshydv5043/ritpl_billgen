package com.ritpl.billgen.repository;

import java.time.LocalDate;
import com.ritpl.billgen.entity.AccountStatus;

/**
 * The filter state of the Super Admin customer list. Every field is optional; a null field is not
 * applied, so the same query backs the plain list, each dashboard KPI link and the search box.
 */
public record CustomerFilter(String search, AccountStatus status, Boolean subscribed, Boolean expiringSoon,
        Boolean expired, LocalDate registeredFrom, LocalDate registeredTo, Long planId, boolean includeDeleted) {

    public static CustomerFilter of(String search) {
        return new CustomerFilter(search, null, null, null, null, null, null, null, false);
    }
    public boolean hasAnyFilter() {
        return (search != null && !search.isBlank()) || status != null || subscribed != null || expiringSoon != null
                || expired != null || registeredFrom != null || registeredTo != null || planId != null || includeDeleted;
    }
}

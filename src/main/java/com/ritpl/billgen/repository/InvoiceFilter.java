package com.ritpl.billgen.repository;

import java.time.LocalDate;
import com.ritpl.billgen.entity.InvoiceStatus;

/**
 * The filter state of an invoice list. Every field is optional, so one query backs the plain list,
 * each toolbar filter, the dashboard's "this month" link and both Excel exports.
 *
 * <p>The tenant is not part of the filter: it is pinned by the service from the caller's scope, so a
 * forged parameter can never widen the search into another company's invoices.</p>
 */
public record InvoiceFilter(String search, InvoiceStatus status, LocalDate issuedFrom, LocalDate issuedTo,
        Boolean overdue, Boolean usageCounted) {

    public static InvoiceFilter of(String search) {
        return new InvoiceFilter(search, null, null, null, null, null);
    }

    public boolean hasAnyFilter() {
        return (search != null && !search.isBlank()) || status != null || issuedFrom != null || issuedTo != null
                || overdue != null || usageCounted != null;
    }
}

package com.ritpl.billgen.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;

/**
 * The query behind every invoice list: the customer's own list, the cross-tenant admin list, the
 * report and both exports. Criteria rather than JPQL text because every filter is optional and the
 * combinations are numerous - the same reason the customer module builds its queries this way.
 */
public final class InvoiceSpecs {
    private InvoiceSpecs() { }

    /** Invoices owned by one account. The tenant is always applied; nothing can list without it. */
    public static Specification<Invoice> ofCustomer(long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Invoice> forFilter(InvoiceFilter filter, LocalDate today) {
        return (root, query, cb) -> {
            InvoiceFilter f = filter == null ? InvoiceFilter.of(null) : filter;
            List<Predicate> predicates = new ArrayList<>();
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.issuedFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("issueDate"), f.issuedFrom()));
            if (f.issuedTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("issueDate"), f.issuedTo()));
            if (Boolean.TRUE.equals(f.usageCounted())) {
                predicates.add(cb.isTrue(root.get("usageCounted")));
            } else if (Boolean.FALSE.equals(f.usageCounted())) {
                predicates.add(cb.isFalse(root.get("usageCounted")));
            }
            if (Boolean.TRUE.equals(f.overdue())) {
                // Overdue is a derived state, not a stored one: issued, unpaid and past its due date.
                predicates.add(cb.equal(root.get("status"), InvoiceStatus.SENT));
                predicates.add(cb.lessThan(root.get("dueDate"), today));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(java.util.Locale.ROOT) + "%";
                List<Predicate> matches = new ArrayList<>();
                matches.add(cb.like(cb.lower(root.get("invoiceNumber")), like));
                matches.add(cb.like(cb.lower(root.get("customer").get("companyName")), like));
                predicates.add(cb.or(matches.toArray(new Predicate[0])));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

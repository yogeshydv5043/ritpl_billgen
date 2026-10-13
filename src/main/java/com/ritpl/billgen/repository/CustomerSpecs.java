package com.ritpl.billgen.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.SubscriptionStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the customer list query with the criteria API instead of JPQL text: every filter is
 * optional, the combinations are numerous, and a mistake in a hand-written null guard is exactly
 * the kind of bug that only shows up in production.
 */
public final class CustomerSpecs {
    private CustomerSpecs() { }

    /** Live (non-deleted, non-internal) accounts only. */
    public static Specification<Customer> liveAccounts() {
        return (root, query, cb) -> cb.and(cb.isFalse(root.get("systemAccount")), cb.isNull(root.get("deletedAt")));
    }

    public static Specification<Customer> forFilter(CustomerFilter filter, ZoneId zone) {
        return (root, query, cb) -> {
            CustomerFilter f = filter == null ? CustomerFilter.of(null) : filter;
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("systemAccount")));
            predicates.add(f.includeDeleted() ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));

            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("companyName")), like),
                        cb.like(cb.lower(root.get("contactName")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (f.registeredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.registeredFrom().atStartOfDay(zone).toLocalDateTime()));
            }
            if (f.registeredTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), f.registeredTo().plusDays(1).atStartOfDay(zone).toLocalDateTime()));
            }
            // Subscription-derived filters are correlated subqueries: they stay correct under
            // pagination, unlike a join, which would multiply rows.
            if (f.subscribed() != null) {
                predicates.add(f.subscribed() ? hasActiveTerm(root, query, cb, zone) : cb.not(hasActiveTerm(root, query, cb, zone)));
            }
            if (Boolean.TRUE.equals(f.expiringSoon())) {
                predicates.add(termEnds(root, query, cb, zone, LocalDate.now(zone), LocalDate.now(zone).plusDays(7)));
            }
            if (Boolean.TRUE.equals(f.expired())) {
                predicates.add(termEndedBefore(root, query, cb, zone, LocalDate.now(zone)));
            }
            if (f.planId() != null) {
                predicates.add(planSubscribed(root, query, cb, f.planId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** A fresh correlated subquery over customer subscriptions; the caller adds its predicates. */
    private static Subquery<Long> termSubquery(jakarta.persistence.criteria.CriteriaQuery<?> query) {
        return query.subquery(Long.class);
    }

    private static Predicate hasActiveTerm(Root<Customer> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, ZoneId zone) {
        Subquery<Long> sub = termSubquery(query);
        Root<CustomerSubscription> s = sub.from(CustomerSubscription.class);
        sub.select(cb.literal(1L)).where(cb.equal(s.get("customer").get("id"), root.get("id")),
                cb.equal(s.get("status"), SubscriptionStatus.ACTIVE));
        return cb.exists(sub);
    }

    private static Predicate termEnds(Root<Customer> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, ZoneId zone, LocalDate from, LocalDate to) {
        Subquery<Long> sub = termSubquery(query);
        Root<CustomerSubscription> s = sub.from(CustomerSubscription.class);
        sub.select(cb.literal(1L)).where(cb.equal(s.get("customer").get("id"), root.get("id")),
                cb.equal(s.get("status"), SubscriptionStatus.ACTIVE),
                cb.isNotNull(s.get("endsOn")), cb.between(s.get("endsOn"), from, to));
        return cb.exists(sub);
    }

    private static Predicate termEndedBefore(Root<Customer> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, ZoneId zone, LocalDate today) {
        Subquery<Long> sub = termSubquery(query);
        Root<CustomerSubscription> s = sub.from(CustomerSubscription.class);
        sub.select(cb.literal(1L)).where(cb.equal(s.get("customer").get("id"), root.get("id")),
                cb.equal(s.get("status"), SubscriptionStatus.ACTIVE),
                cb.isNotNull(s.get("endsOn")), cb.lessThan(s.get("endsOn"), today));
        return cb.exists(sub);
    }

    private static Predicate planSubscribed(Root<Customer> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, Long planId) {
        Subquery<Long> sub = termSubquery(query);
        Root<CustomerSubscription> s = sub.from(CustomerSubscription.class);
        sub.select(cb.literal(1L)).where(cb.equal(s.get("customer").get("id"), root.get("id")),
                cb.equal(s.get("plan").get("id"), planId), cb.equal(s.get("status"), SubscriptionStatus.ACTIVE));
        return cb.exists(sub);
    }
}

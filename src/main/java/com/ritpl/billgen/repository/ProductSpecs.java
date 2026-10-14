package com.ritpl.billgen.repository;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.ProductCategory;

/**
 * Queries for the catalogue lists. Built with the criteria API because every filter is optional and
 * they combine freely; the tenant predicate is always added by the service, never by the request.
 */
public final class ProductSpecs {
    private ProductSpecs() { }

    public static Specification<CatalogProduct> ofCustomer(long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<CatalogProduct> forFilter(ProductFilter filter) {
        return (root, query, cb) -> {
            ProductFilter f = filter == null ? ProductFilter.of(null) : filter;
            List<Predicate> predicates = new ArrayList<>();
            if (f.categoryId() != null) predicates.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            if (f.active() != null) predicates.add(cb.equal(root.get("active"), f.active()));
            if (f.productType() != null) predicates.add(cb.equal(root.get("productType"), f.productType()));
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Categories of one account, optionally narrowed by the search box. */
    public static Specification<ProductCategory> categories(long customerId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

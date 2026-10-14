package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.ProductCategory;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Catalogue items, owned one-to-one-by-many by a customer account.
 *
 * <p>Every method takes the caller's {@link TenantScope}: an administrator may read every
 * account's catalogue, a customer may only ever read and write its own, and a category may only
 * be picked from the same account. Administrators have read-only access across accounts, so every
 * write calls {@link TenantScope#requireWriteAccess()} first.</p>
 */
@Service
public class CatalogService {
    private final CatalogProductRepository products;
    private final ProductCategoryRepository categories;
    private final CustomerRepository customers;
    private final SubscriptionService subscriptions;
    private final AuditService audit;

    public CatalogService(CatalogProductRepository products, ProductCategoryRepository categories, CustomerRepository customers,
            SubscriptionService subscriptions, AuditService audit) {
        this.products = products; this.categories = categories; this.customers = customers;
        this.subscriptions = subscriptions; this.audit = audit;
    }

    /**
     * The paged, filtered catalogue listing. The tenant predicate is added from the caller's scope and
     * never from the request, so a customer cannot widen the search into another account's catalogue.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CatalogProduct> page(TenantScope scope,
            com.ritpl.billgen.repository.ProductFilter filter, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<CatalogProduct> tenant = scope.isAdminScope() ? null
                : com.ritpl.billgen.repository.ProductSpecs.ofCustomer(scope.requireCustomerId());
        org.springframework.data.jpa.domain.Specification<CatalogProduct> conditions =
                com.ritpl.billgen.repository.ProductSpecs.forFilter(filter);
        org.springframework.data.jpa.domain.Specification<CatalogProduct> combined =
                tenant == null ? conditions : tenant.and(conditions);
        return products.findAll(combined, safePage(pageable));
    }

    /** Page one, and a page size the database can still serve in one go, whatever the request claimed. */
    static org.springframework.data.domain.Pageable safePage(org.springframework.data.domain.Pageable pageable) {
        return pageable == null ? org.springframework.data.domain.PageRequest.of(0, 20)
                : org.springframework.data.domain.PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                        Math.min(Math.max(pageable.getPageSize(), 1), 100), pageable.getSort());
    }

    /** The full result of a filter, in the caller's order — what an export of the page must contain. */
    @Transactional(readOnly = true)
    public List<CatalogProduct> findAll(TenantScope scope, com.ritpl.billgen.repository.ProductFilter filter,
            org.springframework.data.domain.Sort sort) {
        // Capped deliberately: an export of an unbounded table is a memory incident waiting to happen.
        return page(scope, filter, org.springframework.data.domain.PageRequest.of(0, 5000, sort)).getContent();
    }

    /** The account's totals, for the header of the catalogue page. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> counts(TenantScope scope) {
        long total = countAll(scope);
        long active = countActive(scope);
        return Map.of("total", total, "active", active, "hidden", total - active);
    }

    @Transactional(readOnly = true)
    public long countAll(TenantScope scope) {
        return products.countByCustomer_Id(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public long countActive(TenantScope scope) {
        return products.countByCustomer_IdAndActiveTrue(scope.requireCustomerId());
    }

    /** The items this account created or edited most recently, bounded to a dashboard-sized page. */
    @Transactional(readOnly = true)
    public List<CatalogProduct> recent(TenantScope scope, int limit) {
        return products.findByCustomer_IdOrderByUpdatedAtDesc(scope.requireCustomerId(), bounded(limit));
    }

    static org.springframework.data.domain.Pageable bounded(int limit) {
        return org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 25));
    }

    /** Catalogue of the caller's account, or the whole estate for an administrator. */
    @Transactional(readOnly = true)
    public List<CatalogProduct> findAll(TenantScope scope) {
        return scope.isAdminScope()
                ? products.findAllByOrderByNameAsc()
                : products.findByCustomer_IdOrderByNameAsc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public List<CatalogProduct> findActive(TenantScope scope) {
        return scope.isAdminScope()
                ? products.findByActiveTrueOrderByNameAsc()
                : products.findByCustomer_IdAndActiveTrueOrderByNameAsc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public CatalogProduct findById(TenantScope scope, long id) {
        if (scope.isAdminScope()) {
            return products.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Product or service not found."));
        }
        return products.findOwned(id, scope.requireCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Product or service not found."));
    }

        @Transactional(readOnly = true)
    public List<CatalogProduct> findForCustomer(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return products.findByCustomer_IdOrderByNameAsc(customerId);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public CatalogProduct save(TenantScope scope, Long id, ProductCommand command) {
        scope.requireWriteAccess();
        long ownerId = scope.requireCustomerId();
        validate(command);
        Customer owner = customers.findById(ownerId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        String sku = command.sku().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = id == null
                ? products.existsBySkuIgnoreCaseAndCustomer_Id(sku, ownerId)
                : products.existsBySkuIgnoreCaseAndCustomer_IdAndIdNot(sku, ownerId, id);
        if (duplicate) throw new IllegalArgumentException("That SKU is already used in your catalogue.");
        if (id == null) {
            var activeSubscription = subscriptions.requireActiveForUpdate(scope);
            if (products.countByCustomer_Id(ownerId) >= activeSubscription.getEffectiveProductLimit()) {
                throw new IllegalArgumentException("Your plan's active product limit has been reached.");
            }
        }

        CatalogProduct product = id == null
                ? new CatalogProduct(owner, sku, command.name().trim(), command.productType(), command.unitPrice(), command.taxRate())
                : findById(scope, id);
        Map<String, Object> before = id == null ? null : Map.of("sku", product.getSku(), "name", product.getName(), "active", product.isActive());
        product.setCustomer(owner);
        product.setSku(sku);
        product.setName(command.name().trim());
        product.setProductType(command.productType());
        product.setCategory(resolveCategory(scope, command.categoryId()));
        product.setDescription(blankToNull(command.description()));
        product.setUnit(normalizeUnit(command.unit()));
        product.setUnitPrice(command.unitPrice().setScale(2));
        product.setTaxRate(command.taxRate().setScale(2));
        product.setActive(command.active());
        CatalogProduct saved = products.save(product);
        audit.record(AuditEventCommand.success(ownerId, id == null ? "CREATE" : "UPDATE", "PRODUCT", "Product", saved.getId(),
                id == null ? "Customer created a catalogue product." : "Customer updated a catalogue product.", before,
                Map.of("sku", saved.getSku(), "name", saved.getName(), "unitPrice", saved.getUnitPrice(),
                        "taxRate", saved.getTaxRate(), "unit", String.valueOf(saved.getUnit()), "active", saved.isActive())));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void setActive(TenantScope scope, long id, boolean active) {
        CatalogProduct product = findById(scope, id);
        scope.requireWriteAccess();
        boolean wasActive = product.isActive();
        product.setActive(active);
        audit.record(AuditEventCommand.success(product.getCustomerId(), "UPDATE", "PRODUCT", "Product", id,
                "Customer changed product availability.", Map.of("active", wasActive), Map.of("active", active)));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(TenantScope scope, long id) {
        CatalogProduct product = findById(scope, id);
        scope.requireWriteAccess();
        Long ownerId = product.getCustomerId(); String sku = product.getSku(); String name = product.getName();
        products.delete(product);
        audit.record(AuditEventCommand.success(ownerId, "DELETE", "PRODUCT", "Product", id,
                "Customer deleted a catalogue product.", Map.of("sku", sku, "name", name), null));
    }

    /** A category can only be linked when it belongs to the same account as the item. */
    private ProductCategory resolveCategory(TenantScope scope, Long categoryId) {
        if (categoryId == null) return null;
        return categories.findOwned(categoryId, scope.requireCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Choose one of your own product categories."));
    }

    /** Free text on the invoice, but never long enough to push a column over its limit. */
    private static String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) return null;
        String trimmed = unit.trim();
        if (trimmed.length() > 24) throw new IllegalArgumentException("The unit must be 24 characters or fewer.");
        return trimmed;
    }

    private void validate(ProductCommand c) {
        if (c == null || c.sku() == null || c.sku().isBlank() || c.sku().trim().length() > 50
                || c.name() == null || c.name().isBlank() || c.name().trim().length() > 160 || c.productType() == null) {
            throw new IllegalArgumentException("Enter a SKU, name and product type.");
        }
        BigDecimal price = c.unitPrice();
        BigDecimal tax = c.taxRate();
        if (price == null || price.signum() < 0 || price.scale() > 2 || price.precision() > 14) {
            throw new IllegalArgumentException("Price must be non-negative with at most 2 decimal places.");
        }
        if (tax == null || tax.signum() < 0 || tax.compareTo(new BigDecimal("100")) > 0 || tax.scale() > 2) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
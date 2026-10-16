package com.ritpl.billgen.service;

import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.ProductCategory;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Product categories, owned by a single customer account. Two accounts may use the same category
 * name because every lookup and every uniqueness check is scoped by customer id.
 */
@Service
public class ProductCategoryService {
    private final ProductCategoryRepository categories;
    private final CatalogProductRepository products;
    private final CustomerRepository customers;
    private final AuditService audit;

    public ProductCategoryService(ProductCategoryRepository categories, CatalogProductRepository products,
            CustomerRepository customers, AuditService audit) {
        this.categories = categories; this.products = products; this.customers = customers; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> findAll(TenantScope scope) {
        return scope.isAdminScope()
                ? categories.findAllByOrderByNameAsc()
                : categories.findByCustomer_IdOrderByNameAsc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> findForCustomer(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return categories.findByCustomer_IdOrderByNameAsc(customerId);
    }

    @Transactional(readOnly = true)
    public ProductCategory findById(TenantScope scope, long id) {
        if (scope.isAdminScope()) {
            return categories.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Product category not found."));
        }
        return categories.findOwned(id, scope.requireCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found."));
    }

    /** Paged, searchable category list for the owning account. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ProductCategory> page(TenantScope scope, String search,
            org.springframework.data.domain.Pageable pageable) {
        long customerId = scope.requireCustomerId();
        return categories.findAll(com.ritpl.billgen.repository.ProductSpecs.categories(customerId, search),
                com.ritpl.billgen.service.CatalogService.safePage(pageable));
    }

    /** How many items each category holds, counted in the database rather than in the page. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> productCounts(TenantScope scope) {
        java.util.Map<Long, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : products.countGroupedByCategory(scope.requireCustomerId())) {
            if (row[0] != null) counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** A hidden category cannot be picked on a new item, but items already in it keep it. */
    @Transactional
    @CacheEvict(cacheNames = {"dashboard"}, allEntries = true)
    public ProductCategory setActive(TenantScope scope, long id, boolean active) {
        ProductCategory category = findById(scope, id);
        scope.requireWriteAccess();
        boolean before = category.isActive();
        if (before == active) return category;
        category.setActive(active);
        ProductCategory saved = categories.save(category);
        audit.record(AuditEventCommand.success(scope.requireCustomerId(), "UPDATE_STATUS", "CATEGORY",
                "Product category", id, active ? "Customer made a category available again."
                        : "Customer hid a category from new catalogue items.",
                Map.of("active", before), Map.of("active", active)));
        return saved;
    }

    @Transactional(readOnly = true)
    public long countAll(TenantScope scope) {
        return categories.countByCustomer_Id(scope.requireCustomerId());
    }

    /** The categories this account touched most recently, bounded to a dashboard-sized page. */
    @Transactional(readOnly = true)
    public List<ProductCategory> recent(TenantScope scope, int limit) {
        return categories.findByCustomer_IdOrderByUpdatedAtDesc(scope.requireCustomerId(),
                com.ritpl.billgen.service.CatalogService.bounded(limit));
    }

    @Transactional(readOnly = true)
    public long countActive(TenantScope scope) {
        return categories.countByCustomer_IdAndActiveTrue(scope.requireCustomerId());
    }

    public ProductCategory save(TenantScope scope, Long id, String name, String description) {
        return save(scope, id, name, description, null);
    }

    /** {@code active == null} leaves the current availability untouched. */
    @Transactional
    @CacheEvict(cacheNames = {"dashboard"}, allEntries = true)
    public ProductCategory save(TenantScope scope, Long id, String name, String description, Boolean active) {
        scope.requireWriteAccess();
        long ownerId = scope.requireCustomerId();
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new IllegalArgumentException("Enter a category name of at most 120 characters.");
        }
        Customer owner = customers.findById(ownerId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        String cleanName = name.trim();
        boolean duplicate = id == null
                ? categories.existsByCustomer_IdAndNameIgnoreCase(ownerId, cleanName)
                : categories.existsByCustomer_IdAndNameIgnoreCaseAndIdNot(ownerId, cleanName, id);
        if (duplicate) throw new IllegalArgumentException("You already have a category with that name.");
        ProductCategory category = id == null ? new ProductCategory(owner, cleanName) : findById(scope, id);
        Map<String, Object> before = id == null ? null : Map.of("name", category.getName());
        category.setCustomer(owner);
        category.setName(cleanName);
        category.setDescription(description == null || description.isBlank() ? null : description.trim());
        if (active != null) category.setActive(active);
        ProductCategory saved = categories.save(category);
        audit.record(AuditEventCommand.success(ownerId, id == null ? "CREATE" : "UPDATE", "CATEGORY", "Product category", saved.getId(),
                id == null ? "Customer created a product category." : "Customer updated a product category.", before,
                Map.of("name", saved.getName())));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(TenantScope scope, long id) {
        ProductCategory category = findById(scope, id);
        scope.requireWriteAccess();
        // Products remain in this account's catalogue; deleting the category unassigns them.
        products.clearCategory(category.getId(), scope.requireCustomerId());
        String name = category.getName(); categories.delete(category);
        audit.record(AuditEventCommand.success(scope.requireCustomerId(), "DELETE", "CATEGORY", "Product category", id,
                "Customer deleted a product category; products were retained and unassigned.", Map.of("name", name), null));
    }
}
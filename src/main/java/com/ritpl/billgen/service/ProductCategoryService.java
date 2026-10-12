package com.ritpl.billgen.service;

import java.util.List;
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

    public ProductCategoryService(ProductCategoryRepository categories, CatalogProductRepository products, CustomerRepository customers) {
        this.categories = categories; this.products = products; this.customers = customers;
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

        @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public ProductCategory save(TenantScope scope, Long id, String name, String description) {
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
        category.setCustomer(owner);
        category.setName(cleanName);
        category.setDescription(description == null || description.isBlank() ? null : description.trim());
        return categories.save(category);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(TenantScope scope, long id) {
        ProductCategory category = findById(scope, id);
        scope.requireWriteAccess();
        // Products remain in this account's catalogue; deleting the category unassigns them.
        products.clearCategory(category.getId(), scope.requireCustomerId());
        categories.delete(category);
    }
}
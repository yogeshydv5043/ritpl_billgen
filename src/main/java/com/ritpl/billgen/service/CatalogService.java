package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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

    public CatalogService(CatalogProductRepository products, ProductCategoryRepository categories, CustomerRepository customers) {
        this.products = products; this.categories = categories; this.customers = customers;
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

        CatalogProduct product = id == null
                ? new CatalogProduct(owner, sku, command.name().trim(), command.productType(), command.unitPrice(), command.taxRate())
                : findById(scope, id);
        product.setCustomer(owner);
        product.setSku(sku);
        product.setName(command.name().trim());
        product.setProductType(command.productType());
        product.setCategory(resolveCategory(scope, command.categoryId()));
        product.setDescription(blankToNull(command.description()));
        product.setUnitPrice(command.unitPrice().setScale(2));
        product.setTaxRate(command.taxRate().setScale(2));
        product.setActive(command.active());
        return products.save(product);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void setActive(TenantScope scope, long id, boolean active) {
        CatalogProduct product = findById(scope, id);
        scope.requireWriteAccess();
        product.setActive(active);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(TenantScope scope, long id) {
        CatalogProduct product = findById(scope, id);
        scope.requireWriteAccess();
        products.delete(product);
    }

    /** A category can only be linked when it belongs to the same account as the item. */
    private ProductCategory resolveCategory(TenantScope scope, Long categoryId) {
        if (categoryId == null) return null;
        return categories.findOwned(categoryId, scope.requireCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Choose one of your own product categories."));
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
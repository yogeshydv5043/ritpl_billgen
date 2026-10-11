package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.repository.CatalogProductRepository;

@Service
public class CatalogService {
    private final CatalogProductRepository products;
    public CatalogService(CatalogProductRepository products) { this.products = products; }

    @Transactional(readOnly = true)
    public List<CatalogProduct> findAll() { return products.findAllByOrderByNameAsc(); }
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "active-products")
    public List<CatalogProduct> findActive() { return products.findByActiveTrueOrderByNameAsc(); }
    @Transactional(readOnly = true)
    public CatalogProduct findById(long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product or service not found."));
    }
    @Transactional
    @CacheEvict(cacheNames = {"active-products", "dashboard"}, allEntries = true)
    public CatalogProduct save(Long id, ProductCommand command) {
        validate(command);
        String sku = command.sku().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = id == null ? products.existsBySkuIgnoreCase(sku) : products.existsBySkuIgnoreCaseAndIdNot(sku, id);
        if (duplicate) throw new IllegalArgumentException("That SKU is already in use.");
        CatalogProduct product = id == null ? new CatalogProduct(sku, command.name().trim(), command.productType(), command.category(), command.description(), command.unitPrice(), command.taxRate()) : findById(id);
        product.setSku(sku);
        product.setName(command.name().trim());
        product.setProductType(command.productType());
        product.setCategory(blankToNull(command.category()));
        product.setDescription(blankToNull(command.description()));
        product.setUnitPrice(command.unitPrice().setScale(2));
        product.setTaxRate(command.taxRate().setScale(2));
        product.setActive(command.active());
        return products.save(product);
    }
    @Transactional
    @CacheEvict(cacheNames = {"active-products", "dashboard"}, allEntries = true)
    public void setActive(long id, boolean active) { findById(id).setActive(active); }

    private void validate(ProductCommand c) {
        if (c == null || c.sku() == null || c.sku().isBlank() || c.sku().trim().length() > 50
                || c.name() == null || c.name().isBlank() || c.name().trim().length() > 160 || c.productType() == null) {
            throw new IllegalArgumentException("Enter a SKU, name and product type.");
        }
        BigDecimal price = c.unitPrice();
        BigDecimal tax = c.taxRate();
        if (price == null || price.signum() < 0 || price.scale() > 2 || price.precision() > 14) throw new IllegalArgumentException("Price must be non-negative with at most 2 decimal places.");
        if (tax == null || tax.signum() < 0 || tax.compareTo(new BigDecimal("100")) > 0 || tax.scale() > 2) throw new IllegalArgumentException("Tax rate must be between 0 and 100.");
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

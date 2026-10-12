package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A catalogue item. Every item belongs to exactly one customer account: the record is stored
 * with its owning customer id so the backend can filter and validate ownership on every read
 * and write. A SKU only has to be unique inside the owning account.
 */
@Entity
@Table(name = "catalog_products")
public class CatalogProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant that owns this item. Never null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 50)
    private String sku;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 16)
    private ProductType productType;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private ProductCategory category;
    @Column(length = 500)
    private String description;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CatalogProduct() { }

    public CatalogProduct(Customer customer, String sku, String name, ProductType productType,
                          BigDecimal unitPrice, BigDecimal taxRate) {
        this.customer = customer;
        this.sku = sku;
        this.name = name;
        this.productType = productType;
        this.unitPrice = unitPrice;
        this.taxRate = taxRate;
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer value) { customer = value; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getSku() { return sku; }
    public void setSku(String value) { sku = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType value) { productType = value; }
    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory value) { category = value; }
    /** Kept for templates and API responses; the category is now a managed record. */
    public String getCategoryName() { return category == null ? null : category.getName(); }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return name; }
}
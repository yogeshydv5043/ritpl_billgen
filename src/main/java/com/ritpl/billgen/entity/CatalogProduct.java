package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_products")
public class CatalogProduct implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String sku;
    @Column(nullable = false, length = 160) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "product_type", nullable = false, length = 16) private ProductType productType;
    @Column(length = 80) private String category;
    @Column(length = 500) private String description;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2) private BigDecimal taxRate;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected CatalogProduct() { }
    public CatalogProduct(String sku, String name, ProductType productType, String category, String description, BigDecimal unitPrice, BigDecimal taxRate) {
        this.sku=sku; this.name=name; this.productType=productType; this.category=category; this.description=description; this.unitPrice=unitPrice; this.taxRate=taxRate;
    }
    @PrePersist void onCreate() { LocalDateTime n=LocalDateTime.now(); createdAt=n; updatedAt=n; }
    @PreUpdate void onUpdate() { updatedAt=LocalDateTime.now(); }
    public Long getId(){return id;} public String getSku(){return sku;} public void setSku(String v){sku=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public ProductType getProductType(){return productType;} public void setProductType(ProductType v){productType=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;} public BigDecimal getTaxRate(){return taxRate;} public void setTaxRate(BigDecimal v){taxRate=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}

package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A product category. Categories belong to a single customer account, and the same name may be
 * reused by different accounts because the unique key is scoped by customer id.
 */
@Entity
@Table(name = "product_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_categories_customer_name", columnNames = {"customer_id", "name"}),
        @UniqueConstraint(name = "uq_product_categories_id_customer", columnNames = {"id", "customer_id"})
})
public class ProductCategory implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant that owns this category. Never null. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 300)
    private String description;
    /** A hidden category stops being offered on new items; products already in it keep it. */
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProductCategory() { }

    public ProductCategory(Customer customer, String name) {
        this.customer = customer;
        this.name = name;
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer value) { customer = value; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return name; }
}
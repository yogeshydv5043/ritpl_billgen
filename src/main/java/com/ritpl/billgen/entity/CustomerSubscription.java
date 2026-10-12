package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * The plan a customer account purchased. The subscription is the link that unlocks the bill
 * templates bundled with {@link SubscriptionPlan#getTemplates()} for that account.
 */
@Entity
@Table(name = "customer_subscriptions", indexes = {
        @Index(name = "idx_customer_subscriptions_customer", columnList = "customer_id, created_at DESC"),
        @Index(name = "idx_customer_subscriptions_plan", columnList = "plan_id")
})
public class CustomerSubscription implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn = LocalDate.now();
    @Column(name = "ends_on")
    private LocalDate endsOn;
    @Column(name = "assigned_by", length = 190)
    private String assignedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerSubscription() { }

    public CustomerSubscription(Customer customer, SubscriptionPlan plan, SubscriptionStatus status, String assignedBy) {
        this.customer = customer;
        this.plan = plan;
        this.status = status == null ? SubscriptionStatus.ACTIVE : status;
        this.assignedBy = assignedBy;
        this.startedOn = LocalDate.now();
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isActive() { return status == SubscriptionStatus.ACTIVE; }
    /** An active subscription whose end date has passed no longer grants template access. */
    public boolean isEffective(LocalDate today) {
        return isActive() && (endsOn == null || !endsOn.isBefore(today));
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer value) { customer = value; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan value) { plan = value; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus value) { status = value; }
    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate value) { startedOn = value; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate value) { endsOn = value; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String value) { assignedBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
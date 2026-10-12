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
import jakarta.persistence.Version;

@Entity
@Table(name = "customer_subscriptions", indexes = {
        @Index(name = "idx_customer_subscriptions_customer", columnList = "customer_id, created_at DESC"),
        @Index(name = "idx_customer_subscriptions_plan", columnList = "plan_id")
})
public class CustomerSubscription implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    @Column(name = "started_on", nullable = false) private LocalDate startedOn = LocalDate.now();
    @Column(name = "ends_on") private LocalDate endsOn;
    @Column(name = "assigned_by", length = 190) private String assignedBy;
    @Column(name = "invoice_limit_override") private Integer invoiceLimitOverride;
    @Column(name = "product_limit_override") private Integer productLimitOverride;
    @Column(name = "custom_template_limit_override") private Integer customTemplateLimitOverride;
    @Column(name = "invoice_usage_carry_forward", nullable = false) private int invoiceUsageCarryForward;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected CustomerSubscription() { }
    public CustomerSubscription(Customer customer, SubscriptionPlan plan, SubscriptionStatus status, String assignedBy) {
        this(customer, plan, status, assignedBy, LocalDate.now(), null, 0);
    }
    public CustomerSubscription(Customer customer, SubscriptionPlan plan, SubscriptionStatus status, String assignedBy,
            LocalDate startedOn, LocalDate endsOn, int invoiceUsageCarryForward) {
        this.customer = customer; this.plan = plan; this.status = status == null ? SubscriptionStatus.ACTIVE : status;
        this.assignedBy = assignedBy; this.startedOn = startedOn == null ? LocalDate.now() : startedOn;
        this.endsOn = endsOn; this.invoiceUsageCarryForward = Math.max(0, invoiceUsageCarryForward);
    }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public boolean isActive() { return status == SubscriptionStatus.ACTIVE; }
    public boolean isEffective(LocalDate today) { return isActive() && !startedOn.isAfter(today) && (endsOn == null || !endsOn.isBefore(today)); }
    public int getEffectiveInvoiceLimit() { return invoiceLimitOverride == null ? plan.getMaxMonthlyInvoices() : invoiceLimitOverride; }
    public int getEffectiveProductLimit() { return productLimitOverride == null ? plan.getMaxProducts() : productLimitOverride; }
    public int getEffectiveCustomTemplateLimit() { return customTemplateLimitOverride == null ? plan.getMaxCustomTemplates() : customTemplateLimitOverride; }
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
    public Integer getInvoiceLimitOverride() { return invoiceLimitOverride; }
    public void setInvoiceLimitOverride(Integer value) { invoiceLimitOverride = value; }
    public Integer getProductLimitOverride() { return productLimitOverride; }
    public void setProductLimitOverride(Integer value) { productLimitOverride = value; }
    public Integer getCustomTemplateLimitOverride() { return customTemplateLimitOverride; }
    public void setCustomTemplateLimitOverride(Integer value) { customTemplateLimitOverride = value; }
    public int getInvoiceUsageCarryForward() { return invoiceUsageCarryForward; }
    public void setInvoiceUsageCarryForward(int value) { invoiceUsageCarryForward = Math.max(0, value); }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

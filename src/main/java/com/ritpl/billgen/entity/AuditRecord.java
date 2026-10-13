package com.ritpl.billgen.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Append-only security and business audit event. Never stores credentials or payment secrets. */
@Entity
@Table(name = "audit_records")
public class AuditRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Column(nullable = false, length = 190)
    private String actor;
    @Column(nullable = false, length = 48)
    private String action;
    @Column(name = "resource_type", nullable = false, length = 48)
    private String resourceType;
    @Column(name = "resource_name", length = 160)
    private String resourceName;
    @Column(name = "resource_id", length = 80)
    private String resourceId;
    @Column(nullable = false, length = 16)
    private String outcome;
    @Column(nullable = false, length = 1000)
    private String summary;
    @Column(name = "before_values", columnDefinition = "text")
    private String beforeValues;
    @Column(name = "after_values", columnDefinition = "text")
    private String afterValues;
    /** The audit definition that governs this event's quota and retention (nullable for old rows). */
    @Column(name = "audit_type_id")
    private Long auditTypeId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditRecord() { }
    public AuditRecord(Customer customer, String actor, String action, String resourceType, String resourceName,
            String resourceId, String outcome, String summary, String beforeValues, String afterValues) {
        this.customer = customer; this.actor = actor; this.action = action; this.resourceType = resourceType;
        this.resourceName = resourceName; this.resourceId = resourceId; this.outcome = outcome;
        this.summary = summary; this.beforeValues = beforeValues; this.afterValues = afterValues;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceName() { return resourceName; }
    public String getResourceId() { return resourceId; }
    public String getOutcome() { return outcome; }
    public String getSummary() { return summary; }
    public String getBeforeValues() { return beforeValues; }
    public String getAfterValues() { return afterValues; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getAuditTypeId() { return auditTypeId; }
    public void setAuditTypeId(Long value) { auditTypeId = value; }
}

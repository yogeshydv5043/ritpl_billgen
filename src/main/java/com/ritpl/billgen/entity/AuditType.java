package com.ritpl.billgen.entity;

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

/**
 * What the application tracks, how much of it a plan may produce and how long the history is
 * kept. This is the audit <em>definition</em>: it is deliberately separate from the immutable
 * {@link AuditRecord} history rows and from the quotas enforced at write time.
 */
@Entity
@Table(name = "audit_types")
public class AuditType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 48) private String code;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "event_category", nullable = false, length = 32)
    private AuditEventCategory eventCategory = AuditEventCategory.SYSTEM;
    /** When set, the definition matches an audit record by its action; otherwise by resource type. */
    @Column(length = 48) private String action;
    @Column(name = "resource_type", length = 48) private String resourceType;
    @Enumerated(EnumType.STRING) @Column(name = "limit_period", nullable = false, length = 16)
    private LimitPeriod limitPeriod = LimitPeriod.NONE;
    @Column(name = "usage_limit") private Integer usageLimit;
    @Column(name = "custom_period_days") private Integer customPeriodDays;
    @Column(name = "retention_days", nullable = false) private int retentionDays = 730;
    @Column(name = "alert_threshold_percent", nullable = false) private int alertThresholdPercent = 80;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "system_defined", nullable = false) private boolean systemDefined = false;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by", length = 190) private String deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected AuditType() { }
    public AuditType(String code, String name, String description, AuditEventCategory eventCategory,
            String action, String resourceType) {
        this.code = code; this.name = name; this.description = description;
        this.eventCategory = eventCategory == null ? AuditEventCategory.SYSTEM : eventCategory;
        this.action = action; this.resourceType = resourceType;
    }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    /** Whether this definition governs a given audit record. */
    public boolean matches(String recordAction, String recordResourceType) {
        if (action != null && !action.isBlank()) return action.equalsIgnoreCase(recordAction);
        return resourceType != null && !resourceType.isBlank() && resourceType.equalsIgnoreCase(recordResourceType);
    }
    /** A quota is only enforced when a period and a positive limit are both configured. */
    public boolean hasQuota() { return usageLimit != null && usageLimit > 0 && limitPeriod != null && limitPeriod != LimitPeriod.NONE; }
    /** Days of history the configured quota is measured over. */
    public int quotaWindowDays() {
        if (limitPeriod == null) return 30;
        return switch (limitPeriod) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            case MONTHLY -> 31;
            case YEARLY -> 366;
            case CUSTOM -> customPeriodDays == null ? 30 : customPeriodDays;
            case NONE -> 36500;
        };
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String v) { code = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public AuditEventCategory getEventCategory() { return eventCategory; }
    public void setEventCategory(AuditEventCategory v) { eventCategory = v == null ? AuditEventCategory.SYSTEM : v; }
    public String getAction() { return action; }
    public void setAction(String v) { action = v == null || v.isBlank() ? null : v.trim().toUpperCase(java.util.Locale.ROOT); }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String v) { resourceType = v == null || v.isBlank() ? null : v.trim().toUpperCase(java.util.Locale.ROOT); }
    public LimitPeriod getLimitPeriod() { return limitPeriod; }
    public void setLimitPeriod(LimitPeriod v) { limitPeriod = v == null ? LimitPeriod.NONE : v; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer v) { usageLimit = v; }
    public Integer getCustomPeriodDays() { return customPeriodDays; }
    public void setCustomPeriodDays(Integer v) { customPeriodDays = v; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int v) { retentionDays = v; }
    public int getAlertThresholdPercent() { return alertThresholdPercent; }
    public void setAlertThresholdPercent(int v) { alertThresholdPercent = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public boolean isSystemDefined() { return systemDefined; }
    public void setSystemDefined(boolean v) { systemDefined = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime v) { deletedAt = v; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String v) { deletedBy = v; }
    public boolean isDeleted() { return deletedAt != null; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return name; }
}

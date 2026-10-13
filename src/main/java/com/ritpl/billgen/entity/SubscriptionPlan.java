package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 160) private String tagline;
    @Column(length = 500) private String description;
    @Column(name = "monthly_price", nullable = false, precision = 14, scale = 2) private BigDecimal monthlyPrice;
    @Column(name = "annual_price", nullable = false, precision = 14, scale = 2) private BigDecimal annualPrice;
    @Column(nullable = false, length = 3) private String currency="INR";
    @Column(name = "max_customers", nullable = false) private int maxCustomers;
    @Column(name = "max_monthly_invoices", nullable = false) private int maxMonthlyInvoices;
    @Column(name = "max_products", nullable = false) private int maxProducts = 100;
    @Column(name = "max_custom_templates", nullable = false) private int maxCustomTemplates = 3;
    @Column(nullable = false, columnDefinition = "text") private String features;
    @Column(nullable = false) private boolean featured;
    @Enumerated(EnumType.STRING) @Column(name = "billing_cycle", nullable = false, length = 16)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;
    @Column(name = "billing_period_days") private Integer billingPeriodDays;
    @Column(name = "grace_period_days", nullable = false) private int gracePeriodDays = 0;
    @Enumerated(EnumType.STRING) @Column(name = "over_limit_behavior", nullable = false, length = 16)
    private OverLimitBehavior overLimitBehavior = OverLimitBehavior.BLOCK;
    @Column(name = "usage_warning_percent", nullable = false) private int usageWarningPercent = 80;
    @Column(name = "notify_at_warning", nullable = false) private boolean notifyAtWarning = true;
    @Column(name = "notify_at_limit", nullable = false) private boolean notifyAtLimit = true;
    @Column(name = "audit_daily_limit") private Integer auditDailyLimit;
    @Column(name = "audit_monthly_limit") private Integer auditMonthlyLimit;
    @Column(name = "audit_yearly_limit") private Integer auditYearlyLimit;
    @Column(name = "audit_default_retention_days", nullable = false) private int auditDefaultRetentionDays = 730;
    @Column(name = "auto_renewal_allowed", nullable = false) private boolean autoRenewalAllowed = true;
    @Column(name = "allow_downgrade", nullable = false) private boolean allowDowngrade = true;
    @Column(name = "allow_midterm_upgrade", nullable = false) private boolean allowMidtermUpgrade = true;
    @Column(name = "expiry_reminder_days", nullable = false, length = 64) private String expiryReminderDays = "30,7,3,1";
    @Column(name = "custom_period_days") private Integer customPeriodDays;
    @Column(name = "plan_start_on") private java.time.LocalDate planStartOn;
    @Column(name = "plan_end_on") private java.time.LocalDate planEndOn;
    @Column(name = "deleted_at") private java.time.Instant deletedAt;
    @Column(name = "deleted_by", length = 190) private String deletedBy;
    @Column(name = "updated_by", length = 190) private String updatedBy;
    @Column(nullable = false) private boolean active=true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    /**
     * The bill templates included with this plan. A customer can only issue invoices with the
     * templates of the plan it purchased.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "plan_templates",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "template_id"))
    private Set<BillTemplate> templates = new LinkedHashSet<>();
    /** Audit definitions this plan unlocks. Kept next to the template entitlement for the same reason. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "plan_audit_types",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "audit_type_id"))
    private Set<AuditType> auditTypes = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected SubscriptionPlan() { }
    public SubscriptionPlan(String code,String name,String tagline,String description,BigDecimal monthlyPrice,BigDecimal annualPrice,String currency,int maxCustomers,int maxMonthlyInvoices,String features,boolean featured,int sortOrder){
        this.code=code;this.name=name;this.tagline=tagline;this.description=description;this.monthlyPrice=monthlyPrice;this.annualPrice=annualPrice;this.currency=currency;this.maxCustomers=maxCustomers;this.maxMonthlyInvoices=maxMonthlyInvoices;this.features=features;this.featured=featured;this.sortOrder=sortOrder;
    }
    @PrePersist void onCreate(){LocalDateTime n=LocalDateTime.now();createdAt=n;updatedAt=n;} @PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getTagline(){return tagline;} public void setTagline(String v){tagline=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getMonthlyPrice(){return monthlyPrice;} public void setMonthlyPrice(BigDecimal v){monthlyPrice=v;} public BigDecimal getAnnualPrice(){return annualPrice;} public void setAnnualPrice(BigDecimal v){annualPrice=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public int getMaxCustomers(){return maxCustomers;} public void setMaxCustomers(int v){maxCustomers=v;}
    public int getMaxMonthlyInvoices(){return maxMonthlyInvoices;} public void setMaxMonthlyInvoices(int v){maxMonthlyInvoices=v;}
    public int getMaxProducts(){return maxProducts;} public void setMaxProducts(int v){maxProducts=v;}
    public int getMaxCustomTemplates(){return maxCustomTemplates;} public void setMaxCustomTemplates(int v){maxCustomTemplates=v;}
    public String getFeatures(){return features;} public void setFeatures(String v){features=v;}
    public boolean isFeatured(){return featured;} public void setFeatured(boolean v){featured=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
    public Set<BillTemplate> getTemplates(){return templates;} public void setTemplates(Set<BillTemplate> v){templates=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);}
    /** The ids of every template bundled with this plan. */
    public Set<Long> getTemplateIds(){java.util.Set<Long> ids=new java.util.LinkedHashSet<>();for(BillTemplate t:templates){ids.add(t.getId());}return ids;}
    public Set<AuditType> getAuditTypes(){return auditTypes;} public void setAuditTypes(Set<AuditType> v){auditTypes = v == null ? new LinkedHashSet<>() : new LinkedHashSet<>(v);}
    public java.util.List<Long> getAuditTypeIds(){return auditTypes.stream().map(AuditType::getId).filter(java.util.Objects::nonNull).toList();}
    public BillingCycle getBillingCycle(){return billingCycle == null ? BillingCycle.MONTHLY : billingCycle;} public void setBillingCycle(BillingCycle v){billingCycle = v;}
    public Integer getBillingPeriodDays(){return billingPeriodDays;} public void setBillingPeriodDays(Integer v){billingPeriodDays = v;}
    public int getGracePeriodDays(){return gracePeriodDays;} public void setGracePeriodDays(int v){gracePeriodDays = Math.max(0, v);}
    public OverLimitBehavior getOverLimitBehavior(){return overLimitBehavior == null ? OverLimitBehavior.BLOCK : overLimitBehavior;} public void setOverLimitBehavior(OverLimitBehavior v){overLimitBehavior = v;}
    public int getUsageWarningPercent(){return usageWarningPercent;} public void setUsageWarningPercent(int v){usageWarningPercent = Math.max(1, Math.min(100, v));}
    public boolean isNotifyAtWarning(){return notifyAtWarning;} public void setNotifyAtWarning(boolean v){notifyAtWarning = v;}
    public boolean isNotifyAtLimit(){return notifyAtLimit;} public void setNotifyAtLimit(boolean v){notifyAtLimit = v;}
    public Integer getAuditDailyLimit(){return auditDailyLimit;} public void setAuditDailyLimit(Integer v){auditDailyLimit = v;}
    public Integer getAuditMonthlyLimit(){return auditMonthlyLimit;} public void setAuditMonthlyLimit(Integer v){auditMonthlyLimit = v;}
    public Integer getAuditYearlyLimit(){return auditYearlyLimit;} public void setAuditYearlyLimit(Integer v){auditYearlyLimit = v;}
    public int getAuditDefaultRetentionDays(){return auditDefaultRetentionDays;} public void setAuditDefaultRetentionDays(int v){auditDefaultRetentionDays = Math.max(1, v);}
    public boolean isAutoRenewalAllowed(){return autoRenewalAllowed;} public void setAutoRenewalAllowed(boolean v){autoRenewalAllowed = v;}
    public boolean isAllowDowngrade(){return allowDowngrade;} public void setAllowDowngrade(boolean v){allowDowngrade = v;}
    public boolean isAllowMidtermUpgrade(){return allowMidtermUpgrade;} public void setAllowMidtermUpgrade(boolean v){allowMidtermUpgrade = v;}
    public String getExpiryReminderDays(){return expiryReminderDays == null || expiryReminderDays.isBlank() ? "30,7,3,1" : expiryReminderDays;} public void setExpiryReminderDays(String v){expiryReminderDays = v;}
    public java.util.List<Integer> reminderOffsets(){
        if (expiryReminderDays == null || expiryReminderDays.isBlank()) return java.util.List.of(7, 3, 1);
        return java.util.Arrays.stream(expiryReminderDays.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .filter(v -> v.chars().allMatch(Character::isDigit)).map(Integer::valueOf)
                .filter(v -> v >= 0 && v <= 365).distinct().sorted(java.util.Comparator.reverseOrder()).toList();
    }
    public Integer getCustomPeriodDays(){return customPeriodDays;} public void setCustomPeriodDays(Integer v){customPeriodDays = v;}
    public java.time.LocalDate getPlanStartOn(){return planStartOn;} public void setPlanStartOn(java.time.LocalDate v){planStartOn = v;}
    public java.time.LocalDate getPlanEndOn(){return planEndOn;} public void setPlanEndOn(java.time.LocalDate v){planEndOn = v;}
    public java.time.Instant getDeletedAt(){return deletedAt;} public void setDeletedAt(java.time.Instant v){deletedAt = v;}
    public String getDeletedBy(){return deletedBy;} public void setDeletedBy(String v){deletedBy = v;}
    public boolean isDeleted(){return deletedAt != null;}
    public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy = v;}
    /** The window an invoice allowance is measured over, derived from the billing cycle. */
    public java.time.Period usageWindow(){
        return switch (getBillingCycle()) {
            case MONTHLY -> java.time.Period.ofMonths(1);
            case YEARLY -> java.time.Period.ofYears(1);
            case CUSTOM -> java.time.Period.ofDays(billingPeriodDays == null ? 30 : Math.max(1, billingPeriodDays));
        };
    }
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}

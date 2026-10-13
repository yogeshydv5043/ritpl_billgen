package com.ritpl.billgen.web.form;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import com.ritpl.billgen.entity.BillingCycle;
import com.ritpl.billgen.entity.OverLimitBehavior;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.service.PlanCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PlanForm {
    @NotBlank @Size(max=40) @Pattern(regexp="[A-Za-z0-9_-]+") private String code;
    @NotBlank @Size(max=100) private String name;
    @Size(max=160) private String tagline;
    @Size(max=500) private String description;
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal monthlyPrice = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal annualPrice = BigDecimal.ZERO;
    @NotBlank @Pattern(regexp="[A-Za-z]{3}") private String currency = "INR";
    @Min(1) private int maxCustomers = 25;
    @Min(1) private int maxMonthlyInvoices = 50;
    @Min(1) private int maxProducts = 100;
    @Min(0) private int maxCustomTemplates = 3;
    @NotBlank @Size(max=5000) private String features;
    private boolean featured;
    private boolean active = true;
    private int sortOrder = 10;
    private Set<Long> templateIds = new LinkedHashSet<>();
    private Set<Long> auditTypeIds = new LinkedHashSet<>();
    private BillingCycle billingCycle = BillingCycle.MONTHLY;
    @Min(1) private Integer billingPeriodDays;
    @Min(0) private int gracePeriodDays = 0;
    @NotNull private OverLimitBehavior overLimitBehavior = OverLimitBehavior.BLOCK;
    @Min(1) private int usageWarningPercent = 80;
    private boolean notifyAtWarning = true;
    private boolean notifyAtLimit = true;
    private Integer auditDailyLimit;
    private Integer auditMonthlyLimit;
    private Integer auditYearlyLimit;
    @Min(1) private int auditDefaultRetentionDays = 730;
    private boolean autoRenewalAllowed = true;
    private boolean allowDowngrade = true;
    private boolean allowMidtermUpgrade = true;
    private String expiryReminderDays = "30,7,3,1";
    private Integer customPeriodDays;
    private java.time.LocalDate planStartOn;
    private java.time.LocalDate planEndOn;

    public static PlanForm from(SubscriptionPlan p) {
        PlanForm f = new PlanForm();
        f.code = p.getCode(); f.name = p.getName(); f.tagline = p.getTagline(); f.description = p.getDescription();
        f.monthlyPrice = p.getMonthlyPrice(); f.annualPrice = p.getAnnualPrice(); f.currency = p.getCurrency();
        f.maxCustomers = p.getMaxCustomers(); f.maxMonthlyInvoices = p.getMaxMonthlyInvoices();
        f.maxProducts = p.getMaxProducts(); f.maxCustomTemplates = p.getMaxCustomTemplates();
        f.features = p.getFeatures(); f.featured = p.isFeatured(); f.active = p.isActive(); f.sortOrder = p.getSortOrder();
        f.templateIds = new LinkedHashSet<>(p.getTemplateIds());
        f.auditTypeIds = new LinkedHashSet<>(p.getAuditTypeIds());
        f.billingCycle = p.getBillingCycle(); f.billingPeriodDays = p.getBillingPeriodDays();
        f.gracePeriodDays = p.getGracePeriodDays(); f.overLimitBehavior = p.getOverLimitBehavior();
        f.usageWarningPercent = p.getUsageWarningPercent(); f.notifyAtWarning = p.isNotifyAtWarning();
        f.notifyAtLimit = p.isNotifyAtLimit(); f.auditDailyLimit = p.getAuditDailyLimit();
        f.auditMonthlyLimit = p.getAuditMonthlyLimit(); f.auditYearlyLimit = p.getAuditYearlyLimit();
        f.auditDefaultRetentionDays = p.getAuditDefaultRetentionDays(); f.autoRenewalAllowed = p.isAutoRenewalAllowed();
        f.allowDowngrade = p.isAllowDowngrade(); f.allowMidtermUpgrade = p.isAllowMidtermUpgrade();
        f.expiryReminderDays = p.getExpiryReminderDays(); f.customPeriodDays = p.getCustomPeriodDays();
        f.planStartOn = p.getPlanStartOn(); f.planEndOn = p.getPlanEndOn();
        return f;
    }

    public PlanCommand toCommand() {
        return new PlanCommand(code, name, tagline, description, monthlyPrice, annualPrice, currency,
                maxCustomers, maxMonthlyInvoices, maxProducts, maxCustomTemplates, features, featured, active,
                sortOrder, templateIds, billingCycle, billingPeriodDays, gracePeriodDays, overLimitBehavior,
                usageWarningPercent, notifyAtWarning, notifyAtLimit, auditDailyLimit, auditMonthlyLimit,
                auditYearlyLimit, auditDefaultRetentionDays, autoRenewalAllowed, allowDowngrade, allowMidtermUpgrade,
                expiryReminderDays, customPeriodDays, planStartOn, planEndOn, auditTypeIds);
    }
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getTagline(){return tagline;} public void setTagline(String v){tagline=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getMonthlyPrice(){return monthlyPrice;} public void setMonthlyPrice(BigDecimal v){monthlyPrice=v;}
    public BigDecimal getAnnualPrice(){return annualPrice;} public void setAnnualPrice(BigDecimal v){annualPrice=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public int getMaxCustomers(){return maxCustomers;} public void setMaxCustomers(int v){maxCustomers=v;}
    public int getMaxMonthlyInvoices(){return maxMonthlyInvoices;} public void setMaxMonthlyInvoices(int v){maxMonthlyInvoices=v;}
    public int getMaxProducts(){return maxProducts;} public void setMaxProducts(int v){maxProducts=v;}
    public int getMaxCustomTemplates(){return maxCustomTemplates;} public void setMaxCustomTemplates(int v){maxCustomTemplates=v;}
    public String getFeatures(){return features;} public void setFeatures(String v){features=v;}
    public boolean isFeatured(){return featured;} public void setFeatured(boolean v){featured=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
    public Set<Long> getTemplateIds(){return templateIds;} public void setTemplateIds(Set<Long> v){templateIds=v;}
    public Set<Long> getAuditTypeIds(){return auditTypeIds;} public void setAuditTypeIds(Set<Long> v){auditTypeIds=v;}
    public BillingCycle getBillingCycle(){return billingCycle;} public void setBillingCycle(BillingCycle v){billingCycle=v;}
    public Integer getBillingPeriodDays(){return billingPeriodDays;} public void setBillingPeriodDays(Integer v){billingPeriodDays=v;}
    public int getGracePeriodDays(){return gracePeriodDays;} public void setGracePeriodDays(int v){gracePeriodDays=v;}
    public OverLimitBehavior getOverLimitBehavior(){return overLimitBehavior;} public void setOverLimitBehavior(OverLimitBehavior v){overLimitBehavior=v;}
    public int getUsageWarningPercent(){return usageWarningPercent;} public void setUsageWarningPercent(int v){usageWarningPercent=v;}
    public boolean isNotifyAtWarning(){return notifyAtWarning;} public void setNotifyAtWarning(boolean v){notifyAtWarning=v;}
    public boolean isNotifyAtLimit(){return notifyAtLimit;} public void setNotifyAtLimit(boolean v){notifyAtLimit=v;}
    public Integer getAuditDailyLimit(){return auditDailyLimit;} public void setAuditDailyLimit(Integer v){auditDailyLimit=v;}
    public Integer getAuditMonthlyLimit(){return auditMonthlyLimit;} public void setAuditMonthlyLimit(Integer v){auditMonthlyLimit=v;}
    public Integer getAuditYearlyLimit(){return auditYearlyLimit;} public void setAuditYearlyLimit(Integer v){auditYearlyLimit=v;}
    public int getAuditDefaultRetentionDays(){return auditDefaultRetentionDays;} public void setAuditDefaultRetentionDays(int v){auditDefaultRetentionDays=v;}
    public boolean isAutoRenewalAllowed(){return autoRenewalAllowed;} public void setAutoRenewalAllowed(boolean v){autoRenewalAllowed=v;}
    public boolean isAllowDowngrade(){return allowDowngrade;} public void setAllowDowngrade(boolean v){allowDowngrade=v;}
    public boolean isAllowMidtermUpgrade(){return allowMidtermUpgrade;} public void setAllowMidtermUpgrade(boolean v){allowMidtermUpgrade=v;}
    public String getExpiryReminderDays(){return expiryReminderDays;} public void setExpiryReminderDays(String v){expiryReminderDays=v;}
    public Integer getCustomPeriodDays(){return customPeriodDays;} public void setCustomPeriodDays(Integer v){customPeriodDays=v;}
    public java.time.LocalDate getPlanStartOn(){return planStartOn;} public void setPlanStartOn(java.time.LocalDate v){planStartOn=v;}
    public java.time.LocalDate getPlanEndOn(){return planEndOn;} public void setPlanEndOn(java.time.LocalDate v){planEndOn=v;}
}

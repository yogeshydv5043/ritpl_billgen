package com.ritpl.billgen.entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * The working preferences of one company account: what a new invoice is pre-filled with, how far the
 * account may look into its own audit trail, which messages it wants, and how wide its lists and
 * exports are.
 *
 * <p>There is one row per account, keyed by the account id, and a row is only written when the
 * account saves the settings screen for the first time - reads fall back to {@link #defaults}. A
 * plain id is stored for the chosen template and category rather than a relation, because the
 * entitlement to use them is decided by the subscription at the moment of use, not by this row.</p>
 */
@Entity
@Table(name = "customer_settings")
public class CustomerSettings {

    @Id private Long customerId;
    @OneToOne @MapsId @JoinColumn(name = "customer_id", nullable = false) private Customer customer;

    @Column(name = "default_template_id") private Long defaultTemplateId;
    @Column(name = "default_custom_template_id") private Long defaultCustomTemplateId;
    @Column(name = "default_currency", nullable = false, length = 3) private String defaultCurrency = "INR";
    @Column(name = "invoice_number_prefix", nullable = false, length = 8) private String invoiceNumberPrefix = "INV-";
    @Column(name = "default_due_days", nullable = false) private int defaultDueDays = 14;
    @Column(name = "default_tax_rate", precision = 5, scale = 2) private BigDecimal defaultTaxRate;
    @Column(name = "default_payment_terms", length = 240) private String defaultPaymentTerms;
    @Column(name = "default_notes", length = 600) private String defaultNotes;
    @Column(name = "default_footer", length = 600) private String defaultFooter;

    @Column(name = "default_unit", length = 24) private String defaultUnit;
    @Column(name = "default_category_id") private Long defaultCategoryId;
    @Column(name = "list_page_size", nullable = false) private int listPageSize = 20;

    @Enumerated(EnumType.STRING) @Column(name = "audit_history_period", nullable = false, length = 16)
    private AuditHistoryPeriod auditHistoryPeriod = AuditHistoryPeriod.ONE_YEAR;

    @Column(name = "notify_expiry_reminders", nullable = false) private boolean notifyExpiryReminders = true;
    @Column(name = "notify_quota_alerts", nullable = false) private boolean notifyQuotaAlerts = true;
    @Column(name = "notify_payment_updates", nullable = false) private boolean notifyPaymentUpdates = true;
    @Column(name = "notify_security_alerts", nullable = false) private boolean notifySecurityAlerts = true;

    @Column(name = "export_range_days", nullable = false) private int exportRangeDays = 90;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CustomerSettings() { }

    public CustomerSettings(Customer customer) {
        this.customer = customer;
        this.customerId = customer == null ? null : customer.getId();
    }

    /** The values an account sees before it has saved anything; never persisted by a read. */
    public static CustomerSettings defaults(Customer customer) {
        return new CustomerSettings(customer);
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public Long getCustomerId() { return customerId; }
    public Customer getCustomer() { return customer; }
    public Long getDefaultTemplateId() { return defaultTemplateId; }
    public Long getDefaultCustomTemplateId() { return defaultCustomTemplateId; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public String getInvoiceNumberPrefix() { return invoiceNumberPrefix; }
    public int getDefaultDueDays() { return defaultDueDays; }
    public BigDecimal getDefaultTaxRate() { return defaultTaxRate; }
    public String getDefaultPaymentTerms() { return defaultPaymentTerms; }
    public String getDefaultNotes() { return defaultNotes; }
    public String getDefaultFooter() { return defaultFooter; }
    public String getDefaultUnit() { return defaultUnit; }
    public Long getDefaultCategoryId() { return defaultCategoryId; }
    public int getListPageSize() { return listPageSize; }
    public AuditHistoryPeriod getAuditHistoryPeriod() { return AuditHistoryPeriod.orDefault(auditHistoryPeriod); }
    public boolean isNotifyExpiryReminders() { return notifyExpiryReminders; }
    public boolean isNotifyQuotaAlerts() { return notifyQuotaAlerts; }
    public boolean isNotifyPaymentUpdates() { return notifyPaymentUpdates; }
    public boolean isNotifySecurityAlerts() { return notifySecurityAlerts; }
    public int getExportRangeDays() { return exportRangeDays; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDefaultTemplateId(Long v) { defaultTemplateId = v; }
    public void setDefaultCustomTemplateId(Long v) { defaultCustomTemplateId = v; }
    public void setDefaultCurrency(String v) { defaultCurrency = v; }
    public void setInvoiceNumberPrefix(String v) { invoiceNumberPrefix = v; }
    public void setDefaultDueDays(int v) { defaultDueDays = v; }
    public void setDefaultTaxRate(BigDecimal v) { defaultTaxRate = v; }
    public void setDefaultPaymentTerms(String v) { defaultPaymentTerms = v; }
    public void setDefaultNotes(String v) { defaultNotes = v; }
    public void setDefaultFooter(String v) { defaultFooter = v; }
    public void setDefaultUnit(String v) { defaultUnit = v; }
    public void setDefaultCategoryId(Long v) { defaultCategoryId = v; }
    public void setListPageSize(int v) { listPageSize = v; }
    public void setAuditHistoryPeriod(AuditHistoryPeriod v) { auditHistoryPeriod = v; }
    public void setNotifyExpiryReminders(boolean v) { notifyExpiryReminders = v; }
    public void setNotifyQuotaAlerts(boolean v) { notifyQuotaAlerts = v; }
    public void setNotifyPaymentUpdates(boolean v) { notifyPaymentUpdates = v; }
    public void setNotifySecurityAlerts(boolean v) { notifySecurityAlerts = v; }
    public void setExportRangeDays(int v) { exportRangeDays = v; }

    /** The oldest audit record the account may list or export; nothing is deleted by this rule. */
    public java.time.Instant auditWindowStart(java.time.Instant now) {
        return now.minusSeconds(getAuditHistoryPeriod().getDays() * 86_400L);
    }

    /** True when this account still wants the given kind of platform message. */
    public boolean wants(CustomerAlertTopic topic) {
        if (topic == null) return true;
        return switch (topic) {
            case EXPIRY_REMINDER -> notifyExpiryReminders;
            case QUOTA_ALERT -> notifyQuotaAlerts;
            case PAYMENT_UPDATE -> notifyPaymentUpdates;
            case SECURITY_ALERT -> notifySecurityAlerts;
        };
    }
}

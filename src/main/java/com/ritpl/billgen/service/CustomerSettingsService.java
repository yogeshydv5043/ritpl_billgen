package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerAlertTopic;
import com.ritpl.billgen.entity.CustomerSettings;
import com.ritpl.billgen.entity.AuditHistoryPeriod;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSettingsRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The preferences a customer account keeps for itself: what a new invoice starts with, how far the
 * account may look into its own audit trail, which platform messages it wants, and how wide its lists
 * and exports are.
 *
 * <p>An account without a saved row reads as {@link CustomerSettings#defaults}, so nothing on this
 * screen is required before the application works, and a read never writes. Everything the rest of the
 * application needs is exposed through the small accessors at the bottom, which is also where the
 * documented limits live - the page size is clamped, the audit window is bounded by the platform, and
 * the numbering prefix is validated before it can reach an invoice.</p>
 */
@Service
public class CustomerSettingsService {
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern PREFIX = Pattern.compile("[A-Z][A-Z0-9-]{0,7}");
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 50, 100);
    private static final int MAX_PAYMENT_TERMS = 240;
    private static final int MAX_LONG_TEXT = 600;

    private final CustomerSettingsRepository settings;
    private final CustomerRepository customers;
    private final SubscriptionService subscriptions;
    private final CustomTemplateService customTemplates;
    private final ProductCategoryService categories;
    private final AuditService audit;

    public CustomerSettingsService(CustomerSettingsRepository settings, CustomerRepository customers,
            SubscriptionService subscriptions, CustomTemplateService customTemplates,
            ProductCategoryService categories, AuditService audit) {
        this.settings = settings; this.customers = customers; this.subscriptions = subscriptions;
        this.customTemplates = customTemplates; this.categories = categories; this.audit = audit;
    }

    /** The row, or the platform defaults when the account has never saved the screen. Read-only. */
    @Transactional(readOnly = true)
    public CustomerSettings get(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return find(customerId);
    }

    /**
     * Internal reads: the caller has already resolved the tenant (an invoice's owner, a scheduled
     * reminder's recipient), so no scope is consulted here.
     */
    @Transactional(readOnly = true)
    public CustomerSettings find(long customerId) {
        // A detached defaults object is what an account without a row reads as, so this never writes
        // and never fails on an account that has meanwhile been soft-deleted.
        return settings.findById(customerId).map(CustomerSettings::attached).orElseGet(
                () -> CustomerSettings.defaults(customers.findById(customerId).orElse(null)));
    }

    @Transactional
    public CustomerSettings save(TenantScope scope, CustomerSettingsCommand command) {
        scope.requireWriteAccess();
        long customerId = scope.requireCustomerId();
        Customer customer = customers.findById(customerId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        // Update in place when the account has saved before; insert the first row otherwise. The two
        // cases are indistinguishable by id alone, because the id is the account id either way.
        CustomerSettings entity = settings.findById(customerId).map(CustomerSettings::attached)
                .orElseGet(() -> new CustomerSettings(customer));
        Map<String, Object> before = describe(entity);

        if (command == null) throw new IllegalArgumentException("Nothing to save.");
        entity.setDefaultCurrency(upper(command.defaultCurrency(), "INR", CURRENCY,
                "Currency must be a three-letter code such as INR or USD."));
        entity.setInvoiceNumberPrefix(upper(command.invoiceNumberPrefix(), "INV-", PREFIX,
                "The invoice number prefix must start with a letter and use only letters, digits and hyphens, up to 8 characters."));
        entity.setDefaultDueDays(clampInt(command.defaultDueDays(), 14, 0, 365, "Payment due in days must be between 0 and 365."));
        entity.setDefaultTaxRate(optionalRate(command.defaultTaxRate()));
        entity.setDefaultPaymentTerms(text(command.defaultPaymentTerms(), MAX_PAYMENT_TERMS, "payment terms"));
        entity.setDefaultNotes(text(command.defaultNotes(), MAX_LONG_TEXT, "invoice notes"));
        entity.setDefaultFooter(text(command.defaultFooter(), MAX_LONG_TEXT, "invoice footer"));
        entity.setDefaultUnit(text(command.defaultUnit(), 24, "default unit"));
        entity.setListPageSize(pageSize(command.listPageSize()));
        entity.setAuditHistoryPeriod(command.auditHistoryPeriod() == null
                ? AuditHistoryPeriod.ONE_YEAR : command.auditHistoryPeriod());
        entity.setExportRangeDays(clampInt(command.exportRangeDays(), 90, 7, 730,
                "The default export period must be between 7 and 730 days."));
        entity.setNotifyExpiryReminders(command.notifyExpiryReminders());
        entity.setNotifyQuotaAlerts(command.notifyQuotaAlerts());
        entity.setNotifyPaymentUpdates(command.notifyPaymentUpdates());
        entity.setNotifySecurityAlerts(command.notifySecurityAlerts());
        entity.setDefaultTemplateId(resolveDefaultTemplate(scope, command.defaultTemplateId()));
        entity.setDefaultCustomTemplateId(resolveDefaultCustomTemplate(scope, command.defaultCustomTemplateId()));
        entity.setDefaultCategoryId(resolveDefaultCategory(scope, command.defaultCategoryId()));

        CustomerSettings saved = settings.save(entity);
        audit.record(AuditEventCommand.success(customerId, "UPDATE", "CUSTOMER_SETTINGS", "Account settings", customerId,
                "Customer updated their own account preferences.", before, describe(saved)));
        return saved;
    }

    // ------------------------------------------------------------------ accessors used by other modules
    /** The prefix an account's invoice numbers start with; the sequence behind them stays central. */
    public String invoiceNumberPrefix(long customerId) {
        CustomerSettings value = find(customerId);
        return value.getInvoiceNumberPrefix() == null || value.getInvoiceNumberPrefix().isBlank()
                ? "INV-" : value.getInvoiceNumberPrefix();
    }

    /** Page size for this account's lists, always one of the supported values. */
    public int pageSize(long customerId) {
        int size = find(customerId).getListPageSize();
        return PAGE_SIZES.contains(size) ? size : 20;
    }

    /** The oldest audit record the account may list or export. */
    public Instant auditWindowStart(long customerId) {
        return find(customerId).auditWindowStart(Instant.now());
    }

    /**
     * Whether a platform message of this kind should still be generated for the account. A lookup
     * failure never suppresses a message: an account must not miss an expiry notice because a
     * preference row could not be read.
     */
    public boolean wantsAlert(long customerId, CustomerAlertTopic topic) {
        try {
            return find(customerId).wants(topic);
        } catch (RuntimeException expected) {
            return true;
        }
    }

    // ------------------------------------------------------------------ validation
    /** A default template has to be one the account is entitled to use right now, not merely a valid id. */
    private Long resolveDefaultTemplate(TenantScope scope, Long templateId) {
        if (templateId == null) return null;
        Set<Long> allowed = subscriptions.accessibleTemplates(scope).stream()
                .map(template -> template.getId()).collect(Collectors.toSet());
        if (!allowed.contains(templateId)) {
            throw new IllegalArgumentException("The default template must be one included in your active plan.");
        }
        return templateId;
    }

    private Long resolveDefaultCustomTemplate(TenantScope scope, Long templateId) {
        if (templateId == null) return null;
        try {
            var template = customTemplates.findById(scope, templateId);
            if (!template.isActive()) {
                throw new IllegalArgumentException("Reactivate the custom template before making it your default.");
            }
            return templateId;
        } catch (ResourceNotFoundException expected) {
            throw new IllegalArgumentException("Choose a custom template you own, or leave the default unset.");
        }
    }

    private Long resolveDefaultCategory(TenantScope scope, Long categoryId) {
        if (categoryId == null) return null;
        try {
            return categories.findById(scope, categoryId).getId();
        } catch (ResourceNotFoundException expected) {
            throw new IllegalArgumentException("Choose a category you own, or leave the default unset.");
        }
    }

    private static String upper(String value, String fallback, Pattern shape, String message) {
        String trimmed = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (trimmed == null || !shape.matcher(trimmed).matches()) throw new IllegalArgumentException(message);
        return trimmed;
    }

    private static int clampInt(Integer value, int fallback, int min, int max, String message) {
        int resolved = value == null ? fallback : value;
        if (resolved < min || resolved > max) throw new IllegalArgumentException(message);
        return resolved;
    }

    private static int pageSize(Integer value) {
        int resolved = value == null ? 20 : value;
        if (!PAGE_SIZES.contains(resolved)) throw new IllegalArgumentException("Choose a page size of 10, 20, 50 or 100 rows.");
        return resolved;
    }

    private static BigDecimal optionalRate(BigDecimal value) {
        if (value == null) return null;
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("The default tax rate must be between 0 and 100 percent.");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String text(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Keep the " + label + " to " + max + " characters or fewer.");
        }
        return trimmed;
    }

    /** A summary for the audit trail: preferences only, never anything secret. */
    private Map<String, Object> describe(CustomerSettings value) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("currency", value.getDefaultCurrency());
        summary.put("invoicePrefix", value.getInvoiceNumberPrefix());
        summary.put("dueDays", value.getDefaultDueDays());
        summary.put("defaultTemplateId", value.getDefaultTemplateId() == null ? "none" : value.getDefaultTemplateId());
        summary.put("defaultCustomTemplateId",
                value.getDefaultCustomTemplateId() == null ? "none" : value.getDefaultCustomTemplateId());
        summary.put("auditHistoryPeriod", value.getAuditHistoryPeriod().getCode());
        summary.put("pageSize", value.getListPageSize());
        summary.put("expiryReminders", value.isNotifyExpiryReminders());
        summary.put("quotaAlerts", value.isNotifyQuotaAlerts());
        summary.put("paymentUpdates", value.isNotifyPaymentUpdates());
        summary.put("securityAlerts", value.isNotifySecurityAlerts());
        summary.put("exportRangeDays", value.getExportRangeDays());
        return summary;
    }
}

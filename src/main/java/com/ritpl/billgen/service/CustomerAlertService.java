package com.ritpl.billgen.service;

import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerAlertTopic;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.NotificationStatus;
import com.ritpl.billgen.entity.SubscriptionNotificationLog;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.SubscriptionNotificationLogRepository;

/**
 * The notices an account can opt into from its own settings: a plan's invoice allowance running low or
 * running out, a payment recorded on one of its invoices, and a change to its credentials.
 *
 * <p>These rows go into the same in-app inbox the subscription reminders use, so there is one place to
 * read them and one mark-read flow. Two rules keep this honest: a row is written only after the event
 * itself has been saved, and a topic the account has switched off writes nothing. The password-change
 * notice is the one case where the preference is ignored, because an account must not be able to hide a
 * credential change from itself - the audit entry for it exists either way.</p>
 */
@Service
public class CustomerAlertService {
    private final SubscriptionNotificationLogRepository notifications;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final CustomerSettingsService settings;
    private final AuditService audit;

    public CustomerAlertService(SubscriptionNotificationLogRepository notifications, CustomerRepository customers,
            InvoiceRepository invoices, CustomerSettingsService settings, AuditService audit) {
        this.notifications = notifications; this.customers = customers; this.invoices = invoices;
        this.settings = settings; this.audit = audit;
    }

    /**
     * Raised after an invoice has been counted against a term. The plan's own warning percentage decides
     * when the first notice appears, and the remaining count is stored in {@code daysBefore} so the
     * unique guard means "one notice per level crossed": issuing fifty invoices cannot produce fifty
     * alerts, and the exhausted notice is never swallowed by an earlier warning.
     */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void quotaAlert(CustomerSubscription term) {
        if (term == null || term.getCustomer() == null || term.getPlan() == null) return;
        Long customerId = term.getCustomerId();
        if (customerId == null) return;
        if (!settings.wantsAlert(customerId, CustomerAlertTopic.QUOTA_ALERT)) return;
        int limit = term.getEffectiveInvoiceLimit();
        if (limit <= 0) return;                                   // an uncapped term has nothing to warn about
        long used = term.getInvoiceUsageCarryForward()
                + invoices.countByUsageSubscription_IdAndUsageCountedTrue(term.getId());
        long remaining = Math.max(0, limit - used);
        int warnAt = Math.max(1, (int) Math.ceil(limit * term.getPlan().getUsageWarningPercent() / 100.0));
        if (remaining > warnAt) return;
        String message = remaining == 0
                ? "Your " + term.getPlan().getName() + " allowance for this term is used up (" + limit
                        + " invoices). New invoices will be refused until the plan is renewed; cancelling an issued invoice does not return its slot."
                : "Only " + remaining + " of your " + limit + " invoices remain for this term ("
                        + term.getPlan().getName() + ", runs to " + term.getEndsOn() + ").";
        save(customerId, term, "QUOTA", (int) Math.min(Integer.MAX_VALUE, remaining), message, true);
    }

    /** A payment notice for the account whose invoice was recorded as paid. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void paymentAlert(Invoice invoice) {
        if (invoice == null || invoice.getCustomer() == null || invoice.getCustomerId() == null) return;
        long customerId = invoice.getCustomerId();
        if (!settings.wantsAlert(customerId, CustomerAlertTopic.PAYMENT_UPDATE)) return;
        String message = "Payment recorded on invoice " + invoice.getInvoiceNumber() + " for "
                + invoice.getCurrency() + " " + invoice.getTotalAmount() + ".";
        // No dedupe key: every invoice that gets paid deserves its own notice.
        save(customerId, invoice.getUsageSubscription(), "PAYMENT", 0, message, false);
    }

    /**
     * A credential-change notice. Delivered whatever the account has chosen, and never deduplicated:
     * every password change deserves its own entry.
     */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void securityAlert(long customerId, String message) {
        save(customerId, null, "SECURITY", 0, message, false);
    }

    /**
     * {@code dedupeKey} is the value that makes a repeat unnecessary for this topic - the remaining
     * invoice count for a quota notice - and {@code deduplicate} is whether it is consulted at all.
     */
    private void save(long customerId, CustomerSubscription term, String type, int dedupeKey, String message,
            boolean deduplicate) {
        Customer customer = customers.findById(customerId).orElse(null);
        if (customer == null) return;
        if (deduplicate && term != null && term.getId() != null
                && notifications.existsBySubscription_IdAndNotificationTypeAndDaysBefore(term.getId(), type, dedupeKey)) {
            return;
        }
        SubscriptionNotificationLog notification = term == null
                ? new SubscriptionNotificationLog(customer, type, dedupeKey, message)
                : new SubscriptionNotificationLog(term, type, dedupeKey, message);
        notification.setStatus(NotificationStatus.DELIVERED);
        notifications.save(notification);
        audit.record(AuditEventCommand.success(customerId, "NOTIFY", "ACCOUNT", "Account alert", notification.getId(),
                "In-app account alert created.", null, Map.of("topic", type, "message", message)).withActor("SYSTEM"));
    }
}

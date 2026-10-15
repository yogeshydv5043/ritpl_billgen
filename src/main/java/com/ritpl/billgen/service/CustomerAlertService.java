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
 * read them and one mark-read flow. Three rules keep this honest: a row is written only after the event
 * itself has been saved; a topic the account has switched off writes nothing; and whether a notice exists
 * at all, and whether it is also emailed, is decided by {@code notification_settings} through
 * {@link PlatformMailService} rather than being hardcoded here. The password-change notice is the one case
 * where the account's own preference is ignored, because an account must not be able to hide a credential
 * change from itself - the setting still decides whether an email goes out alongside it.</p>
 */
@Service
public class CustomerAlertService {
    /** The {@code notification_settings} code for each notice this service can produce. */
    static final String QUOTA_WARNING = "INVOICE_LIMIT_WARNING";
    static final String QUOTA_EXHAUSTED = "INVOICE_LIMIT_REACHED";
    static final String PAYMENT = "PAYMENT_SUCCESS";
    static final String CREDENTIALS = "CREDENTIAL_CHANGED";

    private final SubscriptionNotificationLogRepository notifications;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final CustomerSettingsService settings;
    private final AuditService audit;
    private final PlatformMailService mail;

    public CustomerAlertService(SubscriptionNotificationLogRepository notifications, CustomerRepository customers,
            InvoiceRepository invoices, CustomerSettingsService settings, AuditService audit,
            PlatformMailService mail) {
        this.notifications = notifications; this.customers = customers; this.invoices = invoices;
        this.settings = settings; this.audit = audit; this.mail = mail;
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
        save(customerId, term, "QUOTA", (int) Math.min(Integer.MAX_VALUE, remaining), message, true,
                remaining == 0 ? QUOTA_EXHAUSTED : QUOTA_WARNING,
                "term-" + term.getId() + "-" + remaining, false, "Invoice allowance for this term");
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
        save(customerId, invoice.getUsageSubscription(), "PAYMENT", 0, message, false, PAYMENT,
                "invoice-" + invoice.getId(), false, "Payment recorded");
    }

    /**
     * A credential-change notice. Delivered whatever the account has chosen, and never deduplicated:
     * every password change deserves its own entry.
     */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void securityAlert(long customerId, String message) {
        // forceInApp: the account's own preference and the global notice setting may switch an email off,
        // but a credential change always stays visible in the inbox it happened to.
        save(customerId, null, "SECURITY", 0, message, false, CREDENTIALS,
                "security-" + customerId + "-" + System.currentTimeMillis(), true, "Account credentials changed");
    }

    /**
     * Writes the notice, then asks for its email half. {@code dedupeKey} is the value that makes a repeat
     * unnecessary for this topic - the remaining invoice count for a quota notice - and {@code deduplicate}
     * is whether it is consulted at all; {@code eventKey} is what the email's idempotency key is built from,
     * because "one notice per level crossed" and "one email per event" are different questions.
     *
     * <p>A muted notice returns before either half is written, so switching one off leaves no row behind
     * claiming something was sent, and a notice the account has opted out of never reaches the transport.</p>
     */
    private void save(long customerId, CustomerSubscription term, String type, int dedupeKey, String message,
            boolean deduplicate, String notificationCode, String eventKey, boolean forceInApp, String subject) {
        if (mail.muted(notificationCode)) return;
        Customer customer = customers.findById(customerId).orElse(null);
        if (customer == null) return;
        boolean writtenToInbox = false;
        if (forceInApp || mail.deliversInApp(notificationCode)) {
            if (deduplicate && term != null && term.getId() != null
                    && notifications.existsBySubscription_IdAndNotificationTypeAndDaysBefore(term.getId(), type, dedupeKey)) {
                return;
            }
            SubscriptionNotificationLog notification = term == null
                    ? new SubscriptionNotificationLog(customer, type, dedupeKey, message)
                    : new SubscriptionNotificationLog(term, type, dedupeKey, message);
            notification.setStatus(NotificationStatus.DELIVERED);
            notifications.save(notification);
            audit.record(AuditEventCommand.success(customerId, "NOTIFY", "ACCOUNT", "Account alert",
                    notification.getId(), "In-app account alert created (" + type + ").", null,
                    Map.of("topic", type, "message", message)).withActor("SYSTEM"));
            writtenToInbox = true;
        }
        // The email half guards itself against a repeat by the event key, which is what lets an email-only
        // notice (in-app switched off, or a notice with no inbox row at all) be retried safely.
        mail.email(notificationCode, customer, subject, message, notificationCode + ":" + eventKey);
        if (!writtenToInbox) {
            audit.record(AuditEventCommand.success(customerId, "NOTIFY", "ACCOUNT", "Account alert", null,
                    "Account alert recorded for the " + type + " topic without an inbox entry, because "
                            + "the notice is configured for email only.", null,
                    Map.of("topic", type)).withActor("SYSTEM"));
        }
    }
}

package com.ritpl.billgen.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.NotificationStatus;
import com.ritpl.billgen.entity.SubscriptionNotificationLog;
import com.ritpl.billgen.repository.SubscriptionNotificationLogRepository;
import com.ritpl.billgen.security.TenantScope;

@Service
public class SubscriptionExpiryNotificationService {
    private static final int[] REMINDER_DAYS = {7, 3, 1};
    private final SubscriptionService subscriptions;
    private final SubscriptionNotificationLogRepository notifications;
    private final AuditService audit;
    private final CustomerSettingsService settings;

    public SubscriptionExpiryNotificationService(SubscriptionService subscriptions,
            SubscriptionNotificationLogRepository notifications, AuditService audit, CustomerSettingsService settings) {
        this.subscriptions = subscriptions; this.notifications = notifications; this.audit = audit;
        this.settings = settings;
    }

    /** Daily in-app expiry notifications, deduplicated by subscription and reminder offset. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public int createDailyNotifications() {
        LocalDate today = subscriptions.today(); int created = 0;
        for (int days : REMINDER_DAYS) {
            LocalDate expiry = today.plusDays(days);
            for (var subscription : subscriptions.expiringOnOrBefore(expiry, expiry)) {
                if (notifications.existsBySubscription_IdAndNotificationTypeAndDaysBefore(subscription.getId(), "EXPIRING", days)) continue;
                // Reminders are the one message family an account may opt out of; the expiry notice below
                // is not, because it records an event that has already happened to the subscription.
                if (!settings.wantsAlert(subscription.getCustomerId(), com.ritpl.billgen.entity.CustomerAlertTopic.EXPIRY_REMINDER)) continue;
                String message = "Your " + subscription.getPlan().getName() + " plan expires on " + expiry + ". Renew or choose a new plan to continue creating invoices.";
                SubscriptionNotificationLog notification = new SubscriptionNotificationLog(subscription, "EXPIRING", days, message);
                notification.setStatus(NotificationStatus.DELIVERED);
                notifications.save(notification);
                audit.record(AuditEventCommand.success(subscription.getCustomerId(), "NOTIFY", "SUBSCRIPTION", "Expiry reminder",
                        subscription.getId(), "In-app subscription expiry reminder created.", null,
                        Map.of("daysBeforeExpiry", days, "endsOn", expiry)).withActor("SYSTEM"));
                created++;
            }
        }
        for (var expired : subscriptions.expiredCandidates(today)) {
            if (notifications.existsBySubscription_IdAndNotificationTypeAndDaysBefore(expired.getId(), "EXPIRED", 0)) continue;
            String message = "Your " + expired.getPlan().getName() + " plan expired on " + expired.getEndsOn() + ". Historical invoices remain available; renew or select a plan to create new invoices.";
            SubscriptionNotificationLog notification = new SubscriptionNotificationLog(expired, "EXPIRED", 0, message);
            notification.setStatus(NotificationStatus.DELIVERED);
            notifications.save(notification);
            audit.record(AuditEventCommand.success(expired.getCustomerId(), "NOTIFY", "SUBSCRIPTION", "Expiry notice",
                    expired.getId(), "In-app subscription expiration notice created.", null,
                    Map.of("endsOn", expired.getEndsOn())).withActor("SYSTEM"));
            created++;
        }
        subscriptions.markExpired();
        return created;
    }

    @Transactional
    public List<SubscriptionNotificationLog> findForCustomer(TenantScope scope) {
        long customerId = scope.requireCustomerId();
        return notifications.findTop30ByCustomer_IdOrderByCreatedAtDesc(customerId);
    }

    /** Unread in-app notices for the shell's bell. */
    @Transactional(readOnly = true)
    public long unreadCount(TenantScope scope) {
        long customerId = scope.requireCustomerId();
        return notifications.countByCustomer_IdAndStatusIn(customerId,
                java.util.List.of(NotificationStatus.PENDING, NotificationStatus.DELIVERED));
    }

    @Transactional
    public void markRead(TenantScope scope, long id) {
        long customerId = scope.requireCustomerId();
        SubscriptionNotificationLog notification = notifications.findByIdAndCustomer_Id(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        notification.setStatus(NotificationStatus.READ); notification.setReadAt(java.time.Instant.now());
        audit.record(AuditEventCommand.success(customerId, "READ", "NOTIFICATION", "Subscription notification", id,
                "Customer marked an in-app notification as read.", null, Map.of("status", "READ")));
    }
}

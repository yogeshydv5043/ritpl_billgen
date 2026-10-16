package com.ritpl.billgen.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.NotificationSetting;
import com.ritpl.billgen.entity.NotificationStatus;
import com.ritpl.billgen.entity.SubscriptionNotificationLog;
import com.ritpl.billgen.repository.SubscriptionNotificationLogRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The expiry notices the platform produces on a schedule, and the only place their timing is decided.
 * The ladder, the channels and whether the notice is sent at all come from {@code notification_settings},
 * so switching the reminder off for everybody is a settings change rather than a release - while the
 * per-account choice on the customer's own settings page still applies on top of it, because a notice an
 * account has opted out of must not reappear because an administrator turned the global switch on.
 *
 * <p>Each reminder is written once per subscription and offset, and the email half carries an idempotency
 * key of its own, so re-running the job after a partial failure cannot send a customer the same warning
 * twice. An unreadable or absent settings row falls back to the ladder this job has always used.</p>
 */
@Service
public class SubscriptionExpiryNotificationService {
    /** What the job did before the ladder was configurable, and what it still does with no settings row. */
    private static final int[] BUILT_IN_LADDER = {7, 3, 1};
    private static final String WARNING_CODE = "SUBSCRIPTION_EXPIRY_WARNING";
    private static final String EXPIRED_CODE = "SUBSCRIPTION_EXPIRED";
    private final SubscriptionService subscriptions;
    private final SubscriptionNotificationLogRepository notifications;
    private final AuditService audit;
    private final CustomerSettingsService settings;
    private final NotificationSettingService noticeSettings;
    private final PlatformMailService mail;

    public SubscriptionExpiryNotificationService(SubscriptionService subscriptions,
            SubscriptionNotificationLogRepository notifications, AuditService audit, CustomerSettingsService settings,
            NotificationSettingService noticeSettings, PlatformMailService mail) {
        this.subscriptions = subscriptions; this.notifications = notifications; this.audit = audit;
        this.settings = settings; this.noticeSettings = noticeSettings; this.mail = mail;
    }

    /**
     * The reminder offsets in days before expiry: the configured lead time, then every repeat interval
     * while more than a day remains, and always a final notice the day before. Seven days' notice on a
     * four day interval is therefore reminders at seven, three and one day - the last one is not optional,
     * because a ladder that stops at three would leave the day before expiry unsaid.
     */
    static int[] reminderOffsets(NotificationSetting setting) {
        if (setting == null) return BUILT_IN_LADDER;
        int lead = setting.getTriggerDaysBefore();
        if (lead <= 0) return new int[0];           // "when it happens" is the expiry notice's job
        int step = Math.max(1, setting.getRepeatIntervalDays());
        List<Integer> days = new ArrayList<>();
        for (int offset = lead; offset > 1 && days.size() < 31; offset -= step) days.add(offset);
        days.add(1);
        return days.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Daily expiry notices, deduplicated by subscription and reminder offset. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public int createDailyNotifications() {
        LocalDate today = subscriptions.today(); int created = 0;
        NotificationSetting warning = noticeSettings.find(WARNING_CODE).orElse(null);
        if (warning == null || warning.isActive()) {
            for (int days : reminderOffsets(warning)) {
                LocalDate expiry = today.plusDays(days);
                for (var subscription : subscriptions.expiringOnOrBefore(expiry, expiry)) {
                    if (notifications.existsBySubscription_IdAndNotificationTypeAndDaysBefore(subscription.getId(), "EXPIRING", days)) continue;
                    // Reminders are the one message family an account may opt out of; the expiry notice below
                    // is not, because it records an event that has already happened to the subscription.
                    if (!settings.wantsAlert(subscription.getCustomerId(), com.ritpl.billgen.entity.CustomerAlertTopic.EXPIRY_REMINDER)) continue;
                    String message = "Your " + subscription.getPlan().getName() + " plan expires on " + expiry
                            + ". Renew or choose a new plan to continue creating invoices.";
                    if (warning == null || warning.isInAppEnabled()) {
                        SubscriptionNotificationLog notification =
                                new SubscriptionNotificationLog(subscription, "EXPIRING", days, message);
                        notification.setStatus(NotificationStatus.DELIVERED);
                        notifications.save(notification);
                        audit.record(AuditEventCommand.success(subscription.getCustomerId(), "NOTIFY", "SUBSCRIPTION",
                                "Expiry reminder", subscription.getId(),
                                "Subscription expiry reminder created for " + days + " day(s) before expiry.", null,
                                Map.of("daysBeforeExpiry", days, "endsOn", expiry)).withActor("SYSTEM"));
                    }
                    mail.email(WARNING_CODE, subscription.getCustomer(), "Your plan expires on " + expiry, message,
                            WARNING_CODE + ":" + subscription.getId() + ":" + days);
                    created++;
                }
            }
        }
        NotificationSetting expiryNotice = noticeSettings.find(EXPIRED_CODE).orElse(null);
        if (expiryNotice == null || expiryNotice.isActive()) {
            for (var expired : subscriptions.expiredCandidates(today)) {
                if (notifications.existsBySubscription_IdAndNotificationTypeAndDaysBefore(expired.getId(), "EXPIRED", 0)) continue;
                String message = "Your " + expired.getPlan().getName() + " plan expired on " + expired.getEndsOn()
                        + ". Historical invoices remain available; renew or select a plan to create new invoices.";
                if (expiryNotice == null || expiryNotice.isInAppEnabled()) {
                    SubscriptionNotificationLog notification = new SubscriptionNotificationLog(expired, "EXPIRED", 0, message);
                    notification.setStatus(NotificationStatus.DELIVERED);
                    notifications.save(notification);
                    audit.record(AuditEventCommand.success(expired.getCustomerId(), "NOTIFY", "SUBSCRIPTION",
                            "Expiry notice", expired.getId(), "Subscription expiration notice created.", null,
                            Map.of("endsOn", expired.getEndsOn())).withActor("SYSTEM"));
                }
                mail.email(EXPIRED_CODE, expired.getCustomer(), "Your plan expired on " + expired.getEndsOn(), message,
                        EXPIRED_CODE + ":" + expired.getId());
                created++;
            }
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

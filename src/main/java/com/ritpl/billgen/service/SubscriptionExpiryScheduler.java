package com.ritpl.billgen.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpiryScheduler {
    private final SubscriptionExpiryNotificationService notifications;
    public SubscriptionExpiryScheduler(SubscriptionExpiryNotificationService notifications) { this.notifications = notifications; }

    @Scheduled(cron = "${app.subscription.expiry-reminder-cron:0 0 9 * * *}", zone = "${app.subscription.time-zone:Asia/Kolkata}")
    public void processDailyExpiryNotifications() { notifications.createDailyNotifications(); }
}

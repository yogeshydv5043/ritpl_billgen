package com.ritpl.billgen.api;

import java.time.Instant;
import com.ritpl.billgen.entity.SubscriptionNotificationLog;

public record SubscriptionNotificationResponse(Long id, String type, int daysBefore, String message,
        String status, Instant createdAt, Instant readAt) {
    public static SubscriptionNotificationResponse from(SubscriptionNotificationLog n) {
        return new SubscriptionNotificationResponse(n.getId(), n.getNotificationType(), n.getDaysBefore(),
                n.getMessage(), n.getStatus().name(), n.getCreatedAt(), n.getReadAt());
    }
}

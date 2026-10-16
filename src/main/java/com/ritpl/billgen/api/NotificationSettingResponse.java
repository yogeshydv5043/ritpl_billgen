package com.ritpl.billgen.api;

import java.time.Instant;

import com.ritpl.billgen.entity.ContentStatus;

public record NotificationSettingResponse(Long id, String notificationCode, String notificationName,
        String description, boolean emailEnabled, boolean inAppEnabled, int triggerDaysBefore,
        int repeatIntervalDays, ContentStatus status, boolean deliversAnything, Instant updatedAt) { }

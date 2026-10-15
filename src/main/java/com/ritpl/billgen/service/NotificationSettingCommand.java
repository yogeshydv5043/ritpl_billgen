package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.ContentStatus;

/**
 * How one notice behaves. The code is not in here: it is what the jobs look the row up by, so it is
 * chosen by the application and only its configuration is editable.
 */
public record NotificationSettingCommand(String notificationName, String description, Integer triggerDaysBefore,
        Integer repeatIntervalDays, Boolean emailEnabled, Boolean inAppEnabled, ContentStatus status) {
}

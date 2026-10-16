package com.ritpl.billgen.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** The code is not editable: it is what the background jobs look this row up by. */
public record NotificationSettingRequest(@Size(max = 150) String notificationName,
        @Size(max = 500) String description,
        @Min(0) @Max(90) Integer triggerDaysBefore,
        @Min(0) @Max(365) Integer repeatIntervalDays,
        Boolean emailEnabled, Boolean inAppEnabled,
        com.ritpl.billgen.entity.ContentStatus status) { }

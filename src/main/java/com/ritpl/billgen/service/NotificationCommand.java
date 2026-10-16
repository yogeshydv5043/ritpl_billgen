package com.ritpl.billgen.service;

import java.util.LinkedHashSet;
import java.util.Set;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationRecipientType;
import com.ritpl.billgen.entity.NotificationStatusFilter;
import com.ritpl.billgen.entity.NotificationTriggerEvent;

/** Content and routing of a configurable notification. */
public record NotificationCommand(String name, String title, String body, NotificationChannel channel,
        NotificationTriggerEvent triggerEvent, NotificationRecipientType recipientType,
        NotificationStatusFilter statusFilter, Set<Long> planIds, String scheduleExpression, boolean active) {
    public NotificationCommand {
        planIds = planIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(planIds));
    }
}

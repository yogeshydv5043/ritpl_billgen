package com.ritpl.billgen.service;

import java.time.LocalDateTime;
import java.util.Set;
import com.ritpl.billgen.entity.JobRecurrence;
import com.ritpl.billgen.entity.JobTriggerType;
import com.ritpl.billgen.entity.JobType;

/** Scheduling and delivery settings of one background job. */
public record JobCommand(String name, JobType jobType, String description, Long notificationId,
        JobTriggerType triggerType, LocalDateTime runAt, JobRecurrence recurrence, Set<Long> customerIds,
        int batchSize, int retryLimit, int retryDelayMinutes, int priority, boolean active) {
    public JobCommand {
        customerIds = customerIds == null ? Set.of() : Set.copyOf(customerIds);
        recurrence = recurrence == null ? JobRecurrence.NONE : recurrence;
        triggerType = triggerType == null ? JobTriggerType.MANUAL : triggerType;
    }
}

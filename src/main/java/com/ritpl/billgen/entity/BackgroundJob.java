package com.ritpl.billgen.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A durable unit of background work. The row (not a thread) is the source of truth: a web request
 * only creates the job and its batches, and a worker claims batches inside the database, so bulk
 * email never runs inside a request and survives a restart.
 */
@Entity
@Table(name = "background_jobs")
public class BackgroundJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "job_type", nullable = false, length = 40)
    private JobType jobType = JobType.CUSTOMER_EMAIL;
    @Column(length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 16)
    private JobTriggerType triggerType = JobTriggerType.MANUAL;
    @Enumerated(EnumType.STRING) @Column(name = "recurrence", nullable = false, length = 16)
    private JobRecurrence recurrence = JobRecurrence.NONE;
    @Column(name = "run_at", nullable = false) private Instant runAt = Instant.now();
    /** Comma separated customer ids; empty means "all accounts matching the notification filter". */
    @Column(name = "target_filter", length = 400) private String targetFilter;
    /** Content to render for each recipient; may be null for jobs that do not send messages. */
    @Column(name = "notification_id") private Long notificationId;
    @Column(name = "batch_size", nullable = false) private int batchSize = 100;
    @Column(name = "retry_limit", nullable = false) private int retryLimit = 2;
    @Column(name = "retry_delay_minutes", nullable = false) private int retryDelayMinutes = 15;
    @Column(nullable = false) private int priority = 5;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private JobStatus status = JobStatus.PENDING;
    @Column(name = "eligible_count", nullable = false) private int eligibleCount;
    @Column(name = "batch_count", nullable = false) private int batchCount;
    @Column(name = "processed_count", nullable = false) private int processedCount;
    @Column(name = "success_count", nullable = false) private int successCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "created_by", length = 190) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected BackgroundJob() { }
    public BackgroundJob(String name, JobType jobType, String description) {
        this.name = name; this.jobType = jobType == null ? JobType.CUSTOMER_EMAIL : jobType; this.description = description;
    }
    @PrePersist void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public java.util.List<Long> customerIds() {
        if (targetFilter == null || targetFilter.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(targetFilter.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .filter(v -> v.chars().allMatch(Character::isDigit)).map(Long::valueOf).toList();
    }
    public void addProgress(int processed, int success, int failed) {
        this.processedCount += processed; this.successCount += success; this.failedCount += failed;
    }
    /** Derives the final status from the recorded counts. */
    public JobStatus deriveStatus() {
        if (failedCount == 0) return successCount == 0 && eligibleCount > 0 ? JobStatus.FAILED : JobStatus.COMPLETED;
        return successCount == 0 ? JobStatus.FAILED : JobStatus.PARTIALLY_FAILED;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public JobType getJobType() { return jobType; }
    public void setJobType(JobType v) { jobType = v == null ? JobType.CUSTOMER_EMAIL : v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public JobTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(JobTriggerType v) { triggerType = v == null ? JobTriggerType.MANUAL : v; }
    public JobRecurrence getRecurrence() { return recurrence; }
    public void setRecurrence(JobRecurrence v) { recurrence = v == null ? JobRecurrence.NONE : v; }
    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant v) { runAt = v == null ? Instant.now() : v; }
    public String getTargetFilter() { return targetFilter; }
    public void setTargetFilter(String v) { targetFilter = v; }
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long v) { notificationId = v; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int v) { batchSize = Math.max(1, Math.min(v, 500)); }
    public int getRetryLimit() { return retryLimit; }
    public void setRetryLimit(int v) { retryLimit = Math.max(0, Math.min(v, 10)); }
    public int getRetryDelayMinutes() { return retryDelayMinutes; }
    public void setRetryDelayMinutes(int v) { retryDelayMinutes = Math.max(0, Math.min(v, 1440)); }
    public int getPriority() { return priority; }
    public void setPriority(int v) { priority = Math.max(1, Math.min(v, 9)); }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus v) { status = v == null ? JobStatus.PENDING : v; }
    public int getEligibleCount() { return eligibleCount; }
    public void setEligibleCount(int v) { eligibleCount = Math.max(0, v); }
    public int getBatchCount() { return batchCount; }
    public void setBatchCount(int v) { batchCount = Math.max(0, v); }
    public int getProcessedCount() { return processedCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailedCount() { return failedCount; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { finishedAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    /** 0..100 for the progress bar in the monitoring table. */
    public int getProgressPercent() {
        return eligibleCount <= 0 ? (status == JobStatus.COMPLETED ? 100 : 0)
                : Math.min(100, (int) Math.round(processedCount * 100.0 / eligibleCount));
    }
    @Override public String toString() { return name; }
}

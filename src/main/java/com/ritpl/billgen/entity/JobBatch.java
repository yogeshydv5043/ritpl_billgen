package com.ritpl.billgen.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One slice of a job. A worker claims a batch with {@code FOR UPDATE SKIP LOCKED}, which is what
 * keeps two workers (or a retried request) from processing the same recipients twice.
 */
@Entity
@Table(name = "job_batches")
public class JobBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false)
    private BackgroundJob job;
    @Column(name = "batch_number", nullable = false) private int batchNumber;
    @Column(name = "offset_index", nullable = false) private int offsetIndex;
    @Column(name = "item_count", nullable = false) private int itemCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private JobBatchStatus status = JobBatchStatus.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(name = "success_count", nullable = false) private int successCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected JobBatch() { }
    public JobBatch(BackgroundJob job, int batchNumber, int offsetIndex, int itemCount) {
        this.job = job; this.batchNumber = batchNumber; this.offsetIndex = offsetIndex; this.itemCount = itemCount;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public void start() { this.status = JobBatchStatus.RUNNING; this.attempts++; this.claimedAt = Instant.now(); }
    public void complete(int success, int failed, String error) {
        this.successCount = success; this.failedCount = failed; this.lastError = clip(error);
        this.status = failed > 0 && success == 0 ? JobBatchStatus.FAILED : JobBatchStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }
    private String clip(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 500)); }

    public Long getId() { return id; }
    public BackgroundJob getJob() { return job; }
    public int getBatchNumber() { return batchNumber; }
    public int getOffsetIndex() { return offsetIndex; }
    public int getItemCount() { return itemCount; }
    public void setSuccessCount(int v) { successCount = Math.max(0, v); }
    public void setFailedCount(int v) { failedCount = Math.max(0, v); }
    public void setLastError(String v) { lastError = clip(v); }
    public void setFinishedAt(Instant v) { finishedAt = v; }
    public JobBatchStatus getStatus() { return status; }
    public void setStatus(JobBatchStatus v) { status = v == null ? JobBatchStatus.PENDING : v; }
    public int getAttempts() { return attempts; }
    public int getSuccessCount() { return successCount; }
    public int getFailedCount() { return failedCount; }
    public String getLastError() { return lastError; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

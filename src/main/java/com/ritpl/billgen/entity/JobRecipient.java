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

/** The outcome for one recipient of one job. Retries update this row rather than adding a second send. */
@Entity
@Table(name = "job_recipients")
public class JobRecipient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "batch_id", nullable = false)
    private JobBatch batch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false)
    private BackgroundJob job;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @Column(nullable = false, length = 190) private String recipient;
    @Column(nullable = false, length = 190) private String subject;
    @Column(nullable = false, length = 4000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private JobRecipientStatus status = JobRecipientStatus.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(name = "idempotency_key", nullable = false, length = 255) private String idempotencyKey;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected JobRecipient() { }
    public JobRecipient(JobBatch batch, BackgroundJob job, Customer customer, String recipient,
            String subject, String body, String idempotencyKey) {
        this.batch = batch; this.job = job; this.customer = customer; this.recipient = recipient;
        this.subject = subject; this.body = body; this.idempotencyKey = idempotencyKey;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    /** Re-queues a failed recipient. Its row is reused, so a retry can never double-send. */
    public void resetForRetry() {
        this.status = JobRecipientStatus.PENDING;
        this.lastError = null;
        this.sentAt = null;
    }
    public void skip(String reason) {
        this.status = JobRecipientStatus.SKIPPED;
        this.lastError = reason;
        this.sentAt = java.time.Instant.now();
    }
    public void record(JobRecipientStatus value, String error) {
        this.status = value; this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (value != JobRecipientStatus.PENDING) this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public JobBatch getBatch() { return batch; }
    public BackgroundJob getJob() { return job; }
    public Customer getCustomer() { return customer; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public JobRecipientStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getLastError() { return lastError; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    /** Whether a retry may touch this recipient again. */
    public boolean retryable(int retryLimit) {
        return status == JobRecipientStatus.FAILED && attempts < retryLimit;
    }
}

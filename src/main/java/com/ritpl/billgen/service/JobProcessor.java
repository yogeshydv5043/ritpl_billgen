package com.ritpl.billgen.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.DeliveryStatus;
import com.ritpl.billgen.entity.JobBatch;
import com.ritpl.billgen.entity.JobBatchStatus;
import com.ritpl.billgen.entity.JobRecipient;
import com.ritpl.billgen.entity.JobRecipientStatus;
import com.ritpl.billgen.entity.JobStatus;
import com.ritpl.billgen.entity.JobType;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationTriggerEvent;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.repository.BackgroundJobRepository;
import com.ritpl.billgen.repository.JobBatchRepository;
import com.ritpl.billgen.repository.JobRecipientRepository;

/**
 * The worker behind Job Management. Every run takes a bounded number of batches, each in its own
 * transaction, claiming rows with {@code FOR UPDATE SKIP LOCKED} so several instances can share the
 * queue and a restart only loses the batch that was in flight.
 *
 * <p>Nothing here is driven by a web request: the administrator only ever writes a job row.</p>
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class JobProcessor {
    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);

    private final TransactionTemplate transaction;
    private final JobBatchRepository batches;
    private final JobRecipientRepository recipients;
    private final BackgroundJobRepository jobs;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptions;
    private final JobService jobQueue;
    private final AuditTypeService auditTypes;
    private final AuditService audit;
    private final int batchesPerPoll;

    public JobProcessor(PlatformTransactionManager transactionManager, JobBatchRepository batches,
            JobRecipientRepository recipients, BackgroundJobRepository jobs,
            NotificationService notificationService, SubscriptionService subscriptions, JobService jobQueue,
            AuditTypeService auditTypes, AuditService audit,
            @Value("${app.jobs.batches-per-poll:5}") int batchesPerPoll) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.batches = batches; this.recipients = recipients; this.jobs = jobs;
        this.notificationService = notificationService; this.subscriptions = subscriptions; this.jobQueue = jobQueue;
        this.auditTypes = auditTypes; this.audit = audit;
        this.batchesPerPoll = Math.max(1, Math.min(batchesPerPoll, 50));
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-interval-ms:15000}", initialDelayString = "${app.jobs.initial-delay-ms:12000}")
    public void drain() {
        for (int i = 0; i < batchesPerPoll; i++) {
            Boolean worked = transaction.execute(status -> processOneBatch());
            if (worked == null || !worked) return;
        }
    }

    /** Executes the next runnable batch. Returns false when the queue is empty. */
    private boolean processOneBatch() {
        List<JobBatch> claimed = batches.claimPending(1);
        if (claimed.isEmpty()) return false;
        JobBatch batch = claimed.get(0);
        BackgroundJob job = batch.getJob();
        batch.start();
        batches.save(batch);
        if (job.getStatus() != JobStatus.RUNNING) {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(job.getStartedAt() == null ? Instant.now() : job.getStartedAt());
            jobs.save(job);
        }
        if (job.getJobType() == JobType.AUDIT_RETENTION_PURGE) {
            executeRetentionPurge(job, batch);
            return true;
        }
        Notification notification = notificationService.findLiveById(job.getNotificationId()).orElse(null);
        if (notification == null) {
            failBatch(job, batch, "The notification content for this job no longer exists.");
            return true;
        }
        if (!notification.isActive()) {
            failBatch(job, batch, "The notification is deactivated, so nothing was sent.");
            return true;
        }
        int success = 0;
        int failed = 0;
        int skipped = 0;
        String lastError = null;
        EmailSink sink = notificationService.transport();
        for (JobRecipient recipient : recipients.findByBatch_IdOrderByIdAsc(batch.getId())) {
            if (recipient.getStatus().isSuccessful()) { skipped++; continue; }   // never re-send
            if (recipient.getStatus() == JobRecipientStatus.SKIPPED) { skipped++; continue; }
            try {
                String subject = render(notification.getTitle(), recipient, job);
                String body = render(notification.getBody(), recipient, job);
                DeliveryStatus status = sink != null && sink.isLive() ? DeliveryStatus.SENT : DeliveryStatus.SIMULATED;
                if (sink != null && sink.isLive() && notification.sendsEmail()) {
                    sink.send(recipient.getRecipient(), subject, body);
                }
                notificationService.recordRendered(notification,
                        notification.sendsInApp() ? NotificationChannel.IN_APP : NotificationChannel.EMAIL,
                        recipient.getCustomer(), recipient.getRecipient(), subject, body,
                        "job:" + job.getId() + ":" + recipient.getIdempotencyKey(), status, job.getId());
                recipient.record(JobRecipientStatus.valueOf(status.name()), null);
                recipients.save(recipient);
                success++;
            } catch (Exception e) {
                lastError = e.getMessage();
                recipient.record(JobRecipientStatus.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
                recipients.save(recipient);
                failed++;
            }
        }
        boolean retryAvailable = failed > 0 && batch.getAttempts() <= job.getRetryLimit();
        batch.setSuccessCount(success + skipped);
        batch.setFailedCount(failed);
        batch.setLastError(lastError);
        job.addProgress(success + failed, success, failed);
        if (retryAvailable) {
            // Re-queued for another pass once the job's retry delay has elapsed.
            batch.setStatus(JobBatchStatus.PENDING);
            batch.setFinishedAt(Instant.now());
        } else {
            batch.complete(success + skipped, failed, lastError);
        }
        batches.save(batch);
        jobs.save(job);
        if (!retryAvailable) finalizeIfDone(job);
        return true;
    }

    private void executeRetentionPurge(BackgroundJob job, JobBatch batch) {
        try {
            Map<String, Integer> removed = auditTypes.purgeExpiredHistory();
            int total = removed.getOrDefault("TOTAL", 0);
            batch.setSuccessCount(1);
            batch.setFailedCount(0);
            batch.complete(1, 0, "Removed " + total + " expired audit record(s).");
            job.addProgress(1, 1, 0);
            job.setEligibleCount(1);
        } catch (RuntimeException e) {
            failBatch(job, batch, "Retention purge failed: " + e.getMessage());
            return;
        }
        batches.save(batch);
        jobs.save(job);
        finalizeIfDone(job);
    }

    private void failBatch(BackgroundJob job, JobBatch batch, String reason) {
        batch.complete(0, Math.max(1, batch.getItemCount()), reason);
        batches.save(batch);
        job.addProgress(Math.max(1, batch.getItemCount()), 0, Math.max(1, batch.getItemCount()));
        jobs.save(job);
        finalizeIfDone(job);
    }

    /** Renders at send time, so an account that changed after scheduling still reads its own data. */
    private String render(String template, JobRecipient recipient, BackgroundJob job) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        var customer = recipient.getCustomer();
        if (customer != null) {
            var term = subscriptions.findActiveSubscription(TenantScope.ofCustomer(customer.getId()), customer.getId());
            values.putAll(RecipientResolver.values(customer, term.orElse(null)));
            UsageQuota quota = subscriptions.usage(TenantScope.ofCustomer(customer.getId()), customer.getId());
            values.put("invoiceUsed", String.valueOf(quota.invoicesUsed()));
            values.put("remainingLimit", String.valueOf(quota.remainingInvoices()));
        }
        values.put("jobName", job.getName());
        return NotificationPlaceholders.render(template, values);
    }

    private void finalizeIfDone(BackgroundJob job) {
        long pending = batches.countByJob_IdAndStatus(job.getId(), JobBatchStatus.PENDING)
                + batches.countByJob_IdAndStatus(job.getId(), JobBatchStatus.RUNNING);
        if (pending > 0) { jobs.save(job); return; }
        JobStatus derived = job.deriveStatus();
        job.setStatus(derived);
        job.setFinishedAt(Instant.now());
        jobs.save(job);
        audit.record(AuditEventCommand.success(null, "COMPLETE", "JOB", "Background job", job.getId(),
                        "A background job finished.", null,
                        Map.of("status", derived.name(), "processed", job.getProcessedCount(),
                                "success", job.getSuccessCount(), "failed", job.getFailedCount()))
                .withActor("SYSTEM"));
        if ((derived == JobStatus.FAILED || derived == JobStatus.PARTIALLY_FAILED) && job.getNotificationId() != null) {
            log.warn("Job {} finished as {} ({} failed recipient(s))", job.getName(), derived, job.getFailedCount());
        }
        // A recurring job is re-armed for its next window: batches and recipient rows return to
        // PENDING so each window sends again, while the per-recipient key keeps re-delivery bounded.
        if (derived == JobStatus.COMPLETED) jobQueue.rescheduleForNextRun(job);
    }

    /** Manual entry point used by tests and by the "run one batch" administrative action. */
    public boolean processNextBatchForTests() {
        Boolean worked = transaction.execute(status -> processOneBatch());
        return worked != null && worked;
    }
}

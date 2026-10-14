package com.ritpl.billgen.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.Customer;
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
            if (!processNextBatch()) return;
        }
    }

    /**
     * Claims and runs one batch, then reports whether there was anything to run. Every entry point -
     * the poll, a test, an administrator running a single batch - comes through here, so a failure is
     * always accounted for the same way.
     */
    public boolean processNextBatch() {
        AtomicLong claimed = new AtomicLong();
        Long worked;
        try {
            worked = transaction.execute(status -> processOneBatch(claimed));
        } catch (RuntimeException unexpected) {
            recover(claimed.get(), unexpected);
            return true;        // the batch was either re-queued or failed, so keep draining
        }
        return worked != null;
    }

    /**
     * A batch that throws for an unexpected reason must not stay PENDING forever: that turns one bad
     * row into an error in the log every poll, for as long as the process lives. The claim is charged
     * an attempt in a transaction of its own (the failed one is already rolled back, including its
     * {@code start()}) and the batch is re-paced for another try or marked failed. When nothing is
     * runnable any more the job is finalised, so a broken job ends visibly instead of spinning.
     */
    private void recover(long batchId, RuntimeException failure) {
        if (batchId == 0L) {
            log.error("The job queue could not claim work; the next poll will try again", failure);
            return;
        }
        try {
            transaction.execute(status -> {
                JobBatch batch = batches.findById(batchId).orElse(null);
                if (batch == null || batch.getStatus() != JobBatchStatus.RUNNING) return null;
                BackgroundJob job = batch.getJob();
                int attempts = batch.getAttempts() + 1;
                batch.setAttempts(attempts);
                batch.setLastError(clip(failure));
                if (attempts > job.getRetryLimit()) {
                    batch.setStatus(JobBatchStatus.FAILED);
                } else {
                    batch.setStatus(JobBatchStatus.PENDING);
                }
                batch.setFinishedAt(Instant.now());     // this is what makes the retry wait out the delay
                batches.save(batch);
                recomputeProgress(job);
                jobs.save(job);
                finalizeIfDone(job);
                return null;
            });
        } catch (RuntimeException recoveryFailure) {
            // If the database itself is unreachable there is nothing to record; the next poll retries.
            log.error("Batch {} could not be accounted for after a failure", batchId, recoveryFailure);
            return;
        }
        log.error("Background job batch {} failed and was re-queued or marked failed", batchId, failure);
    }

    private String clip(Throwable failure) {
        String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /** Claims the next runnable batch and processes it; returns null when the queue is empty. */
    private Long processOneBatch(AtomicLong claimed) {
        List<JobBatch> found = batches.claimPending(1);
        if (found.isEmpty()) return null;
        JobBatch batch = found.get(0);
        claimed.set(batch.getId());               // recorded before anything can fail, so recovery knows the row
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
            return batch.getId();
        }
        Notification notification = notificationService.findLiveById(job.getNotificationId()).orElse(null);
        if (notification == null) {
            failBatch(job, batch, "The notification content for this job no longer exists.");
            return batch.getId();
        }
        if (!notification.isActive()) {
            failBatch(job, batch, "The notification is deactivated, so nothing was sent.");
            return batch.getId();
        }
        int success = 0;
        int failed = 0;
        int skipped = 0;
        String lastError = null;
        EmailSink sink = notificationService.transport();
        for (JobRecipient recipient : recipients.findByBatch_IdOrderByIdAsc(batch.getId())) {
            if (recipient.getStatus().isSuccessful()) { skipped++; continue; }   // never re-send
            Customer account = recipient.getCustomer();
            if (account == null || account.isDeleted()) {
                // The audience was chosen when the job was created. An account that has been deleted
                // since is recorded as skipped rather than mailed: a queued job must not reach it.
                recipient.record(JobRecipientStatus.SKIPPED,
                        "The account was deleted before this job ran, so nothing was sent.");
                recipients.save(recipient);
                skipped++;
                continue;
            }
            String subject;
            String message;
            try {
                subject = render(notification.getTitle(), recipient, job);
                message = render(notification.getBody(), recipient, job);
            } catch (RuntimeException renderFailure) {
                lastError = clip(renderFailure);
                recipient.record(JobRecipientStatus.FAILED, lastError);
                recipients.save(recipient);
                failed++;
                continue;
            }
            DeliveryStatus outcome = DeliveryStatus.SENT;
            String failure = null;
            // One history row per channel the notification uses, so an "in-app and email" message
            // cannot report the in-app row as if it were the email delivery.
            if (notification.sendsInApp()) {
                notificationService.recordRendered(notification, NotificationChannel.IN_APP, account,
                        recipient.getRecipient(), subject, message, deliveryKey(job, recipient, "IN_APP"),
                        DeliveryStatus.SENT, null, job.getId());
            }
            if (notification.sendsEmail()) {
                DeliveryStatus emailStatus;
                if (sink != null && sink.isLive()) {
                    try {
                        sink.send(recipient.getRecipient(), subject, message);
                        emailStatus = DeliveryStatus.SENT;
                    } catch (Exception sendFailure) {
                        emailStatus = DeliveryStatus.FAILED;
                        failure = sendFailure.getClass().getSimpleName() + ": " + sendFailure.getMessage();
                    }
                } else {
                    // Nothing is claimed as delivered that only reached the log.
                    emailStatus = DeliveryStatus.SIMULATED;
                }
                notificationService.recordRendered(notification, NotificationChannel.EMAIL, account,
                        recipient.getRecipient(), subject, message, deliveryKey(job, recipient, "EMAIL"),
                        emailStatus, failure, job.getId());
                outcome = emailStatus;
            }
            recipient.record(JobRecipientStatus.valueOf(outcome.name()), failure);
            recipients.save(recipient);
            if (outcome == DeliveryStatus.FAILED) { failed++; lastError = failure; } else { success++; }
        }
        boolean retryAvailable = failed > 0 && batch.getAttempts() <= job.getRetryLimit();
        batch.setSuccessCount(success + skipped);
        batch.setFailedCount(failed);
        batch.setLastError(lastError);
        if (retryAvailable) {
            // Re-queued for another pass once the job's retry delay has elapsed.
            batch.setStatus(JobBatchStatus.PENDING);
            batch.setFinishedAt(Instant.now());
        } else {
            batch.complete(success + skipped, failed, lastError);
        }
        batches.save(batch);
        recomputeProgress(job);
        jobs.save(job);
        if (!retryAvailable) finalizeIfDone(job);
        return batch.getId();
    }

    /** A key per job, recipient and channel, so a retry updates the history row instead of duplicating it. */
    private String deliveryKey(BackgroundJob job, JobRecipient recipient, String channel) {
        return "job:" + job.getId() + ":" + channel + ":" + recipient.getIdempotencyKey();
    }

    /**
     * Progress is recounted from the recipient rows, which are the unit of work and are never
     * duplicated, so a retried batch reports one outcome per customer rather than one per attempt.
     */
    private void recomputeProgress(BackgroundJob job) {
        if (job.getJobType() == JobType.AUDIT_RETENTION_PURGE) return;      // a purge has no recipients
        long sent = recipients.countByJob_IdAndStatus(job.getId(), JobRecipientStatus.SENT);
        long simulated = recipients.countByJob_IdAndStatus(job.getId(), JobRecipientStatus.SIMULATED);
        long skipped = recipients.countByJob_IdAndStatus(job.getId(), JobRecipientStatus.SKIPPED);
        long failed = recipients.countByJob_IdAndStatus(job.getId(), JobRecipientStatus.FAILED);
        job.setSuccessCount((int) (sent + simulated + skipped));
        job.setFailedCount((int) failed);
        job.setProcessedCount((int) (sent + simulated + skipped + failed));
    }

    private void executeRetentionPurge(BackgroundJob job, JobBatch batch) {
        try {
            Map<String, Integer> removed = auditTypes.purgeExpiredHistory();
            int total = removed.getOrDefault("TOTAL", 0);
            batch.setSuccessCount(1);
            batch.setFailedCount(0);
            batch.complete(1, 0, "Removed " + total + " expired audit record(s).");
            job.setEligibleCount(1);
            job.addProgress(1, 1, 0);      // one unit of work per purge run, there are no recipients
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
        recomputeProgress(job);
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
        // A batch that failed before reaching its recipients leaves no failed recipient rows, so the
        // batch outcome has to be consulted as well or the job would read as if it had succeeded.
        if (batches.countByJob_IdAndStatus(job.getId(), JobBatchStatus.FAILED) > 0
                && derived == JobStatus.COMPLETED) {
            derived = job.getSuccessCount() > 0 ? JobStatus.PARTIALLY_FAILED : JobStatus.FAILED;
        }
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

}

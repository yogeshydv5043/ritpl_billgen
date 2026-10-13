package com.ritpl.billgen.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.JobBatch;
import com.ritpl.billgen.entity.JobBatchStatus;
import com.ritpl.billgen.entity.JobRecipient;
import com.ritpl.billgen.entity.JobRecipientStatus;
import com.ritpl.billgen.entity.JobRecurrence;
import com.ritpl.billgen.entity.JobStatus;
import com.ritpl.billgen.entity.JobTriggerType;
import com.ritpl.billgen.entity.JobType;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.repository.BackgroundJobRepository;
import com.ritpl.billgen.repository.JobBatchRepository;
import com.ritpl.billgen.repository.JobRecipientRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Job administration: what an administrator does with a job (create, pause, resume, cancel, retry,
 * monitor). Execution itself lives in {@link JobProcessor}, driven by the database, never by a
 * request thread.
 */
@Service
public class JobService {
    private static final int MAX_PLAN_ROWS = 20000;

    private final BackgroundJobRepository jobs;
    private final JobBatchRepository batches;
    private final JobRecipientRepository recipients;
    private final NotificationService notifications;
    private final RecipientResolver resolver;
    private final AuditService audit;

    public JobService(BackgroundJobRepository jobs, JobBatchRepository batches, JobRecipientRepository recipients,
            NotificationService notifications, RecipientResolver resolver, AuditService audit) {
        this.jobs = jobs; this.batches = batches; this.recipients = recipients; this.notifications = notifications;
        this.resolver = resolver; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<BackgroundJob> search(JobStatus status, String search, Pageable pageable) {
        return jobs.search(status, search == null || search.isBlank() ? null : search.trim(), null,
                pageable == null ? PageRequest.of(0, 20) : pageable);
    }

    @Transactional(readOnly = true)
    public BackgroundJob findById(long id) {
        return jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found."));
    }

    @Transactional(readOnly = true)
    public List<JobBatch> batches(long jobId) { return batches.findByJob_IdOrderByBatchNumberAsc(jobId); }

    @Transactional(readOnly = true)
    public Page<JobRecipient> recipients(long jobId, JobRecipientStatus status, Pageable pageable) {
        return recipients.findForJob(jobId, status, pageable == null ? PageRequest.of(0, 50) : pageable);
    }

    /** The six counters of the job monitoring dashboard. */
    @Transactional(readOnly = true)
    public Map<String, Long> monitoring() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", jobs.count());
        counts.put("running", jobs.countByStatus(JobStatus.RUNNING));
        counts.put("scheduled", jobs.countByStatus(JobStatus.SCHEDULED) + jobs.countByStatus(JobStatus.PENDING));
        counts.put("completed", jobs.countByStatus(JobStatus.COMPLETED));
        counts.put("failed", jobs.countByStatus(JobStatus.FAILED) + jobs.countByStatus(JobStatus.PARTIALLY_FAILED));
        counts.put("paused", jobs.countByStatus(JobStatus.PAUSED));
        return counts;
    }

    /** What creating this job would address, shown before it is committed. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(JobCommand command) {
        Notification notification = notifications.findLiveById(command.notificationId()).orElse(null);
        List<RecipientResolver.Recipient> eligible = resolver.eligible(notification, new ArrayList<>(command.customerIds()));
        int batchCount = eligible.isEmpty() ? 0 : (int) Math.ceil(eligible.size() / (double) Math.max(1, command.batchSize()));
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("eligible", eligible.size());
        preview.put("batchSize", Math.max(1, command.batchSize()));
        preview.put("batches", batchCount);
        return preview;
    }

    /**
     * Creates the job plus its batch and recipient rows. The web request stops here: the batches
     * are claimed later by the worker, one transaction at a time.
     */
    @Transactional
    public BackgroundJob create(TenantScope scope, JobCommand command, String actor) {
        requireAdmin(scope);
        validate(command);
        Notification notification = notifications.findLiveById(command.notificationId())
                .orElseThrow(() -> new IllegalArgumentException("Choose the notification content this job should send."));
        List<RecipientResolver.Recipient> eligible = resolver.eligible(notification, new ArrayList<>(command.customerIds()));
        if (eligible.size() > MAX_PLAN_ROWS) {
            throw new IllegalArgumentException("This job addresses " + eligible.size() + " accounts, which exceeds the "
                    + MAX_PLAN_ROWS + " row planning limit. Narrow the filters or split the job.");
        }
        BackgroundJob job = new BackgroundJob(command.name().trim(), command.jobType(), blankToNull(command.description()));
        job.setTriggerType(command.triggerType());
        job.setRecurrence(command.recurrence());
        job.setRunAt(command.runAt() == null ? Instant.now() : command.runAt().toInstant(ZoneOffset.UTC));
        job.setNotificationId(notification.getId());
        job.setBatchSize(command.batchSize());
        job.setRetryLimit(command.retryLimit());
        job.setRetryDelayMinutes(command.retryDelayMinutes());
        job.setPriority(command.priority());
        job.setTargetFilter(command.customerIds().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
        job.setCreatedBy(actor);
        job.setEligibleCount(eligible.size());
        int batchCount = Math.max(1, (int) Math.ceil(eligible.size() / (double) job.getBatchSize()));
        job.setBatchCount(batchCount);
        job.setStatus(command.triggerType() == JobTriggerType.SCHEDULED && job.getRunAt().isAfter(Instant.now())
                ? JobStatus.SCHEDULED : JobStatus.PENDING);
        BackgroundJob saved = jobs.save(job);

        int number = 0;
        List<JobBatch> planned = new ArrayList<>();
        for (int offset = 0; offset < eligible.size(); offset += saved.getBatchSize()) {
            JobBatch batch = new JobBatch(saved, ++number, offset, Math.min(saved.getBatchSize(), eligible.size() - offset));
            planned.add(batches.save(batch));
        }
        if (planned.isEmpty()) planned.add(batches.save(new JobBatch(saved, 1, 0, 0)));
        int index = 0;
        for (RecipientResolver.Recipient recipient : eligible) {
            JobBatch batch = planned.get(Math.min(index / Math.max(1, saved.getBatchSize()), planned.size() - 1));
            recipients.save(new JobRecipient(batch, saved, recipient.customer(), recipient.address(),
                    safe(notification.getTitle(), 190), NotificationPlaceholders.render(notification.getBody(), recipient.values()),
                    "cust:" + recipient.customer().getId()));
            index++;
        }
        audit.record(AuditEventCommand.success(null, "CREATE", "JOB", "Background job", saved.getId(),
                "Administrator scheduled a background job.", null,
                Map.of("jobType", saved.getJobType().name(), "eligible", saved.getEligibleCount(),
                        "batches", saved.getBatchCount(), "notification", notification.getName(),
                        "trigger", saved.getTriggerType().name(), "runAt", saved.getRunAt().toString())));
        return saved;
    }

    /** "Run now" only unblocks the job; the worker still does the sending. */
    @Transactional
    public BackgroundJob runNow(TenantScope scope, long id, String actor) {
        requireAdmin(scope);
        BackgroundJob job = findById(id);
        if (job.getStatus() == JobStatus.RUNNING) throw new IllegalArgumentException("This job is already running.");
        if (job.getStatus() == JobStatus.CANCELLED) throw new IllegalArgumentException("A cancelled job cannot be run again.");
        if (job.getEligibleCount() == 0) throw new IllegalArgumentException("This job has no eligible recipients.");
        job.setStatus(JobStatus.RUNNING);
        job.setRunAt(Instant.now());
        job.setStartedAt(Instant.now());
        transitionBatches(id, JobBatchStatus.CANCELLED, JobBatchStatus.PENDING);
        audit.record(AuditEventCommand.success(null, "RUN", "JOB", "Background job", id,
                "Administrator started a background job.", null, Map.of("status", "RUNNING", "actor", actor)));
        return job;
    }

    @Transactional
    public BackgroundJob pause(TenantScope scope, long id, String actor) {
        requireAdmin(scope);
        BackgroundJob job = findById(id);
        if (!job.getStatus().isWorkPending()) throw new IllegalArgumentException("This job has no work left to pause.");
        job.setStatus(JobStatus.PAUSED);
        transitionBatches(id, JobBatchStatus.PENDING, JobBatchStatus.PAUSED);
        audit.record(AuditEventCommand.success(null, "PAUSE", "JOB", "Background job", id,
                "Administrator paused a background job.", null, Map.of("actor", actor)));
        return job;
    }

    @Transactional
    public BackgroundJob resume(TenantScope scope, long id, String actor) {
        requireAdmin(scope);
        BackgroundJob job = findById(id);
        if (job.getStatus() != JobStatus.PAUSED) throw new IllegalArgumentException("Only a paused job can be resumed.");
        job.setStatus(JobStatus.RUNNING);
        transitionBatches(id, JobBatchStatus.PAUSED, JobBatchStatus.PENDING);
        audit.record(AuditEventCommand.success(null, "RESUME", "JOB", "Background job", id,
                "Administrator resumed a background job.", null, Map.of("actor", actor)));
        return job;
    }

    @Transactional
    public BackgroundJob cancel(TenantScope scope, long id, boolean confirmed, String actor) {
        requireAdmin(scope);
        BackgroundJob job = findById(id);
        if (!job.getStatus().isWorkPending()) throw new IllegalArgumentException("This job cannot be cancelled.");
        // The UI asks twice; the server independently refuses an unconfirmed cancel of a messaging job.
        if (!confirmed && job.getJobType().sendsCustomerCommunication()) {
            throw new IllegalArgumentException("Cancelling stops delivery to customers. Tick the confirmation to continue.");
        }
        job.setStatus(JobStatus.CANCELLED);
        job.setFinishedAt(Instant.now());
        transitionBatches(id, JobBatchStatus.PENDING, JobBatchStatus.CANCELLED);
        transitionBatches(id, JobBatchStatus.PAUSED, JobBatchStatus.CANCELLED);
        audit.record(AuditEventCommand.success(null, "CANCEL", "JOB", "Background job", id,
                "Administrator cancelled a background job; undelivered recipients were marked cancelled.", null,
                Map.of("processed", job.getProcessedCount(), "remaining", job.getEligibleCount() - job.getProcessedCount(),
                        "actor", actor)));
        return job;
    }

    /**
     * Re-queues recipients that failed with attempts below the retry limit. The existing recipient
     * rows are returned to PENDING inside their own batch, so a retry can never hand a customer a
     * second copy of a message they already received, and per-recipient uniqueness still holds.
     */
    @Transactional
    public int retryFailed(TenantScope scope, long id, String actor) {
        requireAdmin(scope);
        BackgroundJob job = findById(id);
        if (job.getStatus() == JobStatus.RUNNING) throw new IllegalArgumentException("This job is still running.");
        int requeued = 0;
        for (JobBatch batch : batches.findByJob_IdOrderByBatchNumberAsc(id)) {
            List<JobRecipient> rows = recipients.findByBatch_IdOrderByIdAsc(batch.getId());
            List<JobRecipient> eligible = rows.stream()
                    .filter(r -> r.getStatus() == JobRecipientStatus.FAILED && r.getAttempts() <= job.getRetryLimit())
                    .toList();
            if (eligible.isEmpty()) continue;
            for (JobRecipient row : eligible) {
                row.resetForRetry();
                recipients.save(row);
            }
            batch.setStatus(JobBatchStatus.PENDING);
            batch.setSuccessCount((int) rows.stream().filter(r -> r.getStatus().isSuccessful()).count());
            batch.setFailedCount(0);
            batch.setLastError(null);
            batch.setFinishedAt(null);
            batches.save(batch);
            requeued += eligible.size();
        }
        if (requeued == 0) {
            throw new IllegalArgumentException("There is nothing left to retry. Either every recipient was reached, or "
                    + "the retry limit of " + job.getRetryLimit() + " attempt(s) has been used.");
        }
        job.setStatus(JobStatus.RUNNING);
        job.setFinishedAt(null);
        job.setFailedCount(Math.max(0, job.getFailedCount() - requeued));
        job.setProcessedCount(Math.max(0, job.getProcessedCount() - requeued));
        jobs.save(job);
        audit.record(AuditEventCommand.success(null, "RETRY", "JOB", "Background job", id,
                "Administrator retried the failed recipients of a job.",
                Map.of("failed", job.getFailedCount()), Map.of("requeued", requeued, "actor", actor)));
        return requeued;
    }

    /** Re-arms a finished recurring job for its next window. Called by the worker, not by a request. */
    @Transactional
    public void rescheduleForNextRun(BackgroundJob job) {
        if (job.getRecurrence() == null || job.getRecurrence() == JobRecurrence.NONE) return;
        Instant next = switch (job.getRecurrence()) {
            case DAILY -> job.getRunAt().plusSeconds(86400);
            case WEEKLY -> job.getRunAt().plusSeconds(604800);
            case MONTHLY -> job.getRunAt().plus(30, java.time.temporal.ChronoUnit.DAYS);
            case NONE -> job.getRunAt();
        };
        job.setRunAt(next.isAfter(Instant.now()) ? next : Instant.now().plusSeconds(3600));
        job.setStatus(JobStatus.SCHEDULED);
        job.setProcessedCount(0); job.setSuccessCount(0); job.setFailedCount(0);
        job.setStartedAt(null); job.setFinishedAt(null);
        for (JobBatch batch : batches.findByJob_IdOrderByBatchNumberAsc(job.getId())) {
            for (JobRecipient row : recipients.findByBatch_IdOrderByIdAsc(batch.getId())) {
                row.resetForRetry();
                recipients.save(row);
            }
            batch.setStatus(JobBatchStatus.PENDING);
            batch.setAttempts(0);
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setFinishedAt(null);
            batches.save(batch);
        }
        jobs.save(job);
    }

    private void transitionBatches(long jobId, JobBatchStatus from, JobBatchStatus to) {
        for (JobBatch batch : batches.findByJob_IdOrderByBatchNumberAsc(jobId)) {
            if (batch.getStatus() == from) {
                batch.setStatus(to);
                batches.save(batch);
            }
        }
    }

    private void validate(JobCommand command) {
        if (command == null) throw new IllegalArgumentException("Enter the job details.");
        if (command.name() == null || command.name().isBlank() || command.name().trim().length() > 120) {
            throw new IllegalArgumentException("Enter a job name of at most 120 characters.");
        }
        if (command.jobType() == null) throw new IllegalArgumentException("Choose a job type.");
        if (command.jobType() != JobType.AUDIT_RETENTION_PURGE && command.notificationId() == null) {
            throw new IllegalArgumentException("Choose the notification content this job should send.");
        }
        if (command.batchSize() < 1 || command.batchSize() > 500) {
            throw new IllegalArgumentException("A batch must contain between 1 and 500 recipients.");
        }
        if (command.retryLimit() < 0 || command.retryLimit() > 10) {
            throw new IllegalArgumentException("A retry limit must be between 0 and 10.");
        }
        if (command.retryDelayMinutes() < 0 || command.retryDelayMinutes() > 1440) {
            throw new IllegalArgumentException("A retry delay must be between 0 and 1440 minutes.");
        }
        if (command.triggerType() == JobTriggerType.SCHEDULED) {
            LocalDateTime runAt = command.runAt();
            if (runAt == null) throw new IllegalArgumentException("Choose when the scheduled job should run.");
            if (runAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
                throw new IllegalArgumentException("A scheduled run time cannot be in the past.");
            }
        }
        Notification notification = notifications.findLiveById(command.notificationId()).orElse(null);
        if (notification != null && !notification.isActive()) {
            throw new IllegalArgumentException("Activate the notification before using it in a job.");
        }
    }

    private String safe(String value, int max) {
        if (value == null || value.isBlank()) return "BillGen notification";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void requireAdmin(TenantScope scope) {
        if (scope == null || !scope.isAdminScope()) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        }
    }
}

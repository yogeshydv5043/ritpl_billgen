package com.ritpl.billgen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.JobBatch;
import com.ritpl.billgen.entity.JobRecurrence;
import com.ritpl.billgen.entity.JobTriggerType;
import com.ritpl.billgen.entity.JobType;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationRecipientType;
import com.ritpl.billgen.entity.NotificationStatusFilter;
import com.ritpl.billgen.entity.NotificationTriggerEvent;
import com.ritpl.billgen.repository.JobBatchRepository;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomerAccountCommand;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.JobCommand;
import com.ritpl.billgen.service.JobProcessor;
import com.ritpl.billgen.service.JobService;
import com.ritpl.billgen.service.NotificationCommand;
import com.ritpl.billgen.service.NotificationService;

/**
 * Bulk sending runs outside a web request, so the queue's claims, ordering and retry pacing are
 * proven here against a real PostgreSQL container rather than against mocks: the claiming rule is
 * a single SQL statement, and a mock would happily accept one that the database rejects.
 *
 * <p>The worker bean is present so its claim path can be driven by hand, but its poll and initial
 * delay are pushed out by an hour: a background drain would otherwise consume a batch between the
 * claim and the assertion. Every test runs in a transaction that is rolled back, which also gives
 * the {@code for update skip locked} statement the read-write transaction it needs — a read-only
 * one is rejected by PostgreSQL. Skipped when Docker is unavailable.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.demo.seed-enabled=false",
        "app.jobs.enabled=true",
        // The scheduled drain must not race the assertions; each test drives the worker itself.
        "app.jobs.poll-interval-ms=3600000",
        "app.jobs.initial-delay-ms=3600000",
        "spring.session.store-type=none",
        "spring.cache.type=none",
        "app.subscription.time-zone=UTC"
})
class JobQueueIntegrationTest {

    private static final int BATCH_SIZE = 1;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billgen_jobs_it")
            .withUsername("billgen")
            .withPassword("billgen_it_only");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired CustomerService customerService;
    @Autowired NotificationService notifications;
    @Autowired JobService jobs;
    @Autowired JobBatchRepository batches;
    @Autowired JobProcessor worker;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void recipientsArePlannedIntoBatchesAtCreationAndNothingIsClaimedUntilTheJobIsStarted() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long first = newCustomer("jobs-a-" + suffix + "@demo.test", "Alpha " + suffix).getId();
        long second = newCustomer("jobs-b-" + suffix + "@demo.test", "Beta " + suffix).getId();
        BackgroundJob job = newJob(newNotification(suffix), suffix, 5, 0, Set.of(first, second));

        List<JobBatch> planned = batches.findByJob_IdOrderByBatchNumberAsc(job.getId());
        assertEquals(2, planned.size(), "two accounts at a batch size of one become two batches");
        assertEquals(List.of(1, 2), planned.stream().map(JobBatch::getBatchNumber).toList());
        assertEquals(0, planned.stream().map(JobBatch::getItemCount).filter(n -> n != 1).count(),
                "each batch records the one recipient it carries");
        assertEquals(2, job.getEligibleCount(), "the job records the audience it planned for");
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from job_recipients where job_id = ?", Integer.class, job.getId()).intValue());
        assertEquals(2, jdbc.queryForObject(
                "select count(distinct customer_id) from job_recipients where job_id = ?",
                Integer.class, job.getId()).intValue(), "one row per eligible account, so a retry cannot duplicate a send");

        assertTrue(claimFor(job).isEmpty(), "a job nobody started offers no work to the worker");

        jobs.runNow(TenantScope.administrator(), job.getId(), "tester");
        assertEquals(List.of(planned.get(0).getId(), planned.get(1).getId()), claimFor(job),
                "once started, batches are offered in creation order");
    }

    @Test
    @Transactional
    void aFailedBatchStaysPutUntilTheJobsRetryDelayHasElapsed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long customerId = newCustomer("jobs-retry-" + suffix + "@demo.test", "Retrying " + suffix).getId();
        BackgroundJob job = newJob(newNotification(suffix), suffix, 5, 30, Set.of(customerId));
        JobBatch batch = batches.findByJob_IdOrderByBatchNumberAsc(job.getId()).get(0);
        jobs.runNow(TenantScope.administrator(), job.getId(), "tester");

        // The worker marks a batch it could not finish PENDING again with the failure time recorded,
        // which is what paces the retry; simulate a batch that just failed.
        jdbc.update("update job_batches set status = 'PENDING', finished_at = now() where id = ?", batch.getId());
        assertTrue(claimFor(job).isEmpty(), "a batch that failed moments ago waits for the retry delay");

        jdbc.update("update job_batches set finished_at = now() - interval '31 minutes' where id = ?", batch.getId());
        assertEquals(List.of(batch.getId()), claimFor(job), "once the delay has elapsed the same batch is runnable");
    }

    @Test
    @Transactional
    void theHighestPriorityJobIsClaimedFirst() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long customerId = newCustomer("jobs-prio-" + suffix + "@demo.test", "Priority " + suffix).getId();
        BackgroundJob ordinary = newJob(newNotification("ordinary-" + suffix), "ordinary-" + suffix, 3, 0, Set.of(customerId));
        BackgroundJob urgent = newJob(newNotification("urgent-" + suffix), "urgent-" + suffix, 9, 0, Set.of(customerId));
        jobs.runNow(TenantScope.administrator(), ordinary.getId(), "tester");
        jobs.runNow(TenantScope.administrator(), urgent.getId(), "tester");

        List<Long> firstClaim = batches.claimPending(1).stream().map(JobBatch::getId).toList();
        assertEquals(1, firstClaim.size(), "the limit is honoured, so a worker takes one batch per transaction");
        assertEquals(List.of(firstBatchId(urgent)), firstClaim, "priority decides who goes first");
        assertFalse(firstClaim.contains(firstBatchId(ordinary)), "the lower priority job waits its turn");
    }

    @Test
    @Transactional
    void aPausedJobStopsOfferingWorkWithoutDiscardingIt() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long customerId = newCustomer("jobs-pause-" + suffix + "@demo.test", "Paused " + suffix).getId();
        BackgroundJob job = newJob(newNotification(suffix), suffix, 5, 0, Set.of(customerId));
        jobs.runNow(TenantScope.administrator(), job.getId(), "tester");
        assertFalse(claimFor(job).isEmpty(), "a running job offers its batches");

        jobs.pause(TenantScope.administrator(), job.getId(), "tester");
        assertTrue(claimFor(job).isEmpty(), "pausing stops further work without losing the queue");
        assertEquals(1, jdbc.queryForObject("select count(*) from job_batches where job_id = ? and status = 'PAUSED'",
                Integer.class, job.getId()).intValue(), "the batch is parked, not discarded: nothing was sent and nothing was deleted");

        jobs.resume(TenantScope.administrator(), job.getId(), "tester");
        assertFalse(claimFor(job).isEmpty(), "resuming makes the same batches runnable again");
    }


    @Test
    @Transactional
    void oneBatchIsSentPerTransactionAndEveryRecipientGetsItsOwnDeliveryRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long first = newCustomer("jobs-run-a-" + suffix + "@demo.test", "Runner Alpha " + suffix).getId();
        long second = newCustomer("jobs-run-b-" + suffix + "@demo.test", "Runner Beta " + suffix).getId();
        BackgroundJob job = newJob(newNotification(suffix), suffix, 5, 0, Set.of(first, second));
        jobs.runNow(TenantScope.administrator(), job.getId(), "tester");

        assertTrue(worker.processNextBatch(), "the worker takes the first batch");
        assertEquals(1, scalar("processed_count", job),
                "one batch of one account is counted in the database, not the whole audience kept in memory");
        assertEquals(1, count("select count(*) from notification_deliveries where job_id = ?", job),
                "the recipient has its own delivery row");
        assertEquals("SIMULATED", jdbc.queryForObject(
                "select status from notification_deliveries where job_id = ?", String.class, job.getId()),
                "with no mail transport configured the send is recorded as simulated, not claimed as delivered");
        assertEquals(1, count("select count(*) from job_recipients where job_id = ? and status <> 'PENDING'", job));

        assertTrue(worker.processNextBatch(), "the second batch is still runnable");
        assertEquals(2, scalar("processed_count", job));
        assertEquals(2, scalar("success_count", job));
        assertFalse(worker.processNextBatch(), "an empty queue reports that there was nothing to do");
        assertEquals("COMPLETED", jdbc.queryForObject("select status from background_jobs where id = ?",
                String.class, job.getId()), "the job status is derived from its finished batches");
        assertEquals(0, count("select count(*) from job_recipients where job_id = ? and status = 'FAILED'", job),
                "nothing failed, so no retry was scheduled");
    }

    // --------------------------------------------------------------------------- helpers

    /** The claim under test, narrowed to this job so other tests' rows cannot disturb it. */
    private List<Long> claimFor(BackgroundJob job) {
        Set<Long> mine = Set.copyOf(firstBatchIds(job));
        return batches.claimPending(50).stream().map(JobBatch::getId).filter(mine::contains).toList();
    }

    private List<Long> firstBatchIds(BackgroundJob job) {
        return jdbc.queryForList("select id from job_batches where job_id = ? order by batch_number asc",
                Long.class, job.getId());
    }

    private Long firstBatchId(BackgroundJob job) { return firstBatchIds(job).get(0); }
    /** Reads a job's persisted column, so an assertion proves the write and not the in-memory copy. */
    private int scalar(String column, BackgroundJob job) {
        return jdbc.queryForObject("select " + column + " from background_jobs where id = ?",
                Integer.class, job.getId());
    }

    private int count(String sql, BackgroundJob job) {
        Integer found = jdbc.queryForObject(sql, Integer.class, job.getId());
        return found == null ? 0 : found;
    }


    private BackgroundJob newJob(Notification content, String suffix, int priority, int retryDelayMinutes,
            Set<Long> customerIds) {
        return jobs.create(TenantScope.administrator(), new JobCommand("Bulk mail " + suffix,
                JobType.CUSTOMER_EMAIL, "Integration test run", content.getId(), JobTriggerType.MANUAL, null,
                JobRecurrence.NONE, customerIds, BATCH_SIZE, 2, retryDelayMinutes, priority, true), "tester");
    }

    private Notification newNotification(String suffix) {
        return notifications.save(TenantScope.administrator(), null, new NotificationCommand(
                "Queue notice " + suffix, "Hello {{customerName}}",
                "Your account at {{companyName}} is active.", NotificationChannel.EMAIL,
                NotificationTriggerEvent.MANUAL, NotificationRecipientType.CUSTOMERS,
                NotificationStatusFilter.ALL, Set.of(), null, true));
    }

    private Customer newCustomer(String email, String companyName) {
        return customerService.createAccount(TenantScope.administrator(), new CustomerAccountCommand(companyName,
                "Contact " + companyName, email, "+91 90000 00000", "1 Market Road", "Ludhiana", "Punjab",
                "141001", "India", null, email, "Str0ngPass!23", Boolean.TRUE));
    }
}

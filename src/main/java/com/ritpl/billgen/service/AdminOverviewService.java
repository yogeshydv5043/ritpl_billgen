package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.entity.AuditRecord;
import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.JobStatus;
import com.ritpl.billgen.repository.AuditRecordRepository;
import com.ritpl.billgen.repository.BackgroundJobRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.NotificationRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;

/**
 * Everything the Super Admin dashboard shows. Counts are aggregate queries against the live
 * database - never a hard-coded number and never a sample row presented as production data.
 */
@Service
public class AdminOverviewService {
    private static final int EXPIRY_WARNING_DAYS = 30;
    private static final int PERIOD_DAYS = 30;

    private final CustomerRepository customers;
    private final CustomerSubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final BackgroundJobRepository jobs;
    private final NotificationRepository notifications;
    private final AuditRecordRepository auditRecords;
    private final SubscriptionService subscriptionService;

    public AdminOverviewService(CustomerRepository customers, CustomerSubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans, BackgroundJobRepository jobs, NotificationRepository notifications,
            AuditRecordRepository auditRecords, SubscriptionService subscriptionService) {
        this.customers = customers; this.subscriptions = subscriptions; this.plans = plans; this.jobs = jobs;
        this.notifications = notifications; this.auditRecords = auditRecords; this.subscriptionService = subscriptionService;
    }

    /** One KPI card: count, previous-period comparison and the filtered list it links to. */
    public record Kpi(String key, String label, long value, Long previousValue, String href, String icon, String tone) {
        public Long delta() { return previousValue == null ? null : value - previousValue; }
    }

    /** One row of the "Top 10 new customers" table: only the columns the dashboard shows. */
    public record NewCustomer(long customerId, String companyName, String contactName, String email,
            LocalDateTime registeredOn, String accountStatus, String planName, LocalDate expiry, boolean deleted) { }

    public record Overview(List<Kpi> kpis, List<NewCustomer> newCustomers,
            List<CustomerSubscription> recentSubscriptions, List<Map<String, Object>> upcomingExpiries,
            List<AuditRecord> recentActivity, List<BackgroundJob> attentionJobs,
            List<Map<String, Object>> configurationChanges, Map<String, Long> jobCounts) { }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "dashboard", key = "'admin-overview'")
    public Overview build() { return load(); }

    /** Kept separate so tests can exercise the assembly without the cache proxy. */
    @Transactional(readOnly = true)
    public Overview load() {
        LocalDate today = subscriptionService.today();
        LocalDateTime periodStart = today.minusDays(PERIOD_DAYS).atStartOfDay();
        LocalDateTime previousStart = today.minusDays(PERIOD_DAYS * 2L).atStartOfDay();

        long total = customers.countBySystemAccountFalseAndDeletedAtIsNull();
        long active = customers.countBySystemAccountFalseAndDeletedAtIsNullAndStatus(AccountStatus.ACTIVE);
        long disabled = customers.countBySystemAccountFalseAndDeletedAtIsNullAndStatus(AccountStatus.DISABLED);
        long withoutSubscription = customers.countWithoutAnySubscription();
        long subscribed = subscriptions.countAccountsSubscribed(com.ritpl.billgen.entity.SubscriptionStatus.ACTIVE);
        long expiringSoon = customers.countExpiringBetween(today, today.plusDays(EXPIRY_WARNING_DAYS));

        List<Kpi> kpis = List.of(
                new Kpi("total", "Total customers", total, null, "/customers", "◉", "neutral"),
                new Kpi("active", "Active customers", active,
                        customers.countBySystemAccountFalseAndDeletedAtIsNullAndStatusAndCreatedAtBetween(AccountStatus.ACTIVE, previousStart, periodStart),
                        "/customers?status=ACTIVE", "✓", "positive"),
                new Kpi("disabled", "Disabled customers", disabled,
                        customers.countBySystemAccountFalseAndDeletedAtIsNullAndStatusAndCreatedAtBetween(AccountStatus.DISABLED, previousStart, periodStart),
                        "/customers?status=DISABLED", "⊘", "muted"),
                new Kpi("no-subscription", "Without a subscription", withoutSubscription, null,
                        "/customers?subscribed=false", "◇", "warning"),
                new Kpi("subscribed", "With a current subscription", subscribed, null,
                        "/customers?subscribed=true", "◆", "positive"),
                new Kpi("expiring", "Subscriptions expiring in 30 days", expiringSoon, null,
                        "/customers?expiringSoon=true", "◷", "danger"));

        List<NewCustomer> newCustomers = customers.findTop10BySystemAccountFalseAndDeletedAtIsNullOrderByCreatedAtDesc()
                .stream().map(this::toRow).toList();

        List<CustomerSubscription> recentSubscriptions = subscriptions.findTop10ByOrderByCreatedAtDesc();

        List<Map<String, Object>> upcomingExpiries = new java.util.ArrayList<>();
        for (CustomerSubscription subscription : subscriptions.findByStatusAndEndsOnBetween(
                com.ritpl.billgen.entity.SubscriptionStatus.ACTIVE, today, today.plusDays(EXPIRY_WARNING_DAYS))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subscription", subscription);
            row.put("customer", subscription.getCustomer());
            row.put("daysRemaining", java.time.temporal.ChronoUnit.DAYS.between(today, subscription.getEndsOn()));
            row.put("amount", subscription.getPlan() == null ? BigDecimal.ZERO : subscription.getPlan().getMonthlyPrice());
            row.put("currency", subscription.getPlan() == null ? "INR" : subscription.getPlan().getCurrency());
            upcomingExpiries.add(row);
        }

        List<AuditRecord> recentActivity = auditRecords.search(null, null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 8)).getContent();

        List<BackgroundJob> attentionJobs = java.util.stream.Stream
                .concat(jobs.findByStatusOrderByPriorityDescRunAtAsc(JobStatus.RUNNING).stream(),
                        jobs.findByStatusOrderByPriorityDescRunAtAsc(JobStatus.PENDING).stream())
                .limit(6).toList();

        List<Map<String, Object>> configurationChanges = new java.util.ArrayList<>();
        for (AuditRecord record : auditRecords.search(null, null, null, "TEMPLATE", null,
                Instant.now().minus(PERIOD_DAYS, java.time.temporal.ChronoUnit.DAYS), null,
                org.springframework.data.domain.PageRequest.of(0, 4)).getContent()) {
            configurationChanges.add(activityRow(record));
        }
        for (AuditRecord record : auditRecords.search(null, null, null, "NOTIFICATION_CONFIG", null,
                Instant.now().minus(PERIOD_DAYS, java.time.temporal.ChronoUnit.DAYS), null,
                org.springframework.data.domain.PageRequest.of(0, 2)).getContent()) {
            configurationChanges.add(activityRow(record));
        }

        Map<String, Long> jobCounts = new LinkedHashMap<>();
        jobCounts.put("total", jobs.count());
        jobCounts.put("running", jobs.countByStatus(JobStatus.RUNNING));
        jobCounts.put("scheduled", jobs.countByStatus(JobStatus.SCHEDULED) + jobs.countByStatus(JobStatus.PENDING));
        jobCounts.put("completed", jobs.countByStatus(JobStatus.COMPLETED));
        jobCounts.put("failed", jobs.countByStatus(JobStatus.FAILED) + jobs.countByStatus(JobStatus.PARTIALLY_FAILED));
        jobCounts.put("paused", jobs.countByStatus(JobStatus.PAUSED));
        jobCounts.put("notifications", notifications.countByActiveTrueAndDeletedAtIsNull());
        jobCounts.put("plans", plans.countByActiveTrueAndDeletedAtIsNull());

        return new Overview(kpis, newCustomers, recentSubscriptions, upcomingExpiries, recentActivity,
                attentionJobs, configurationChanges, jobCounts);
    }

    private Map<String, Object> activityRow(AuditRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("record", record);
        row.put("when", record.getCreatedAt() == null ? "" : java.time.Duration.between(record.getCreatedAt(), Instant.now()).toMinutes());
        return row;
    }

    /** The dashboard row for one account: identity plus the current term and plan. */
    private NewCustomer toRow(com.ritpl.billgen.entity.Customer customer) {
        var term = subscriptions.findFirstByCustomer_IdAndStatus(customer.getId(),
                com.ritpl.billgen.entity.SubscriptionStatus.ACTIVE);
        return new NewCustomer(customer.getId(), customer.getCompanyName(), customer.getContactName(), customer.getEmail(),
                customer.getCreatedAt(), customer.getStatus().getLabel(),
                term.map(t -> t.getPlan().getName()).orElse(null),
                term.map(CustomerSubscription::getEndsOn).orElse(null), customer.isDeleted());
    }
}

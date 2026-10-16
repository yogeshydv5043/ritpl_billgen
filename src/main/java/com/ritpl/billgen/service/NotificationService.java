package com.ritpl.billgen.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.DeliveryStatus;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationDelivery;
import com.ritpl.billgen.entity.NotificationTriggerEvent;
import com.ritpl.billgen.repository.NotificationDeliveryRepository;
import com.ritpl.billgen.repository.NotificationRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Dynamic notification management: the Super Admin owns the content, channel, recipients and
 * activation of every message the application sends, so wording changes do not need a release.
 * This service renders and records deliveries; actually posting email is the transport's job.
 */
@Service
public class NotificationService {
    private static final int MAX_BODY = 4000;
    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final List<EmailSink> emailSinks;
    private final AuditService audit;

    public NotificationService(NotificationRepository notifications, NotificationDeliveryRepository deliveries,
            List<EmailSink> emailSinks, AuditService audit) {
        this.notifications = notifications; this.deliveries = deliveries; this.emailSinks = emailSinks; this.audit = audit;
    }

    /** What the UI says about the mail transport; never throws when no sink bean is present. */
    public String transportLabel() {
        EmailSink sink = transport();
        return sink == null ? "No mail transport configured" : sink.describe();
    }

    /** The transport used for email: SMTP when a live one is configured, otherwise just recording. */
    public EmailSink transport() {
        return emailSinks.stream().filter(EmailSink::isLive).findFirst()
                .orElseGet(() -> emailSinks.isEmpty() ? null : emailSinks.get(0));
    }

    @Transactional(readOnly = true)
    public List<Notification> findAll() { return notifications.findByDeletedAtIsNullOrderByUpdatedAtDesc(); }

    @Transactional(readOnly = true)
    public List<Notification> findActive() { return notifications.findByActiveTrueAndDeletedAtIsNull(); }

    /**
     * Lookup that tolerates a retired or missing rule: background work must be able to report the
     * problem instead of failing while resolving the row.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Notification> findLiveById(Long id) {
        return id == null ? java.util.Optional.empty() : notifications.findById(id).filter(n -> !n.isDeleted());
    }

    @Transactional(readOnly = true)
    public Notification findById(long id) {
        return notifications.findById(id).filter(n -> !n.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
    }

    @Transactional(readOnly = true)
    public Page<NotificationDelivery> history(TenantScope scope, Long customerId, String trigger,
            DeliveryStatus status, Pageable pageable) {
        Long tenant = scope.isAdminScope() ? customerId : scope.requireCustomerId();
        if (!scope.isAdminScope()) scope.requireCustomerAccess(customerId);
        return deliveries.search(tenant, blankToNull(trigger), status, null,
                pageable == null ? PageRequest.of(0, 50) : pageable);
    }

    @Transactional(readOnly = true)
    public List<NotificationDelivery> historyForNotification(long notificationId) {
        return deliveries.findTop50ByNotification_IdOrderByCreatedAtDesc(notificationId);
    }

    @Transactional(readOnly = true)
    public List<NotificationDelivery> recentForCustomer(long customerId) {
        return deliveries.findTop50ByCustomer_IdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> deliveryCounts() {
        return Map.of("PENDING", deliveries.countByStatus(DeliveryStatus.PENDING),
                "SENT", deliveries.countByStatus(DeliveryStatus.SENT),
                "SIMULATED", deliveries.countByStatus(DeliveryStatus.SIMULATED),
                "FAILED", deliveries.countByStatus(DeliveryStatus.FAILED),
                "SKIPPED", deliveries.countByStatus(DeliveryStatus.SKIPPED));
    }

    @Transactional
    public Notification save(TenantScope scope, Long id, NotificationCommand command) {
        requireAdmin(scope);
        validate(command, id);
        Notification notification = id == null ? new Notification(command.name().trim(), command.title().trim(),
                command.body().trim(), command.channel(), command.triggerEvent()) : findById(id);
        Map<String, Object> before = id == null ? null
                : Map.of("title", notification.getTitle(), "active", notification.isActive(),
                        "channel", notification.getChannel().name());
        notification.setName(command.name().trim());
        notification.setTitle(command.title().trim());
        notification.setBody(command.body().trim());
        notification.setChannel(command.channel());
        notification.setTriggerEvent(command.triggerEvent());
        notification.setRecipientType(command.recipientType());
        notification.setStatusFilter(command.statusFilter());
        notification.setPlanFilter(joinIds(command.planIds()));
        notification.setScheduleExpression(blankToNull(command.scheduleExpression()));
        // Activation is the gate that matters: a notification with unknown placeholders is never sent.
        if (command.active()) validatePlaceholders(notification);
        notification.setActive(command.active());
        if (notification.getCreatedBy() == null) notification.setCreatedBy(actorOf(scope));
        Notification saved = notifications.save(notification);
        audit.record(AuditEventCommand.success(null, id == null ? "CREATE" : "UPDATE", "NOTIFICATION_CONFIG",
                "Notification", saved.getId(),
                id == null ? "Administrator created a dynamic notification." : "Administrator updated a dynamic notification.",
                before, Map.of("name", saved.getName(), "active", saved.isActive(), "channel", saved.getChannel().name(),
                        "trigger", saved.getTriggerEvent().name())));
        return saved;
    }

    @Transactional
    public Notification setActive(TenantScope scope, long id, boolean active) {
        requireAdmin(scope);
        Notification notification = findById(id);
        if (active) validatePlaceholders(notification);
        boolean before = notification.isActive();
        notification.setActive(active);
        audit.record(AuditEventCommand.success(null, active ? "ACTIVATE" : "DEACTIVATE", "NOTIFICATION_CONFIG",
                "Notification", id, active ? "Administrator activated a dynamic notification."
                        : "Administrator deactivated a dynamic notification.",
                Map.of("active", before), Map.of("active", active)));
        return notification;
    }

    /** Soft deletion: delivery history for an old message stays queryable. */
    @Transactional
    public void delete(TenantScope scope, long id) {
        requireAdmin(scope);
        Notification notification = findById(id);
        notification.setDeletedAt(Instant.now());
        notification.setActive(false);
        audit.record(AuditEventCommand.success(null, "DELETE", "NOTIFICATION_CONFIG", "Notification", id,
                "Administrator deleted a dynamic notification; its delivery history was preserved.",
                Map.of("name", notification.getName()), Map.of("deleted", true)));
    }

    /**
     * Fires every active notification bound to a trigger for one account. In-app deliveries are
     * always recorded; an email delivery is only recorded when the account has a login email.
     *
     * @param periodToken makes the send idempotent per day (or per job run), so a retried
     *                    scheduler pass cannot deliver the same message twice
     * @return the number of delivery rows created
     */
    @Transactional
    public int fire(NotificationTriggerEvent trigger, Customer customer, Long planId, Map<String, String> values,
            String periodToken) {
        if (trigger == null || customer == null) return 0;
        List<Notification> matching = notifications.findByTriggerEventAndActiveTrueAndDeletedAtIsNull(trigger);
        int created = 0;
        for (Notification notification : matching) {
            if (!notification.includesPlan(planId)) continue;
            if (notification.getRecipientType() == com.ritpl.billgen.entity.NotificationRecipientType.ADMINS) continue;
            if (!passesStatusFilter(notification, customer)) continue;
            String title = clip(NotificationPlaceholders.render(notification.getTitle(), values), 190);
            String body = clip(NotificationPlaceholders.render(notification.getBody(), values), MAX_BODY);
            String token = periodToken == null ? LocalDate.now().toString() : periodToken;
            if (notification.sendsInApp()) created += record(notification, NotificationChannel.IN_APP, customer,
                    recipientFor(customer), title, body, "IN_APP:" + notification.getId() + ":" + customer.getId() + ":" + token);
            if (notification.sendsEmail() && recipientFor(customer) != null) {
                created += recordEmail(notification, customer, title, body,
                        "EMAIL:" + notification.getId() + ":" + customer.getId() + ":" + token);
            }
        }
        return created;
    }

    /**
     * Records a delivery a job has already rendered; the job owns the actual transport attempt, so the
     * outcome and the reason for a failure are passed in rather than invented here. Re-running a batch
     * updates the same row - the idempotency key is per job, account and channel - so retrying a job
     * cannot leave two history rows for one customer.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public NotificationDelivery recordRendered(Notification notification, NotificationChannel channel, Customer customer,
            String recipient, String title, String body, String idempotencyKey, DeliveryStatus status,
            String failureDetail, Long jobId) {
        NotificationDelivery delivery = deliveries.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (delivery == null) {
            delivery = new NotificationDelivery(notification, channel, customer, recipient, title, body, idempotencyKey);
        }
        delivery.setJobId(jobId);
        delivery.mark(status, status == DeliveryStatus.FAILED
                ? (failureDetail == null || failureDetail.isBlank() ? "Transport reported a failure." : failureDetail)
                : null);
        return deliveries.save(delivery);
    }

    /** In-app delivery: the message is in the account's inbox as soon as the row exists. */
    private int record(Notification notification, NotificationChannel channel, Customer customer, String recipient,
            String title, String body, String idempotencyKey) {
        if (deliveries.findByIdempotencyKey(idempotencyKey).isPresent()) return 0;
        NotificationDelivery delivery = new NotificationDelivery(notification, channel, customer, recipient, title, body, idempotencyKey);
        delivery.mark(DeliveryStatus.SENT, null);
        deliveries.save(delivery);
        touch(notification);
        return 1;
    }

    /**
     * Email delivery goes through the configured transport. When no transport is configured the
     * delivery is recorded as SIMULATED - the history stays true to what actually happened.
     */
    private int recordEmail(Notification notification, Customer customer, String title, String body, String idempotencyKey) {
        if (deliveries.findByIdempotencyKey(idempotencyKey).isPresent()) return 0;
        NotificationDelivery delivery = new NotificationDelivery(notification, NotificationChannel.EMAIL, customer,
                recipientFor(customer), title, body, idempotencyKey);
        EmailSink sink = transport();
        if (sink == null || !sink.isLive()) {
            delivery.mark(DeliveryStatus.SIMULATED, null);
        } else {
            try {
                sink.send(delivery.getRecipient(), title, body);
                delivery.mark(DeliveryStatus.SENT, null);
            } catch (Exception e) {
                delivery.mark(DeliveryStatus.FAILED, e.getMessage());
            }
        }
        deliveries.save(delivery);
        touch(notification);
        return 1;
    }

    private void touch(Notification notification) {
        notification.setLastRunAt(Instant.now());
        notifications.save(notification);
    }

    private void validate(NotificationCommand command, Long id) {
        if (command == null) throw new IllegalArgumentException("Enter the notification details.");
        if (command.name() == null || command.name().isBlank() || command.name().trim().length() > 120) {
            throw new IllegalArgumentException("Enter a notification name of at most 120 characters.");
        }
        boolean duplicate = id == null ? notifications.existsByNameIgnoreCase(command.name().trim())
                : notifications.existsByNameIgnoreCaseAndIdNot(command.name().trim(), id);
        if (duplicate) throw new IllegalArgumentException("A notification with this name already exists.");
        if (command.title() == null || command.title().isBlank() || command.title().trim().length() > 190) {
            throw new IllegalArgumentException("Enter a title of at most 190 characters.");
        }
        if (command.body() == null || command.body().isBlank() || command.body().trim().length() > MAX_BODY) {
            throw new IllegalArgumentException("Enter a message body of at most " + MAX_BODY + " characters.");
        }
        if (command.channel() == null) throw new IllegalArgumentException("Choose a delivery channel.");
        if (command.triggerEvent() == null) throw new IllegalArgumentException("Choose the event that triggers this notification.");
        if (command.scheduleExpression() != null && !command.scheduleExpression().isBlank()
                && !command.scheduleExpression().trim().matches("[0-9A-Za-z?*,:/\\-]+")) {
            throw new IllegalArgumentException("A schedule may only contain a cron expression.");
        }
    }

    private void validatePlaceholders(Notification notification) {
        Set<String> problems = new LinkedHashSet<>();
        problems.addAll(NotificationPlaceholders.invalid(notification.getTitle()));
        problems.addAll(NotificationPlaceholders.invalid(notification.getBody()));
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Unknown placeholder(s) " + String.join(", ", problems)
                    + ". Available: " + String.join(", ", NotificationPlaceholders.SUGGESTED));
        }
    }

    private boolean passesStatusFilter(Notification notification, Customer customer) {
        return switch (notification.getStatusFilter() == null
                ? com.ritpl.billgen.entity.NotificationStatusFilter.ACTIVE_ONLY : notification.getStatusFilter()) {
            case ALL -> true;
            case ACTIVE_ONLY -> customer.isActiveAccount() && !customer.isDeleted();
            case DISABLED_ONLY -> !customer.isActiveAccount();
            // Subscription presence is decided by the caller for bulk sends; single-account
            // firing already knows the account, so these filters only exclude deleted rows.
            case WITH_SUBSCRIPTION, WITHOUT_SUBSCRIPTION -> !customer.isDeleted();
        };
    }

    private String recipientFor(Customer customer) { return customer.getEmail(); }

    private String joinIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(java.util.Objects::nonNull).map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String actorOf(TenantScope scope) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "ADMIN" : auth.getName();
    }

    private void requireAdmin(TenantScope scope) {
        if (scope == null || !scope.isAdminScope()) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String clip(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }

    public static List<NotificationTriggerEvent> triggers() { return List.of(NotificationTriggerEvent.values()); }
    public static Set<String> allowedPlaceholders() { return new LinkedHashSet<>(NotificationPlaceholders.SUGGESTED); }
}

package com.ritpl.billgen.entity;

import java.time.Instant;
import java.time.LocalDateTime;
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
 * An administrator-defined message and the event that fires it. Content lives here rather than in
 * code so that wording, recipients and channel can change without a deployment; the trigger points
 * at a place in the application that raises events.
 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(nullable = false, length = 190) private String title;
    @Column(nullable = false, length = 4000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private NotificationChannel channel = NotificationChannel.IN_APP;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_event", nullable = false, length = 48)
    private NotificationTriggerEvent triggerEvent = NotificationTriggerEvent.MANUAL;
    @Enumerated(EnumType.STRING) @Column(name = "recipient_type", nullable = false, length = 16)
    private NotificationRecipientType recipientType = NotificationRecipientType.CUSTOMERS;
    @Enumerated(EnumType.STRING) @Column(name = "status_filter", nullable = false, length = 24)
    private NotificationStatusFilter statusFilter = NotificationStatusFilter.ACTIVE_ONLY;
    /** Comma separated plan ids. Empty means "every plan". */
    @Column(name = "plan_filter", length = 400) private String planFilter;
    /** Informational schedule for a notification fired by a job (cron expression). */
    @Column(name = "schedule_expression", length = 64) private String scheduleExpression;
    @Column(nullable = false) private boolean active = false;
    @Column(name = "last_run_at") private Instant lastRunAt;
    @Column(name = "created_by", length = 190) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected Notification() { }
    public Notification(String name, String title, String body, NotificationChannel channel,
            NotificationTriggerEvent triggerEvent) {
        this.name = name; this.title = title; this.body = body;
        this.channel = channel == null ? NotificationChannel.IN_APP : channel;
        this.triggerEvent = triggerEvent == null ? NotificationTriggerEvent.MANUAL : triggerEvent;
    }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public java.util.List<Long> planIds() {
        if (planFilter == null || planFilter.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(planFilter.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .filter(v -> v.chars().allMatch(Character::isDigit)).map(Long::valueOf).toList();
    }
    public boolean includesPlan(Long planId) {
        java.util.List<Long> ids = planIds();
        return ids.isEmpty() || (planId != null && ids.contains(planId));
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getBody() { return body; }
    public void setBody(String v) { body = v; }
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel v) { channel = v == null ? NotificationChannel.IN_APP : v; }
    public NotificationTriggerEvent getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(NotificationTriggerEvent v) { triggerEvent = v == null ? NotificationTriggerEvent.MANUAL : v; }
    public NotificationRecipientType getRecipientType() { return recipientType; }
    public void setRecipientType(NotificationRecipientType v) { recipientType = v == null ? NotificationRecipientType.CUSTOMERS : v; }
    public NotificationStatusFilter getStatusFilter() { return statusFilter; }
    public void setStatusFilter(NotificationStatusFilter v) { statusFilter = v == null ? NotificationStatusFilter.ACTIVE_ONLY : v; }
    public String getPlanFilter() { return planFilter; }
    public void setPlanFilter(String v) { planFilter = v; }
    public String getScheduleExpression() { return scheduleExpression; }
    public void setScheduleExpression(String v) { scheduleExpression = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant v) { lastRunAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { deletedAt = v; }
    public boolean isDeleted() { return deletedAt != null; }
    public boolean sendsEmail() { return channel == NotificationChannel.EMAIL || channel == NotificationChannel.BOTH; }
    public boolean sendsInApp() { return channel == NotificationChannel.IN_APP || channel == NotificationChannel.BOTH; }
    @Override public String toString() { return name; }
}

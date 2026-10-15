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
 * One rendered notification for one recipient: the delivery history, status and failure detail an
 * administrator needs to prove a message actually reached a company.
 */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id") private Notification notification;
    @Column(name = "notification_name", nullable = false, length = 120) private String notificationName;
    @Column(name = "trigger_event", nullable = false, length = 48) private String triggerEvent;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private NotificationChannel channel = NotificationChannel.IN_APP;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @Column(nullable = false, length = 190) private String recipient;
    @Column(nullable = false, length = 190) private String title;
    @Column(nullable = false, length = 4000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private DeliveryStatus status = DeliveryStatus.PENDING;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @Column(name = "job_id") private Long jobId;
    /** Prevents a retry or a re-run of the same trigger from sending a customer a second copy. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255) private String idempotencyKey;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected NotificationDelivery() { }
    public NotificationDelivery(Notification notification, NotificationChannel channel, Customer customer,
            String recipient, String title, String body, String idempotencyKey) {
        this.notification = notification;
        this.notificationName = notification == null ? "System" : notification.getName();
        this.triggerEvent = notification == null || notification.getTriggerEvent() == null
                ? NotificationTriggerEvent.MANUAL.name() : notification.getTriggerEvent().name();
        this.channel = channel == null ? NotificationChannel.IN_APP : channel;
        this.customer = customer; this.recipient = recipient; this.title = title; this.body = body;
        this.idempotencyKey = idempotencyKey;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public void mark(DeliveryStatus value, String reason) {
        this.status = value;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
        if (value != DeliveryStatus.PENDING) this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public Notification getNotification() { return notification; }
    public String getNotificationName() { return notificationName; }
    public String getTriggerEvent() { return triggerEvent; }
    public NotificationChannel getChannel() { return channel; }
    public Customer getCustomer() { return customer; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getRecipient() { return recipient; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public DeliveryStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long v) { jobId = v; }

    /**
     * Names the notice this delivery belongs to when it was produced by the platform rather than by a
     * message an administrator wrote - the history still has to say which notice it is showing.
     */
    public void describeSource(String name, String code) {
        if (name != null && !name.isBlank()) this.notificationName = name.length() > 120 ? name.substring(0, 120) : name;
        if (code != null && !code.isBlank()) this.triggerEvent = code.length() > 48 ? code.substring(0, 48) : code;
    }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}

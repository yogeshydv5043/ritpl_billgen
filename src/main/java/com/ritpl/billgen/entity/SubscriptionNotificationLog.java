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

@Entity
@Table(name = "subscription_notification_logs")
public class SubscriptionNotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private CustomerSubscription subscription;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @Column(name = "notification_type", nullable = false, length = 24)
    private String notificationType;
    @Column(name = "days_before", nullable = false)
    private int daysBefore;
    @Column(nullable = false, length = 500)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected SubscriptionNotificationLog() { }
    public SubscriptionNotificationLog(CustomerSubscription subscription, String notificationType, int daysBefore, String message) {
        this.subscription = subscription;
        this.customer = subscription.getCustomer();
        this.notificationType = notificationType;
        this.daysBefore = daysBefore;
        this.message = message;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public CustomerSubscription getSubscription() { return subscription; }
    public Customer getCustomer() { return customer; }
    public String getNotificationType() { return notificationType; }
    public int getDaysBefore() { return daysBefore; }
    public String getMessage() { return message; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant value) { readAt = value; }
}

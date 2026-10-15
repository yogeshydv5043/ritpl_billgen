package com.ritpl.billgen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One notice the platform knows how to produce, and how loud it is about it. The code is fixed by the
 * application - it is what the jobs look up - so an administrator changes behaviour here rather than
 * inventing events the system cannot send.
 *
 * <p>{@code triggerDaysBefore} of zero means "when it happens, not before"; a
 * {@code repeatIntervalDays} of zero means "once, as long as the condition lasts", which is what keeps a
 * retried job from mailing the same customer twice a day.</p>
 */
@Entity
@Table(name = "notification_settings")
public class NotificationSetting extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "notification_code", nullable = false, length = 48) private String notificationCode;
    @Column(name = "notification_name", nullable = false, length = 150) private String notificationName;
    @Column(length = 500) private String description;
    @Column(name = "email_enabled", nullable = false) private boolean emailEnabled;
    @Column(name = "in_app_enabled", nullable = false) private boolean inAppEnabled = true;
    @Column(name = "trigger_days_before", nullable = false) private int triggerDaysBefore;
    @Column(name = "repeat_interval_days", nullable = false) private int repeatIntervalDays;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ContentStatus status = ContentStatus.ACTIVE;

    public Long getId() { return id; }
    public String getNotificationCode() { return notificationCode; }
    public void setNotificationCode(String v) { this.notificationCode = v; }
    public String getNotificationName() { return notificationName; }
    public void setNotificationName(String v) { this.notificationName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean v) { this.emailEnabled = v; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(boolean v) { this.inAppEnabled = v; }
    public int getTriggerDaysBefore() { return triggerDaysBefore; }
    public void setTriggerDaysBefore(int v) { this.triggerDaysBefore = v; }
    public int getRepeatIntervalDays() { return repeatIntervalDays; }
    public void setRepeatIntervalDays(int v) { this.repeatIntervalDays = v; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus v) { this.status = v; }
    public boolean isActive() { return status.isActive(); }
    /** Whether this notice should reach the account at all, given the channels it can use. */
    public boolean deliversAnything() { return isActive() && (emailEnabled || inAppEnabled); }
}

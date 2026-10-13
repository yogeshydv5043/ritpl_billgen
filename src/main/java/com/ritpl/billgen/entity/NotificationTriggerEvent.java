package com.ritpl.billgen.entity;

/**
 * The events a dynamic notification can be bound to. Adding a trigger here is the only code
 * change required; content, recipients and channel stay administrator-configurable.
 */
public enum NotificationTriggerEvent {
    SUBSCRIPTION_EXPIRING_7("Subscription expiring in 7 days"),
    SUBSCRIPTION_EXPIRING_3("Subscription expiring in 3 days"),
    SUBSCRIPTION_EXPIRED("Subscription expired"),
    SUBSCRIPTION_RENEWED("Subscription renewed"),
    INVOICE_USAGE_WARNING("Invoice usage reached the warning threshold"),
    INVOICE_USAGE_EXHAUSTED("Invoice usage limit exhausted"),
    AUDIT_USAGE_WARNING("Audit usage limit approaching"),
    AUDIT_USAGE_EXHAUSTED("Audit usage limit exhausted"),
    ACCOUNT_DISABLED("Account disabled"),
    ACCOUNT_ENABLED("Account enabled"),
    PLAN_CHANGED("Plan changed"),
    JOB_FAILED("Background job failed"),
    MANUAL("Sent on demand from Job Management");

    private final String label;
    NotificationTriggerEvent(String label) { this.label = label; }
    public String getLabel() { return label; }
}

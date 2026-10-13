package com.ritpl.billgen.entity;

/** The background work a Super Admin can schedule. */
public enum JobType {
    SUBSCRIPTION_REMINDER("Subscription renewal reminders"),
    EXPIRY_NOTIFICATION("Subscription expiry notifications"),
    PLAN_CHANGE_NOTIFICATION("Plan change notifications"),
    USAGE_LIMIT_ALERT("Usage limit alerts"),
    CUSTOMER_EMAIL("General customer email"),
    SCHEDULED_REPORT("Scheduled reports"),
    AUDIT_RETENTION_PURGE("Audit retention purge");

    private final String label;
    JobType(String label) { this.label = label; }
    public String getLabel() { return label; }
    /** Only these job types address customers, so cancelling or retrying needs a confirmation. */
    public boolean sendsCustomerCommunication() { return this != AUDIT_RETENTION_PURGE && this != SCHEDULED_REPORT; }
}

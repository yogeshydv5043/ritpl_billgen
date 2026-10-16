package com.ritpl.billgen.entity;

/**
 * The message families a customer account can opt out of in its own settings. These are the
 * platform-generated notices, not the administrator's campaigns: an account cannot switch off the
 * messages that tell it a subscription has expired or that a payment is due, because those describe
 * obligations the account still has.
 */
public enum CustomerAlertTopic {
    EXPIRY_REMINDER("Subscription expiry reminders"),
    QUOTA_ALERT("Invoice quota alerts"),
    PAYMENT_UPDATE("Payment and status updates"),
    SECURITY_ALERT("Security alerts on the account");

    private final String displayName;

    CustomerAlertTopic(String displayName) { this.displayName = displayName; }

    public String getCode() { return name(); }
    public String getDisplayName() { return displayName; }
}

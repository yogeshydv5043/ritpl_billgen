package com.ritpl.billgen.entity;

/** Account filter applied when a notification resolves its recipients. */
public enum NotificationStatusFilter {
    ALL("Every account"), ACTIVE_ONLY("Active accounts only"), DISABLED_ONLY("Disabled accounts only"),
    WITH_SUBSCRIPTION("Accounts with a subscription"), WITHOUT_SUBSCRIPTION("Accounts without a subscription");

    private final String label;
    NotificationStatusFilter(String label) { this.label = label; }
    public String getLabel() { return label; }
}

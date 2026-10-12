package com.ritpl.billgen.entity;

public enum SubscriptionStatus {
    ACTIVE("Active"), EXPIRED("Expired"), CANCELLED("Cancelled");

    private final String displayName;

    SubscriptionStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
package com.ritpl.billgen.entity;

/**
 * Whether a piece of site content is reachable from the public side. Deliberately two values and not a
 * workflow: the application publishes and withdraws, it does not approve.
 */
public enum ContentStatus {
    ACTIVE("Published"), INACTIVE("Hidden");

    private final String displayName;
    ContentStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return this == ACTIVE; }
}

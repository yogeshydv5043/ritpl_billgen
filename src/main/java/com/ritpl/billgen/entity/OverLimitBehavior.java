package com.ritpl.billgen.entity;

/** What happens when an account reaches a plan limit. */
public enum OverLimitBehavior {
    /** Refuse the operation (the safe default for a paid entitlement). */
    BLOCK("Block the action"),
    /** Record a usage alert and let the operation through. */
    WARN("Warn and allow");

    private final String label;
    OverLimitBehavior(String label) { this.label = label; }
    public String getLabel() { return label; }
}

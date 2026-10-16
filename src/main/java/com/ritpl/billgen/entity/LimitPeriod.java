package com.ritpl.billgen.entity;

/** The window an administrator chooses for a usage quota. Only one period is active at a time. */
public enum LimitPeriod {
    NONE("No limit"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom");

    private final String label;
    LimitPeriod(String label) { this.label = label; }
    public String getLabel() { return label; }
}

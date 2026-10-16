package com.ritpl.billgen.entity;

/** How a plan's usage period is measured. */
public enum BillingCycle {
    MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom period");

    private final String label;
    BillingCycle(String label) { this.label = label; }
    public String getLabel() { return label; }
}

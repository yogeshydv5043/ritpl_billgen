package com.ritpl.billgen.entity;

/**
 * Delivery outcome of one notification for one recipient. SIMULATED means the message was
 * rendered and recorded but no transport is configured in this deployment.
 */
public enum DeliveryStatus {
    PENDING("Pending"), SENT("Sent"), SIMULATED("Recorded (no transport)"), FAILED("Failed"), SKIPPED("Skipped");

    private final String label;
    DeliveryStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    public boolean isDelivered() { return this == SENT || this == SIMULATED; }
}

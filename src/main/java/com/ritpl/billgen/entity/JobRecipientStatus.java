package com.ritpl.billgen.entity;

/** Per-recipient result. Kept separate from the batch so a retry only touches failed rows. */
public enum JobRecipientStatus {
    PENDING("Pending"), SENT("Sent"), SIMULATED("Recorded (no transport)"), FAILED("Failed"),
    SKIPPED("Skipped (already delivered)"), RETRY_SCHEDULED("Retry scheduled");

    private final String label;
    JobRecipientStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    public boolean isSuccessful() { return this == SENT || this == SIMULATED || this == SKIPPED; }
}

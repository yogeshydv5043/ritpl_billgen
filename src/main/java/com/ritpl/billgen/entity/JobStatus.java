package com.ritpl.billgen.entity;

public enum JobStatus {
    PENDING("Pending"), SCHEDULED("Scheduled"), RUNNING("Running"), COMPLETED("Completed"),
    PARTIALLY_FAILED("Partially failed"), FAILED("Failed"), CANCELLED("Cancelled"), PAUSED("Paused");

    private final String label;
    JobStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    /** A job in one of these states still has work queued, so it may be cancelled or paused. */
    public boolean isWorkPending() { return this == PENDING || this == SCHEDULED || this == RUNNING || this == PAUSED; }
    public boolean isTerminal() { return this == COMPLETED || this == FAILED || this == CANCELLED || this == PARTIALLY_FAILED; }
}

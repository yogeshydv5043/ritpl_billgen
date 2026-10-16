package com.ritpl.billgen.entity;

public enum JobBatchStatus {
    PENDING("Pending"), RUNNING("Running"), COMPLETED("Completed"), FAILED("Failed"),
    CANCELLED("Cancelled"), PAUSED("Paused");

    private final String label;
    JobBatchStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}

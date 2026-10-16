package com.ritpl.billgen.entity;

public enum JobTriggerType {
    MANUAL("Run now"), SCHEDULED("Scheduled");

    private final String label;
    JobTriggerType(String label) { this.label = label; }
    public String getLabel() { return label; }
}

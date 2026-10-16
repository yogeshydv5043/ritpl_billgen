package com.ritpl.billgen.entity;

public enum JobRecurrence {
    NONE("Once"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly");

    private final String label;
    JobRecurrence(String label) { this.label = label; }
    public String getLabel() { return label; }
}

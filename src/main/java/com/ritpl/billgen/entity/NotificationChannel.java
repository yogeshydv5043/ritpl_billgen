package com.ritpl.billgen.entity;

/** Where a configured notification is delivered. */
public enum NotificationChannel {
    EMAIL("Email"), IN_APP("In-app"), BOTH("Email + In-app");

    private final String label;
    NotificationChannel(String label) { this.label = label; }
    public String getLabel() { return label; }
}

package com.ritpl.billgen.entity;

/** Who receives a configured notification. */
public enum NotificationRecipientType {
    CUSTOMERS("Customer accounts"), ADMINS("Administrator accounts");

    private final String label;
    NotificationRecipientType(String label) { this.label = label; }
    public String getLabel() { return label; }
}

package com.ritpl.billgen.entity;

public enum InvoiceStatus {
    DRAFT("Draft"), SENT("Sent"), PAID("Paid");

    private final String displayName;

    InvoiceStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

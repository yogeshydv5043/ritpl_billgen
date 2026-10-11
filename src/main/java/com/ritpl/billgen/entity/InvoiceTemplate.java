package com.ritpl.billgen.entity;

public enum InvoiceTemplate {
    CLASSIC("Classic", "Balanced and professional."),
    MODERN("Modern", "A bold contemporary accent."),
    MINIMAL("Minimal", "A clean, compact statement.");

    private final String displayName;
    private final String description;
    InvoiceTemplate(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    public String getCode() { return name(); }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}

package com.ritpl.billgen.entity;

/**
 * The print layouts the invoice renderer knows about. Templates are admin-managed data; a
 * template picks one of these layouts through {@link BillTemplate#getStyle()}.
 */
public enum BillTemplateStyle {
    CLASSIC("Classic", "Balanced and professional."),
    MODERN("Modern", "A bold contemporary accent."),
    MINIMAL("Minimal", "A clean, compact statement.");

    private final String displayName;
    private final String description;

    BillTemplateStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() { return name(); }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    /** CSS modifier used by the print view, e.g. {@code template-modern}. */
    public String getCssClass() { return "template-" + name().toLowerCase(java.util.Locale.ROOT); }
}
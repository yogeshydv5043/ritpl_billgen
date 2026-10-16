package com.ritpl.billgen.entity;

/** The policies the product can show. Each type has one row, so adding a page means adding a constant. */
public enum PolicyType {
    REFUND("Refund policy", "refund"),
    PRIVACY("Privacy policy", "privacy"),
    TERMS_CONDITIONS("Terms & conditions", "terms");

    private final String displayName;
    private final String slug;
    PolicyType(String displayName, String slug) { this.displayName = displayName; this.slug = slug; }
    public String getDisplayName() { return displayName; }
    public String getSlug() { return slug; }
}

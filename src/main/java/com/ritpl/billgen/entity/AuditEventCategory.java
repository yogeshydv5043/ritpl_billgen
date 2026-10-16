package com.ritpl.billgen.entity;

/** How an audit definition is grouped in the Super Admin console. */
public enum AuditEventCategory {
    SECURITY("Security"), ACCOUNT("Accounts"), BILLING("Billing"), CATALOG("Catalogue"),
    SUBSCRIPTION("Subscriptions"), TEMPLATE("Templates"), ADMINISTRATION("Administration"), SYSTEM("System");

    private final String label;
    AuditEventCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
}

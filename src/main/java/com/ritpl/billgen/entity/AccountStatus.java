package com.ritpl.billgen.entity;

/** Lifecycle of a company account. A disabled account keeps its data but cannot sign in. */
public enum AccountStatus {
    ACTIVE("Active"), DISABLED("Disabled");

    private final String label;
    AccountStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}

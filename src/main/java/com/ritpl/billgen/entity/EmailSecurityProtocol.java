package com.ritpl.billgen.entity;

/** How the mail server is reached. {@code NONE} is a real choice, not a placeholder: a local relay
 *  needs neither authentication nor encryption, and pretending otherwise would force a broken config. */
public enum EmailSecurityProtocol {
    NONE("No encryption"), START_TLS("StartTLS"), SSL_TLS("SSL / TLS");

    private final String displayName;
    EmailSecurityProtocol(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    public boolean isEncrypted() { return this != NONE; }
}

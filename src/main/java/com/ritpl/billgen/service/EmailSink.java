package com.ritpl.billgen.service;

/**
 * The transport a background job uses for email. Kept behind an interface so the deployment
 * decides how messages actually leave the system (SMTP when configured, recorded otherwise)
 * without the job pipeline changing.
 */
public interface EmailSink {
    /** @throws Exception any transport failure; the caller records it against the recipient. */
    void send(String recipient, String subject, String body) throws Exception;

    /** True when a real transport is configured, so the UI can say so honestly. */
    default boolean isLive() { return false; }

    /** Human readable name of the active transport, shown on the job and notification screens. */
    default String describe() { return "Recording only (no mail transport configured)"; }
}

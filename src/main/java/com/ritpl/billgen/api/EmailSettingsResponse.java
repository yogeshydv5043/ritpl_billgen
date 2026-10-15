package com.ritpl.billgen.api;

import java.time.Instant;

import com.ritpl.billgen.service.EmailSettingsService;

/**
 * The mirror of {@link EmailSettingsRequest} with the credential replaced by {@code passwordSet} and a
 * sentence. There is no field a client could read the password from, so a client cannot accidentally
 * display it.
 */
public record EmailSettingsResponse(String smtpHost, Integer smtpPort, String smtpUsername, boolean passwordSet,
        Instant passwordSetAt, boolean authEnabled, com.ritpl.billgen.entity.EmailSecurityProtocol securityProtocol,
        Integer timeoutMs, String senderName, String senderEmail, String replyToEmail,
        com.ritpl.billgen.entity.ContentStatus status, boolean credentialStorageAvailable, boolean transportLive,
        String transportSummary, String passwordHint) {

    public static EmailSettingsResponse of(EmailSettingsService.View view) {
        return new EmailSettingsResponse(view.smtpHost(), view.smtpPort(), view.smtpUsername(), view.passwordSet(),
                view.passwordSetAt(), view.authEnabled(), view.securityProtocol(), view.timeoutMs(),
                view.senderName(), view.senderEmail(), view.replyToEmail(), view.status(),
                view.credentialStorageAvailable(), view.transportLive(), view.transportSummary(),
                view.passwordHint());
    }
}

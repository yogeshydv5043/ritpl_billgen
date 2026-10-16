package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.EmailSecurityProtocol;

/**
 * The mail transport. A null {@code password} means "leave the stored credential alone": the screen never
 * shows the password back, so an empty field cannot be told apart from a deliberate clearing, and the
 * safe reading wins. {@link EmailSettingsService} is the only class that decides what to do with it.
 */
public record EmailSettingsCommand(String smtpHost, Integer smtpPort, String smtpUsername, String password,
        Boolean authEnabled, EmailSecurityProtocol securityProtocol, Integer timeoutMs, String senderName,
        String senderEmail, String replyToEmail, ContentStatus status) {
}

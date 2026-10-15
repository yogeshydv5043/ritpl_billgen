package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.EmailSecurityProtocol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code password} is accepted and never returned. A null or blank value leaves the stored credential
 * alone, which is why the field is optional even though a server that needs auth obviously has one.
 */
public record EmailSettingsRequest(@Size(max = 255) String smtpHost,
        @Min(1) @Max(65535) Integer smtpPort,
        @Size(max = 190) String smtpUsername,
        @Size(max = 200) String password,
        Boolean authEnabled,
        EmailSecurityProtocol securityProtocol,
        @Min(1000) @Max(120000) Integer timeoutMs,
        @Size(max = 150) String senderName,
        @Email @Size(max = 190) String senderEmail,
        @Email @Size(max = 190) String replyToEmail,
        com.ritpl.billgen.entity.ContentStatus status) { }

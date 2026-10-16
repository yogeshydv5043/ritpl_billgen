package com.ritpl.billgen.api;

import java.time.Instant;

/** The stored branding plus when it last changed. Nothing here is a secret, so it is all returned. */
public record AppInfoResponse(boolean configured, String siteName, String siteTagline, String siteLogoUrl,
        String siteFaviconUrl, String supportEmail, String supportPhone, String supportWhatsapp,
        String primaryColor, String secondaryColor, String instagramUrl, String facebookUrl, String youtubeUrl,
        String xUrl, String footerDescription, String copyrightText, String disclaimerText,
        Instant updatedAt, String updatedBy) { }

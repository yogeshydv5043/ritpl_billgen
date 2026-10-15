package com.ritpl.billgen.api;

/**
 * What an unauthenticated visitor may learn about the product. This is deliberately a different shape from
 * {@link AppInfoResponse}: the public view has no audit columns, and a field added to the administrator's
 * form does not appear here unless somebody decides it belongs on a public page.
 */
public record PublicAppInfoResponse(String siteName, String siteTagline, String siteLogoUrl,
        String siteFaviconUrl, String supportEmail, String supportPhone, String supportWhatsapp,
        String primaryColor, String secondaryColor, String instagramUrl, String facebookUrl,
        String youtubeUrl, String xUrl, String footerDescription, String copyrightText, String disclaimerText) {
}

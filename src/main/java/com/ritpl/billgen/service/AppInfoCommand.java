package com.ritpl.billgen.service;

/** Everything the application says about itself, submitted as one form. Links are http(s) or absent. */
public record AppInfoCommand(String siteName, String siteTagline, String siteLogoUrl, String siteFaviconUrl,
        String supportEmail, String supportPhone, String supportWhatsapp, String primaryColor,
        String secondaryColor, String instagramUrl, String facebookUrl, String youtubeUrl, String xUrl,
        String footerDescription, String copyrightText, String disclaimerText) {
}

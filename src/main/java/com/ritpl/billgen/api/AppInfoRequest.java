package com.ritpl.billgen.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One form, sixteen fields. The colours are CSS hex values, the links are http(s) or absent. */
public record AppInfoRequest(
        @NotBlank @Size(max = 150) String siteName,
        @Size(max = 255) String siteTagline,
        @Size(max = 500) String siteLogoUrl,
        @Size(max = 500) String siteFaviconUrl,
        @Email @Size(max = 190) String supportEmail,
        @Size(max = 40) String supportPhone,
        @Size(max = 40) String supportWhatsapp,
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "must be a hex colour such as #7357f4") String primaryColor,
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "must be a hex colour such as #19182f") String secondaryColor,
        @Size(max = 500) String instagramUrl,
        @Size(max = 500) String facebookUrl,
        @Size(max = 500) String youtubeUrl,
        @Size(max = 500) String xUrl,
        @Size(max = 1000) String footerDescription,
        @Size(max = 500) String copyrightText,
        @Size(max = 2000) String disclaimerText) { }

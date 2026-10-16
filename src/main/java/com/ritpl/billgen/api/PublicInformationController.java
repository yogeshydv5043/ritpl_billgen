package com.ritpl.billgen.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ritpl.billgen.entity.AppInfo;
import com.ritpl.billgen.service.AppInfoService;

/**
 * The one part of the site configuration an anonymous reader is allowed to ask for: what the product is
 * called, how to reach the people who run it, and what it says in its own footer. It is a separate
 * response type rather than {@link AppInfoResponse} with fields nulled, so a later addition to the
 * administrator's form cannot leak out of here by accident.
 *
 * <p>Policy pages and FAQs have their own public pages, rendered by the web layer from the same services;
 * this endpoint exists for the clients that need branding without HTML, which is why it carries nothing
 * else.</p>
 */
@RestController
@RequestMapping("/api/public")
public class PublicInformationController {

    private final AppInfoService appInfo;

    public PublicInformationController(AppInfoService appInfo) { this.appInfo = appInfo; }

    @GetMapping("/app-information")
    public PublicAppInfoResponse information() {
        AppInfo row = appInfo.current();
        return new PublicAppInfoResponse(row.getSiteName(), row.getSiteTagline(), row.getSiteLogoUrl(),
                row.getSiteFaviconUrl(), row.getSupportEmail(), row.getSupportPhone(), row.getSupportWhatsapp(),
                row.getPrimaryColor(), row.getSecondaryColor(), row.getInstagramUrl(), row.getFacebookUrl(),
                row.getYoutubeUrl(), row.getXUrl(), row.getFooterDescription(), row.getCopyrightText(),
                row.getDisclaimerText());
    }
}

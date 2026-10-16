package com.ritpl.billgen.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AppInfo;
import com.ritpl.billgen.repository.AppInfoRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Read and write the application's own identity. There is one row and both halves of that are the point:
 * reads fall back to the shipped branding so a page never renders blank before anybody has configured
 * anything, and a write can only ever land on that one row.
 *
 * <p>Nothing here is a secret - the support address and the social links are shown to the public - but
 * saving them is still audited, because the audit trail is where a reader can see who pointed the
 * product's logo and support address at their own server.</p>
 */
@Service
public class AppInfoService {

    private final AppInfoRepository info;
    private final AuditService audit;

    public AppInfoService(AppInfoRepository info, AuditService audit) { this.info = info; this.audit = audit; }

    @Transactional(readOnly = true)
    public AppInfo current() {
        return info.findById(AppInfo.SINGLETON_ID).map(AppInfo::attached).orElseGet(AppInfo::defaults);
    }

    @Transactional
    public AppInfo save(TenantScope scope, AppInfoCommand command) {
        ConfigurationRules.requireAdmin(scope);
        AppInfo row = info.findById(AppInfo.SINGLETON_ID).map(AppInfo::attached).orElseGet(AppInfo::new);
        boolean created = row.getCreatedAt() == null;
        Map<String, Object> before = created ? null : snapshot(row);

        String primary = ConfigurationRules.color(command.primaryColor(), "Primary colour");
        String secondary = ConfigurationRules.color(command.secondaryColor(), "Secondary colour");

        row.setSiteName(ConfigurationRules.required(command.siteName(), 150, "Site name"));
        row.setSiteTagline(ConfigurationRules.optional(command.siteTagline(), 255, "Tagline"));
        row.setSiteLogoUrl(ConfigurationRules.image(command.siteLogoUrl(), 500, "Logo URL"));
        row.setSiteFaviconUrl(ConfigurationRules.image(command.siteFaviconUrl(), 500, "Favicon URL"));
        row.setSupportEmail(ConfigurationRules.email(command.supportEmail(), 190, "Support email"));
        row.setSupportPhone(ConfigurationRules.optional(command.supportPhone(), 40, "Support phone"));
        row.setSupportWhatsapp(ConfigurationRules.optional(command.supportWhatsapp(), 40, "Support WhatsApp"));
        row.setPrimaryColor(primary == null ? "#7357f4" : primary);
        row.setSecondaryColor(secondary == null ? "#19182f" : secondary);
        row.setInstagramUrl(ConfigurationRules.url(command.instagramUrl(), 500, "Instagram URL"));
        row.setFacebookUrl(ConfigurationRules.url(command.facebookUrl(), 500, "Facebook URL"));
        row.setYoutubeUrl(ConfigurationRules.url(command.youtubeUrl(), 500, "YouTube URL"));
        row.setXUrl(ConfigurationRules.url(command.xUrl(), 500, "X URL"));
        row.setFooterDescription(ConfigurationRules.optional(command.footerDescription(), 1000, "Footer description"));
        row.setCopyrightText(ConfigurationRules.optional(command.copyrightText(), 500, "Copyright text"));
        row.setDisclaimerText(ConfigurationRules.optional(command.disclaimerText(), 2000, "Disclaimer"));

        if (created) row.recordAuthor("administrator"); else row.recordEditor("administrator");
        AppInfo saved = AppInfo.attached(info.save(row));
        audit.record(AuditEventCommand.success(null, created ? "CREATE" : "UPDATE", "APP_INFO",
                "Application information", AppInfo.SINGLETON_ID,
                created ? "Administrator set the application information for the first time."
                        : "Administrator updated the application information.",
                before, snapshot(saved)));
        return saved;
    }

    /** Only the facts a reader needs to see the change; a URL is reported as "set", never as a diff. */
    private static Map<String, Object> snapshot(AppInfo row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("siteName", row.getSiteName());
        values.put("supportEmail", row.getSupportEmail());
        values.put("logo", row.getSiteLogoUrl() == null ? "none" : "set");
        values.put("primaryColor", row.getPrimaryColor());
        values.put("socialLinks", (row.getInstagramUrl() == null ? 0 : 1) + (row.getFacebookUrl() == null ? 0 : 1)
                + (row.getYoutubeUrl() == null ? 0 : 1) + (row.getXUrl() == null ? 0 : 1));
        values.put("footerSet", row.getFooterDescription() != null);
        return values;
    }
}

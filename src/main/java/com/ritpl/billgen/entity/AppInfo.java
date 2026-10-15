package com.ritpl.billgen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

/**
 * What the product is called, who to call when it misbehaves, how it looks and what the footer says -
 * the whole of the application's own identity in one row. Social links and footer text live here rather
 * than in a second settings table, because they are the same decision made at the same time.
 *
 * <p>There is exactly one row. The id is assigned rather than generated and the database refuses any
 * other value, so a duplicate configuration cannot be created by two administrators saving at once.</p>
 */
@Entity
@Table(name = "app_info")
public class AppInfo extends AuditableEntity implements Persistable<Long> {

    /** The one row this table may hold. */
    public static final long SINGLETON_ID = 1L;

    /** See {@link CustomerSettings} - an assigned id would otherwise read as "detached" on a first save. */
    @jakarta.persistence.Transient private boolean attached;

    @Id private Long id = SINGLETON_ID;

    @Column(name = "site_name", nullable = false, length = 150) private String siteName;
    @Column(name = "site_tagline", length = 255) private String siteTagline;
    @Column(name = "site_logo_url", length = 500) private String siteLogoUrl;
    @Column(name = "site_favicon_url", length = 500) private String siteFaviconUrl;

    @Column(name = "support_email", length = 190) private String supportEmail;
    @Column(name = "support_phone", length = 40) private String supportPhone;
    @Column(name = "support_whatsapp", length = 40) private String supportWhatsapp;

    @Column(name = "primary_color", nullable = false, length = 7) private String primaryColor = "#7357f4";
    @Column(name = "secondary_color", nullable = false, length = 7) private String secondaryColor = "#19182f";

    @Column(name = "instagram_url", length = 500) private String instagramUrl;
    @Column(name = "facebook_url", length = 500) private String facebookUrl;
    @Column(name = "youtube_url", length = 500) private String youtubeUrl;
    @Column(name = "x_url", length = 500) private String xUrl;

    @Column(name = "footer_description", length = 1000) private String footerDescription;
    @Column(name = "copyright_text", length = 500) private String copyrightText;
    @Column(name = "disclaimer_text", length = 2000) private String disclaimerText;

    @Override public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @Override public boolean isNew() { return !attached; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String v) { this.siteName = v; }
    public String getSiteTagline() { return siteTagline; }
    public void setSiteTagline(String v) { this.siteTagline = v; }
    public String getSiteLogoUrl() { return siteLogoUrl; }
    public void setSiteLogoUrl(String v) { this.siteLogoUrl = v; }
    public String getSiteFaviconUrl() { return siteFaviconUrl; }
    public void setSiteFaviconUrl(String v) { this.siteFaviconUrl = v; }
    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String v) { this.supportEmail = v; }
    public String getSupportPhone() { return supportPhone; }
    public void setSupportPhone(String v) { this.supportPhone = v; }
    public String getSupportWhatsapp() { return supportWhatsapp; }
    public void setSupportWhatsapp(String v) { this.supportWhatsapp = v; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String v) { this.primaryColor = v; }
    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String v) { this.secondaryColor = v; }
    public String getInstagramUrl() { return instagramUrl; }
    public void setInstagramUrl(String v) { this.instagramUrl = v; }
    public String getFacebookUrl() { return facebookUrl; }
    public void setFacebookUrl(String v) { this.facebookUrl = v; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String v) { this.youtubeUrl = v; }
    public String getXUrl() { return xUrl; }
    public void setXUrl(String v) { this.xUrl = v; }
    public String getFooterDescription() { return footerDescription; }
    public void setFooterDescription(String v) { this.footerDescription = v; }
    public String getCopyrightText() { return copyrightText; }
    public void setCopyrightText(String v) { this.copyrightText = v; }
    public String getDisclaimerText() { return disclaimerText; }
    public void setDisclaimerText(String v) { this.disclaimerText = v; }

    /** A row that exists in the table: saving it is an update, whatever its assigned id looks like. */
    public static AppInfo attached(AppInfo row) { row.attached = true; return row; }

    /** What the application shows before anybody has configured it - the branding it already ships with. */
    public static AppInfo defaults() {
        AppInfo info = new AppInfo();
        info.siteName = "BillGen";
        info.siteTagline = "Invoice Studio";
        info.copyrightText = "\u00a9 2026 BillGen";
        return info;
    }
}

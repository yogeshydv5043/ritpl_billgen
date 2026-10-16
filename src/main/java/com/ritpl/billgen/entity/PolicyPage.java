package com.ritpl.billgen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A policy the website has to be able to show: refunds, privacy, terms. The body is stored as
 * sanitized HTML - {@link com.ritpl.billgen.service.RichTextSanitizer} has already reduced it to the
 * handful of tags a policy is written with, so nothing read back from here needs escaping again.
 */
@Entity
@Table(name = "policy_pages")
public class PolicyPage extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "policy_type", nullable = false, length = 24)
    private PolicyType policyType = PolicyType.PRIVACY;

    @Column(nullable = false, length = 200) private String heading;
    @Column(name = "meta_title", length = 200) private String metaTitle;
    @Column(name = "meta_description", length = 500) private String metaDescription;
    @Column(name = "page_content", nullable = false, length = 20000) private String pageContent;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ContentStatus status = ContentStatus.INACTIVE;

    public Long getId() { return id; }
    public PolicyType getPolicyType() { return policyType; }
    public void setPolicyType(PolicyType v) { this.policyType = v; }
    public String getHeading() { return heading; }
    public void setHeading(String v) { this.heading = v; }
    public String getMetaTitle() { return metaTitle; }
    public void setMetaTitle(String v) { this.metaTitle = v; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String v) { this.metaDescription = v; }
    public String getPageContent() { return pageContent; }
    public void setPageContent(String v) { this.pageContent = v; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus v) { this.status = v; }
    public boolean isPublished() { return status.isActive(); }
}

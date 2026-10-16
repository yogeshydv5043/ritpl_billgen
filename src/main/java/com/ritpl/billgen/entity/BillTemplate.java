package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A bill/invoice template. Templates are created and maintained by an administrator, bundled
 * into subscription plans and unlocked for a customer account through its purchased plan.
 */
@Entity
@Table(name = "bill_templates")
public class BillTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "style_key", nullable = false, length = 20)
    private BillTemplateStyle style = BillTemplateStyle.CLASSIC;
    @Column(name = "accent_color", length = 7)
    private String accentColor;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    /** Optional copy rendered by the layout. Snapshotted onto the invoice when it is generated. */
    @Column(name = "header_text", length = 500)
    private String headerText;
    @Column(name = "footer_text", length = 500)
    private String footerText;
    @Column(name = "payment_terms", length = 500)
    private String paymentTerms;
    @Column(name = "terms_and_conditions", length = 2000)
    private String termsAndConditions;
    /** Bumped on every save. An invoice stores the version it was produced with. */
    @Column(nullable = false)
    private int version = 1;
    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;
    @Column(name = "deleted_by", length = 190)
    private String deletedBy;
    @Column(name = "updated_by", length = 190)
    private String updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BillTemplate() { }

    public BillTemplate(String code, String name, BillTemplateStyle style) {
        this.code = code;
        this.name = name;
        this.style = style == null ? BillTemplateStyle.CLASSIC : style;
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public BillTemplateStyle getStyle() { return style; }
    public void setStyle(BillTemplateStyle value) { style = value == null ? BillTemplateStyle.CLASSIC : value; }
    public String getStyleCode() { return style == null ? BillTemplateStyle.CLASSIC.name() : style.name(); }
    public String getStyleCssClass() { return style == null ? BillTemplateStyle.CLASSIC.getCssClass() : style.getCssClass(); }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String value) { accentColor = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { sortOrder = value; }
    public String getHeaderText() { return headerText; }
    public void setHeaderText(String value) { headerText = value; }
    public String getFooterText() { return footerText; }
    public void setFooterText(String value) { footerText = value; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String value) { paymentTerms = value; }
    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String value) { termsAndConditions = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { version = Math.max(1, value); }
    public void bumpVersion() { version = Math.max(1, version) + 1; }
    public java.time.Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.Instant value) { deletedAt = value; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String value) { deletedBy = value; }
    public boolean isDeleted() { return deletedAt != null; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { updatedBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return name; }
}
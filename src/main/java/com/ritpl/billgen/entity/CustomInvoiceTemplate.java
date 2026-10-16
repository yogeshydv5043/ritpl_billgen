package com.ritpl.billgen.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "custom_invoice_templates", indexes = @Index(name = "idx_custom_templates_customer_active", columnList = "customer_id, active, name"))
public class CustomInvoiceTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @Column(nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "style_key", nullable = false, length = 20)
    private BillTemplateStyle style = BillTemplateStyle.CLASSIC;
    @Column(name = "accent_color", length = 7) private String accentColor;
    // The presentation a customer chooses for its own copy of a layout. These columns exist only on
    // the customer's template: the system template a plan bundles is never written from here.
    @Column(name = "font_key", nullable = false, length = 16) private String fontKey = "DEFAULT";
    @Column(name = "layout_density", nullable = false, length = 16) private String layoutDensity = "COMFORTABLE";
    @Column(name = "show_logo", nullable = false) private boolean showLogo = true;
    @Column(name = "show_bank_details", nullable = false) private boolean showBankDetails = false;
    @Column(name = "bank_details", length = 600) private String bankDetails;
    @Column(name = "payment_terms", length = 240) private String paymentTerms;
    @Column(name = "footer_text", length = 600) private String footerText;
    @Column(name = "signature_label", length = 120) private String signatureLabel;
    @Column(name = "signature_name", length = 120) private String signatureName;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private int version = 1;
    @Column(name = "deleted_at") private java.time.Instant deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected CustomInvoiceTemplate() { }
    public CustomInvoiceTemplate(Customer customer, String name, BillTemplateStyle style, String accentColor) {
        this.customer = customer; this.name = name; this.style = style; this.accentColor = accentColor;
    }
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer v) { customer = v; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public BillTemplateStyle getStyle() { return style; }
    public String getStyleKey() { return style == null ? "CLASSIC" : style.name(); }
    public void setStyle(BillTemplateStyle v) { style = v == null ? BillTemplateStyle.CLASSIC : v; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String v) { accentColor = v; }
    public String getFontKey() { return fontKey == null ? "DEFAULT" : fontKey; }
    public void setFontKey(String v) { fontKey = v == null || v.isBlank() ? "DEFAULT" : v; }
    public String getLayoutDensity() { return layoutDensity == null ? "COMFORTABLE" : layoutDensity; }
    public void setLayoutDensity(String v) { layoutDensity = v == null || v.isBlank() ? "COMFORTABLE" : v; }
    public boolean isCompact() { return "COMPACT".equals(getLayoutDensity()); }
    public boolean isShowLogo() { return showLogo; }
    public void setShowLogo(boolean v) { showLogo = v; }
    public boolean isShowBankDetails() { return showBankDetails; }
    public void setShowBankDetails(boolean v) { showBankDetails = v; }
    public String getBankDetails() { return bankDetails; }
    public void setBankDetails(String v) { bankDetails = v; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String v) { paymentTerms = v; }
    public String getFooterText() { return footerText; }
    public void setFooterText(String v) { footerText = v; }
    public String getSignatureLabel() { return signatureLabel; }
    public void setSignatureLabel(String v) { signatureLabel = v; }
    public String getSignatureName() { return signatureName; }
    public void setSignatureName(String v) { signatureName = v; }

    /** Back to the plain layout the style defines: colors and text the customer added are dropped. */
    public void resetPresentation() {
        fontKey = "DEFAULT"; layoutDensity = "COMFORTABLE"; showLogo = true; showBankDetails = false;
        bankDetails = null; paymentTerms = null; footerText = null; signatureLabel = null; signatureName = null;
    }

    public boolean isActive() { return active; }
    public int getVersion() { return version; }
    public void setVersion(int value) { version = Math.max(1, value); }
    public void bumpVersion() { version = Math.max(1, version) + 1; }
    public java.time.Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.Instant value) { deletedAt = value; }
    public boolean isDeleted() { return deletedAt != null; }
    public void setActive(boolean v) { active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.ritpl.billgen.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_customer_date", columnList = "customer_id, issue_date"),
        @Index(name = "idx_invoices_status_due", columnList = "status, due_date"),
        @Index(name = "idx_invoices_generated_at", columnList = "generated_at")
})
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "invoice_number", nullable = false, unique = true, length = 32) private String invoiceNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(nullable = false, length = 3) private String currency = "INR";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "template_id")
    private BillTemplate template;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "custom_template_id")
    private CustomInvoiceTemplate customTemplate;
    @Column(name = "template_name_snapshot", length = 100) private String templateNameSnapshot;
    @Column(name = "template_style_snapshot", length = 20) private String templateStyleSnapshot;
    @Column(name = "template_accent_snapshot", length = 7) private String templateAccentSnapshot;
    /** The template version this invoice was produced with; edits to the template never rewrite it. */
    @Column(name = "template_version_snapshot") private Integer templateVersionSnapshot;
    // Copied from the customer's own template when the invoice is issued, so a later edit to that
    // template cannot change a document that has already been delivered. A null means the invoice was
    // issued on a standard template, which prints with its layout's own defaults.
    @Column(name = "template_font_snapshot", length = 16) private String templateFontSnapshot;
    @Column(name = "template_density_snapshot", length = 16) private String templateDensitySnapshot;
    @Column(name = "template_terms_snapshot", length = 240) private String templateTermsSnapshot;
    @Column(name = "template_footer_snapshot", length = 600) private String templateFooterSnapshot;
    @Column(name = "template_bank_snapshot", length = 600) private String templateBankSnapshot;
    @Column(name = "template_show_logo_snapshot") private Boolean templateShowLogoSnapshot;
    @Column(name = "template_signature_label_snapshot", length = 120) private String templateSignatureLabelSnapshot;
    @Column(name = "template_signature_name_snapshot", length = 120) private String templateSignatureNameSnapshot;
    @Column(length = 1200) private String notes;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "company_name_snapshot", length = 160) private String companyNameSnapshot;
    @Column(name = "company_contact_snapshot", length = 120) private String companyContactSnapshot;
    @Column(name = "company_email_snapshot", length = 190) private String companyEmailSnapshot;
    @Column(name = "company_phone_snapshot", length = 40) private String companyPhoneSnapshot;
    @Column(name = "company_address_snapshot", length = 600) private String companyAddressSnapshot;
    @Column(name = "company_gstin_snapshot", length = 24) private String companyGstinSnapshot;
    @Column(name = "company_logo_snapshot", columnDefinition = "bytea") private byte[] companyLogoSnapshot;
    @Column(name = "company_logo_content_type_snapshot", length = 40) private String companyLogoContentTypeSnapshot;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "usage_counted", nullable = false) private boolean usageCounted;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usage_subscription_id")
    private CustomerSubscription usageSubscription;
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<InvoiceItem> items = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public Invoice() { }
    public void addItem(InvoiceItem item) { items.add(item); item.setInvoice(this); item.setCustomer(customer); }
    @PrePersist void onCreate() {
        LocalDateTime n = LocalDateTime.now(); createdAt = n; updatedAt = n;
        if (generatedAt == null) generatedAt = Instant.now();
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public boolean isOverdue(LocalDate day) { return status == InvoiceStatus.SENT && dueDate.isBefore(day); }
    public String getTemplateCode() { return customTemplate != null ? "CUSTOM-" + customTemplate.getId() : template == null ? null : template.getCode(); }
    public String getTemplateName() { return templateNameSnapshot != null ? templateNameSnapshot : customTemplate != null ? customTemplate.getName() : template == null ? null : template.getName(); }
    public String getTemplateCssClass() {
        String style = templateStyleSnapshot != null ? templateStyleSnapshot : customTemplate != null ? customTemplate.getStyleKey() : template == null ? "CLASSIC" : template.getStyleCode();
        try { return BillTemplateStyle.valueOf(style).getCssClass(); } catch (RuntimeException ex) { return BillTemplateStyle.CLASSIC.getCssClass(); }
    }
    /** Typeface and spacing classes the account chose, so the print view matches the PDF. */
    public String getPresentationCssClass() {
        java.util.List<String> classes = new java.util.ArrayList<>();
        String font = getPresentationFont();
        if (font != null && !"DEFAULT".equals(font)) classes.add("invoice-font-" + font.toLowerCase(java.util.Locale.ROOT));
        if (isCompactPresentation()) classes.add("invoice-compact");
        return String.join(" ", classes);
    }

    public String getTemplateAccentColor() { return templateAccentSnapshot != null ? templateAccentSnapshot : customTemplate != null ? customTemplate.getAccentColor() : template == null ? null : template.getAccentColor(); }
    public String getCompanyLogoDataUrl() {
        return companyLogoSnapshot == null || companyLogoSnapshot.length == 0 || companyLogoContentTypeSnapshot == null
                ? null : "data:" + companyLogoContentTypeSnapshot + ";base64," + Base64.getEncoder().encodeToString(companyLogoSnapshot);
    }
    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String v) { invoiceNumber = v; }
    public Customer getCustomer() { return customer; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public void setCustomer(Customer v) { customer = v; for (InvoiceItem item : items) item.setCustomer(v); }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate v) { issueDate = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { dueDate = v; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus v) { status = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public BillTemplate getTemplate() { return template; }
    public void setTemplate(BillTemplate v) { template = v; }
    public CustomInvoiceTemplate getCustomTemplate() { return customTemplate; }
    public void setCustomTemplate(CustomInvoiceTemplate v) { customTemplate = v; }
    public String getTemplateNameSnapshot() { return templateNameSnapshot; }
    public void setTemplateNameSnapshot(String v) { templateNameSnapshot = v; }
    public String getTemplateStyleSnapshot() { return templateStyleSnapshot; }
    public void setTemplateStyleSnapshot(String v) { templateStyleSnapshot = v; }
    public String getTemplateAccentSnapshot() { return templateAccentSnapshot; }
    public void setTemplateAccentSnapshot(String v) { templateAccentSnapshot = v; }
    public Integer getTemplateVersionSnapshot() { return templateVersionSnapshot; }
    public void setTemplateVersionSnapshot(Integer v) { templateVersionSnapshot = v; }

    // --- presentation, always read through the snapshot taken when the invoice was issued. An invoice
    // issued before these columns existed falls back to the live template, exactly as the accent does.
    public String getPresentationFont() { return snapshotOr(templateFontSnapshot, customTemplate == null ? null : customTemplate.getFontKey()); }
    public void setTemplateFontSnapshot(String v) { templateFontSnapshot = v; }
    public String getPresentationDensity() { return snapshotOr(templateDensitySnapshot, customTemplate == null ? null : customTemplate.getLayoutDensity()); }
    public void setTemplateDensitySnapshot(String v) { templateDensitySnapshot = v; }
    public String getPaymentTerms() { return snapshotOr(templateTermsSnapshot, customTemplate == null ? null : customTemplate.getPaymentTerms()); }
    public void setTemplateTermsSnapshot(String v) { templateTermsSnapshot = v; }
    public String getFooterText() { return snapshotOr(templateFooterSnapshot, customTemplate == null ? null : customTemplate.getFooterText()); }
    public void setTemplateFooterSnapshot(String v) { templateFooterSnapshot = v; }
    public String getBankDetails() { return showsBankDetails() ? snapshotOr(templateBankSnapshot, customTemplate == null ? null : customTemplate.getBankDetails()) : null; }
    public void setTemplateBankSnapshot(String v) { templateBankSnapshot = v; }
    public String getSignatureLabel() { return snapshotOr(templateSignatureLabelSnapshot, customTemplate == null ? null : customTemplate.getSignatureLabel()); }
    public void setTemplateSignatureLabelSnapshot(String v) { templateSignatureLabelSnapshot = v; }
    public String getSignatureName() { return snapshotOr(templateSignatureNameSnapshot, customTemplate == null ? null : customTemplate.getSignatureName()); }
    public void setTemplateSignatureNameSnapshot(String v) { templateSignatureNameSnapshot = v; }
    public boolean printsLogo() { return templateShowLogoSnapshot == null
            ? (customTemplate == null || customTemplate.isShowLogo()) : templateShowLogoSnapshot; }
    public void setTemplateShowLogoSnapshot(Boolean v) { templateShowLogoSnapshot = v; }
    public Boolean getTemplateShowLogoSnapshot() { return templateShowLogoSnapshot; }
    public boolean showsBankDetails() { return templateBankSnapshot != null || (templateBankSnapshot == null && customTemplate != null && customTemplate.isShowBankDetails()); }
    /** Compact layouts tighten the table and the running text in both the print view and the PDF. */
    public boolean isCompactPresentation() { return "COMPACT".equals(getPresentationDensity()); }
    /** True when the invoice carries a signature block worth printing. */
    public boolean hasSignatureBlock() { return notBlank(getSignatureLabel()) || notBlank(getSignatureName()); }
    private static String snapshotOr(String snapshot, String live) { return notBlank(snapshot) ? snapshot : live; }
    private static boolean notBlank(String v) { return v != null && !v.isBlank(); }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { subtotal = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { taxAmount = v; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal v) { discountAmount = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { totalAmount = v; }
    public String getCompanyNameSnapshot() { return companyNameSnapshot; }
    public void setCompanyNameSnapshot(String v) { companyNameSnapshot = v; }
    public String getCompanyContactSnapshot() { return companyContactSnapshot; }
    public void setCompanyContactSnapshot(String v) { companyContactSnapshot = v; }
    public String getCompanyEmailSnapshot() { return companyEmailSnapshot; }
    public void setCompanyEmailSnapshot(String v) { companyEmailSnapshot = v; }
    public String getCompanyPhoneSnapshot() { return companyPhoneSnapshot; }
    public void setCompanyPhoneSnapshot(String v) { companyPhoneSnapshot = v; }
    public String getCompanyAddressSnapshot() { return companyAddressSnapshot; }
    public void setCompanyAddressSnapshot(String v) { companyAddressSnapshot = v; }
    public String getCompanyGstinSnapshot() { return companyGstinSnapshot; }
    public void setCompanyGstinSnapshot(String v) { companyGstinSnapshot = v; }
    public byte[] getCompanyLogoSnapshot() { return companyLogoSnapshot; }
    public void setCompanyLogoSnapshot(byte[] v) { companyLogoSnapshot = v == null ? null : v.clone(); }
    public String getCompanyLogoContentTypeSnapshot() { return companyLogoContentTypeSnapshot; }
    public void setCompanyLogoContentTypeSnapshot(String v) { companyLogoContentTypeSnapshot = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { paidAt = v; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant v) { cancelledAt = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant v) { generatedAt = v; }
    public boolean isUsageCounted() { return usageCounted; }
    public void setUsageCounted(boolean v) { usageCounted = v; }
    public CustomerSubscription getUsageSubscription() { return usageSubscription; }
    public void setUsageSubscription(CustomerSubscription v) { usageSubscription = v; }
    public List<InvoiceItem> getItems() { return items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

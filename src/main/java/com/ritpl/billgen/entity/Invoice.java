package com.ritpl.billgen.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        @Index(name = "idx_invoices_status_due", columnList = "status, due_date")
})
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "invoice_number", nullable = false, unique = true, length = 32)
    private String invoiceNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(nullable = false, length = 3) private String currency = "INR";
    @Column(name = "template_code", nullable = false, length = 20) private String templateCode = "CLASSIC";
    @Column(length = 1200) private String notes;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") private List<InvoiceItem> items = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public Invoice() { }
    public void addItem(InvoiceItem item) { items.add(item); item.setInvoice(this); }
    @PrePersist void onCreate() { LocalDateTime n = LocalDateTime.now(); createdAt = n; updatedAt = n; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public boolean isOverdue(LocalDate day) { return status == InvoiceStatus.SENT && dueDate.isBefore(day); }
    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String v) { invoiceNumber = v; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer v) { customer = v; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate v) { issueDate = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { dueDate = v; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus v) { status = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String v) { templateCode = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { subtotal = v; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal v) { taxAmount = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { totalAmount = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { paidAt = v; }
    public List<InvoiceItem> getItems() { return items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

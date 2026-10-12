package com.ritpl.billgen.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A line item snapshot that directly carries the owning customer/tenant id. */
@Entity
@Table(name = "invoice_items", indexes = {
        @Index(name = "idx_invoice_items_invoice", columnList = "invoice_id"),
        @Index(name = "idx_invoice_items_customer", columnList = "customer_id")
})
public class InvoiceItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent invoice; it must have the same customer owner as this row. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Direct tenant association for strict per-customer row isolation. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 240) private String description;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2) private BigDecimal taxRate;
    @Column(name = "line_subtotal", nullable = false, precision = 14, scale = 2) private BigDecimal lineSubtotal;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount;
    @Column(name = "line_total", nullable = false, precision = 14, scale = 2) private BigDecimal lineTotal;

    protected InvoiceItem() { }

    public InvoiceItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate,
                       BigDecimal lineSubtotal, BigDecimal taxAmount, BigDecimal lineTotal) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.taxRate = taxRate;
        this.lineSubtotal = lineSubtotal;
        this.taxAmount = taxAmount;
        this.lineTotal = lineTotal;
    }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice value) { invoice = value; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer value) { customer = value; }
    public Long getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getLineSubtotal() { return lineSubtotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
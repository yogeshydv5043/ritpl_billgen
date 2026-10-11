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
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_items")
public class InvoiceItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;
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
    public void setInvoice(Invoice v) { invoice = v; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getLineSubtotal() { return lineSubtotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
}

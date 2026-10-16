package com.ritpl.billgen.service;

import java.math.BigDecimal;

public record InvoiceLineInput(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate, Long productId) {
    public InvoiceLineInput(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {
        this(description, quantity, unitPrice, taxRate, null);
    }
}

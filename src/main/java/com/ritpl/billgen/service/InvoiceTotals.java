package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceTotals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount,
                            List<CalculatedLine> lines) {
    public record CalculatedLine(InvoiceLineInput input, BigDecimal subtotal, BigDecimal tax, BigDecimal total) { }
}

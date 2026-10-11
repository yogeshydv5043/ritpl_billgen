package com.ritpl.billgen.api;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.InvoiceItem;

public record InvoiceLineResponse(Long id,String description,BigDecimal quantity,BigDecimal unitPrice,
                                  BigDecimal taxRate,BigDecimal subtotal,BigDecimal taxAmount,BigDecimal total) {
    public static InvoiceLineResponse from(InvoiceItem line){return new InvoiceLineResponse(line.getId(),line.getDescription(),line.getQuantity(),line.getUnitPrice(),line.getTaxRate(),line.getLineSubtotal(),line.getTaxAmount(),line.getLineTotal());}
}

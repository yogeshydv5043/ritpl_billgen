package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.ritpl.billgen.entity.InvoiceStatus;

public record CreateInvoiceCommand(Long customerId, LocalDate issueDate, LocalDate dueDate, String notes,
        String currency, Long templateId, List<InvoiceLineInput> lines, InvoiceStatus initialStatus,
        Long customTemplateId, BigDecimal discountAmount, String paymentTerms) {

    /** The document as the API and the demo seeder build it: no per-invoice override of the terms text. */
    public CreateInvoiceCommand(Long customerId, LocalDate issueDate, LocalDate dueDate, String notes,
            String currency, Long templateId, List<InvoiceLineInput> lines, InvoiceStatus initialStatus,
            Long customTemplateId, BigDecimal discountAmount) {
        this(customerId, issueDate, dueDate, notes, currency, templateId, lines, initialStatus, customTemplateId,
                discountAmount, null);
    }

    public CreateInvoiceCommand(Long customerId, LocalDate issueDate, LocalDate dueDate, String notes,
            String currency, Long templateId, List<InvoiceLineInput> lines, InvoiceStatus initialStatus) {
        this(customerId, issueDate, dueDate, notes, currency, templateId, lines, initialStatus, null, BigDecimal.ZERO, null);
    }

    public CreateInvoiceCommand {
        discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        paymentTerms = paymentTerms == null || paymentTerms.isBlank() ? null : paymentTerms.trim();
    }
}

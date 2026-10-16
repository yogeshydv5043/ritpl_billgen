package com.ritpl.billgen.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;

public record InvoiceResponse(Long id, String invoiceNumber, CustomerSummary customer, LocalDate issueDate,
        LocalDate dueDate, InvoiceStatus status, String currency, Long templateId, String templateCode,
        String templateName, Long customTemplateId, String notes, BigDecimal subtotal, BigDecimal taxAmount,
        BigDecimal discountAmount, BigDecimal totalAmount, LocalDateTime paidAt, Instant generatedAt,
        boolean usageCounted, List<InvoiceLineResponse> lines) {
    public record CustomerSummary(Long id, String companyName, String contactName, String email) { }
    public static InvoiceResponse from(Invoice i) {
        var c = i.getCustomer();
        return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), new CustomerSummary(c.getId(), c.getCompanyName(), c.getContactName(), c.getEmail()),
                i.getIssueDate(), i.getDueDate(), i.getStatus(), i.getCurrency(), i.getTemplate() == null ? null : i.getTemplate().getId(),
                i.getTemplateCode(), i.getTemplateName(), i.getCustomTemplate() == null ? null : i.getCustomTemplate().getId(),
                i.getNotes(), i.getSubtotal(), i.getTaxAmount(), i.getDiscountAmount(), i.getTotalAmount(), i.getPaidAt(),
                i.getGeneratedAt(), i.isUsageCounted(), i.getItems().stream().map(InvoiceLineResponse::from).toList());
    }
}

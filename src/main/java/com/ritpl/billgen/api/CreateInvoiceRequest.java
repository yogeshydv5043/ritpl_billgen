package com.ritpl.billgen.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInvoiceRequest(
        Long customerId,
        @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate issueDate,
        @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dueDate,
        @Size(max=1200) String notes,
        @Pattern(regexp="[A-Za-z]{3}") String currency,
        Long templateId,
        Long customTemplateId,
        @NotNull InvoiceStatus initialStatus,
        @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal discountAmount,
        @Valid @NotEmpty @Size(max=30) List<InvoiceLineRequest> lines) {
    public CreateInvoiceCommand toCommand() {
        return new CreateInvoiceCommand(customerId, issueDate, dueDate, notes, currency, templateId,
                lines.stream().map(InvoiceLineRequest::toInput).collect(Collectors.toList()), initialStatus,
                customTemplateId, discountAmount);
    }
}

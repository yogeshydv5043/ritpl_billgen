package com.ritpl.billgen.service;

import java.time.LocalDate;
import java.util.List;
import com.ritpl.billgen.entity.InvoiceStatus;

public record CreateInvoiceCommand(Long customerId, LocalDate issueDate, LocalDate dueDate, String notes,
                                   String currency, String templateCode, List<InvoiceLineInput> lines,
                                   InvoiceStatus initialStatus) { }

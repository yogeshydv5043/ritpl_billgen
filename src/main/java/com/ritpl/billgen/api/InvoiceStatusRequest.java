package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.InvoiceStatus;
import jakarta.validation.constraints.NotNull;

public record InvoiceStatusRequest(@NotNull InvoiceStatus status) { }

package com.ritpl.billgen.service;

import java.math.BigDecimal;

public record InvoiceLineInput(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) { }

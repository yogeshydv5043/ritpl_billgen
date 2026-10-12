package com.ritpl.billgen.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Customer-wise statistics for the administrator dashboard: what each account has generated and
 * which plan it is on.
 */
public record CustomerAccountStats(Long customerId,
                                   String companyName,
                                   String contactName,
                                   String email,
                                   String phone,
                                   String city,
                                   String loginEmail,
                                   boolean loginEnabled,
                                   Long planId,
                                   String planName,
                                   String planCode,
                                   String subscriptionStatus,
                                   LocalDate subscriptionStart,
                                   LocalDate subscriptionEnd,
                                   long invoiceCount,
                                   long productCount,
                                   long categoryCount,
                                   BigDecimal totalBilled,
                                   LocalDate lastInvoiceDate,
                                   java.time.LocalDateTime createdAt) implements Serializable {

    public boolean hasPlan() { return planName != null; }
}
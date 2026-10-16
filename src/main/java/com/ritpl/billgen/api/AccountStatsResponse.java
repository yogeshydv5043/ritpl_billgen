package com.ritpl.billgen.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.ritpl.billgen.service.CustomerAccountStats;

public record AccountStatsResponse(Long customerId, String companyName, String contactName, String email,
        String phone, String city, String loginEmail, boolean loginEnabled, Long planId, String planName,
        String planCode, String subscriptionStatus, LocalDate subscriptionStart, LocalDate subscriptionEnd,
        long invoiceCount, long invoiceLimit, long invoicesUsed, long remainingInvoices,
        long productCount, long productLimit, long customTemplateCount, long customTemplateLimit,
        long categoryCount, BigDecimal totalBilled, LocalDate lastInvoiceDate, java.time.LocalDateTime createdAt) {
    public static AccountStatsResponse from(CustomerAccountStats s) {
        return new AccountStatsResponse(s.customerId(), s.companyName(), s.contactName(), s.email(), s.phone(),
                s.city(), s.loginEmail(), s.loginEnabled(), s.planId(), s.planName(), s.planCode(), s.subscriptionStatus(),
                s.subscriptionStart(), s.subscriptionEnd(), s.invoiceCount(), s.invoiceLimit(), s.invoicesUsed(), s.remainingInvoices(),
                s.productCount(), s.productLimit(), s.customTemplateCount(), s.customTemplateLimit(), s.categoryCount(),
                s.totalBilled(), s.lastInvoiceDate(), s.createdAt());
    }
}

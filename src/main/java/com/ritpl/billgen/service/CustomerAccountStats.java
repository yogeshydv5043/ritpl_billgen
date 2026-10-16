package com.ritpl.billgen.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerAccountStats(Long customerId, String companyName, String contactName, String email,
        String phone, String city, String loginEmail, boolean loginEnabled, Long planId, String planName,
        String planCode, String subscriptionStatus, LocalDate subscriptionStart, LocalDate subscriptionEnd,
        long invoiceCount, long invoiceLimit, long invoicesUsed, long remainingInvoices,
        long productCount, long productLimit, long customTemplateCount, long customTemplateLimit,
        long categoryCount, BigDecimal totalBilled, LocalDate lastInvoiceDate,
        java.time.LocalDateTime createdAt, String accountStatus, boolean deleted,
        java.time.Instant deletedAt, String deletedBy, String deletionReason) implements Serializable {
    public boolean hasPlan() { return planName != null; }
    public boolean isActiveAccount() { return "Active".equals(accountStatus); }
    /** Days until the current term ends; negative once it has expired. */
    public Long daysToExpiry() {
        return subscriptionEnd == null ? null : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), subscriptionEnd);
    }
    public boolean expiringSoon() {
        Long days = daysToExpiry();
        return days != null && days >= 0 && days <= 7;
    }
}

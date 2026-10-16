package com.ritpl.billgen.service;

public record UsageQuota(long invoiceLimit, long invoicesUsed, long remainingInvoices,
        long productLimit, long productsUsed, long remainingProducts,
        long customTemplateLimit, long customTemplatesUsed, long remainingCustomTemplates,
        java.time.LocalDate startsOn, java.time.LocalDate endsOn) { }

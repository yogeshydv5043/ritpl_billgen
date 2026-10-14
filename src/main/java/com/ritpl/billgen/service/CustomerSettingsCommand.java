package com.ritpl.billgen.service;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.AuditHistoryPeriod;

/**
 * What a customer account submits on its settings screen. Boxed numbers so "the field was left empty"
 * can be told apart from zero, which matters for the optional tax preference.
 */
public record CustomerSettingsCommand(Long defaultTemplateId, Long defaultCustomTemplateId, String defaultCurrency,
        String invoiceNumberPrefix, Integer defaultDueDays, BigDecimal defaultTaxRate, String defaultPaymentTerms,
        String defaultNotes, String defaultFooter, String defaultUnit, Long defaultCategoryId, Integer listPageSize,
        AuditHistoryPeriod auditHistoryPeriod, boolean notifyExpiryReminders, boolean notifyQuotaAlerts,
        boolean notifyPaymentUpdates, boolean notifySecurityAlerts, Integer exportRangeDays) { }

package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import com.ritpl.billgen.entity.BillingCycle;
import com.ritpl.billgen.entity.OverLimitBehavior;

/**
 * Everything a Super Admin configures on a plan: price, the entitlement limits, which invoice
 * templates and audit types it unlocks, the usage period, over-limit behaviour, renewal rules and
 * expiry reminders.
 */
public record PlanCommand(String code, String name, String tagline, String description,
                          BigDecimal monthlyPrice, BigDecimal annualPrice, String currency,
                          int maxCustomers, int maxMonthlyInvoices, int maxProducts, int maxCustomTemplates,
                          String features, boolean featured, boolean active, int sortOrder,
                          Set<Long> templateIds,
                          BillingCycle billingCycle, Integer billingPeriodDays, int gracePeriodDays,
                          OverLimitBehavior overLimitBehavior, int usageWarningPercent, boolean notifyAtWarning,
                          boolean notifyAtLimit, Integer auditDailyLimit, Integer auditMonthlyLimit,
                          Integer auditYearlyLimit, int auditDefaultRetentionDays, boolean autoRenewalAllowed,
                          boolean allowDowngrade, boolean allowMidtermUpgrade, String expiryReminderDays,
                          Integer customPeriodDays, java.time.LocalDate planStartOn, java.time.LocalDate planEndOn,
                          Set<Long> auditTypeIds) {
    public PlanCommand {
        templateIds = templateIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(templateIds));
        auditTypeIds = auditTypeIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(auditTypeIds));
        billingCycle = billingCycle == null ? BillingCycle.MONTHLY : billingCycle;
        overLimitBehavior = overLimitBehavior == null ? OverLimitBehavior.BLOCK : overLimitBehavior;
        usageWarningPercent = usageWarningPercent < 1 || usageWarningPercent > 100 ? 80 : usageWarningPercent;
        auditDefaultRetentionDays = auditDefaultRetentionDays < 1 ? 730 : auditDefaultRetentionDays;
        expiryReminderDays = expiryReminderDays == null || expiryReminderDays.isBlank() ? "30,7,3,1" : expiryReminderDays;
    }

    /** The configuration set used before the Super Admin plan editor existed. */
    public PlanCommand(String code, String name, String tagline, String description, BigDecimal monthlyPrice,
            BigDecimal annualPrice, String currency, int maxCustomers, int maxMonthlyInvoices, int maxProducts,
            int maxCustomTemplates, String features, boolean featured, boolean active, int sortOrder,
            Set<Long> templateIds) {
        this(code, name, tagline, description, monthlyPrice, annualPrice, currency, maxCustomers, maxMonthlyInvoices,
                maxProducts, maxCustomTemplates, features, featured, active, sortOrder, templateIds,
                BillingCycle.MONTHLY, null, 0, OverLimitBehavior.BLOCK, 80, true, true, null, null, null, 730,
                true, true, true, "30,7,3,1", null, null, null, Set.of());
    }
}

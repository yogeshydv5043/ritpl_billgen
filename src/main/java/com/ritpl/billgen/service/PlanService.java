package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AuditType;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillingCycle;
import com.ritpl.billgen.entity.OverLimitBehavior;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.repository.AuditTypeRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;

/**
 * Subscription plans. Each plan carries the set of bill templates a customer unlocks when it
 * purchases that plan, which is the only way template access is granted.
 */
@Service
public class PlanService {
    private final SubscriptionPlanRepository plans;
    private final TemplateService templates;
    private final AuditTypeRepository auditTypes;
    private final AuditService audit;

    public PlanService(SubscriptionPlanRepository plans, TemplateService templates, AuditTypeRepository auditTypes,
            AuditService audit) {
        this.plans = plans; this.templates = templates; this.auditTypes = auditTypes; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> findAll() { return plans.findAllByOrderBySortOrderAscNameAsc(); }

    /** Live plans for the Super Admin listing (soft-deleted plans are hidden). */
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> findLive() { return plans.findByDeletedAtIsNullOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    public List<AuditType> permittedAuditTypes(SubscriptionPlan plan) {
        return plan == null ? List.of() : plan.getAuditTypes().stream()
                .filter(t -> t.isActive() && !t.isDeleted())
                .sorted(java.util.Comparator.comparing(AuditType::getEventCategory).thenComparing(AuditType::getName))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "active-plans")
    public List<SubscriptionPlan> findActive() { return plans.findByActiveTrueOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    public SubscriptionPlan findById(long id) {
        return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found."));
    }

    @Transactional(readOnly = true)
    public long assignedCustomerCount(long planId) { return plans.countAssignedCustomers(planId); }

    @Transactional(readOnly = true)
    public long activeSubscriberCount(long planId) { return plans.countActiveSubscriptions(planId); }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public SubscriptionPlan save(Long id, PlanCommand command) { return save(id, command, "admin"); }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public SubscriptionPlan save(Long id, PlanCommand command, String actor) {
        validate(command);
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = id == null ? plans.existsByCodeIgnoreCase(code) : plans.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicate) throw new IllegalArgumentException("That plan code is already in use.");
        String currency = command.currency() == null || command.currency().isBlank()
                ? "INR" : command.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a 3-letter code.");
        SubscriptionPlan plan = id == null
                ? new SubscriptionPlan(code, command.name().trim(), blankToNull(command.tagline()), blankToNull(command.description()),
                        command.monthlyPrice().setScale(2), command.annualPrice().setScale(2), currency,
                        command.maxCustomers(), command.maxMonthlyInvoices(), command.features().trim(),
                        command.featured(), command.sortOrder())
                : findById(id);
        Map<String, Object> before = id == null ? null : Map.of("name", plan.getName(), "active", plan.isActive(),
                "maxInvoices", plan.getMaxMonthlyInvoices(), "maxProducts", plan.getMaxProducts());
        plan.setCode(code); plan.setName(command.name().trim());
        plan.setTagline(blankToNull(command.tagline())); plan.setDescription(blankToNull(command.description()));
        plan.setMonthlyPrice(command.monthlyPrice().setScale(2)); plan.setAnnualPrice(command.annualPrice().setScale(2));
        plan.setCurrency(currency);
        plan.setMaxCustomers(command.maxCustomers()); plan.setMaxMonthlyInvoices(command.maxMonthlyInvoices());
        plan.setMaxProducts(command.maxProducts()); plan.setMaxCustomTemplates(command.maxCustomTemplates());
        plan.setFeatures(command.features().trim());
        plan.setFeatured(command.featured()); plan.setActive(command.active()); plan.setSortOrder(command.sortOrder());
        plan.setBillingCycle(command.billingCycle());
        plan.setBillingPeriodDays(command.billingPeriodDays());
        plan.setGracePeriodDays(command.gracePeriodDays());
        plan.setOverLimitBehavior(command.overLimitBehavior());
        plan.setUsageWarningPercent(command.usageWarningPercent());
        plan.setNotifyAtWarning(command.notifyAtWarning());
        plan.setNotifyAtLimit(command.notifyAtLimit());
        plan.setAuditDailyLimit(command.auditDailyLimit());
        plan.setAuditMonthlyLimit(command.auditMonthlyLimit());
        plan.setAuditYearlyLimit(command.auditYearlyLimit());
        plan.setAuditDefaultRetentionDays(command.auditDefaultRetentionDays());
        plan.setAutoRenewalAllowed(command.autoRenewalAllowed());
        plan.setAllowDowngrade(command.allowDowngrade());
        plan.setAllowMidtermUpgrade(command.allowMidtermUpgrade());
        plan.setExpiryReminderDays(normaliseReminders(command.expiryReminderDays()));
        plan.setCustomPeriodDays(command.customPeriodDays());
        plan.setPlanStartOn(command.planStartOn());
        plan.setPlanEndOn(command.planEndOn());
        plan.setUpdatedBy(actor == null ? "admin" : actor);
        plan.setTemplates(resolveTemplates(command.templateIds()));
        plan.setAuditTypes(resolveAuditTypes(command.auditTypeIds()));
        SubscriptionPlan saved = plans.save(plan);
        audit.record(AuditEventCommand.success(null, id == null ? "CREATE" : "UPDATE", "PLAN", "Subscription plan", saved.getId(),
                id == null ? "Admin created a subscription plan." : "Admin updated a subscription plan.", before,
                Map.of("name", saved.getName(), "active", saved.isActive(), "maxInvoices", saved.getMaxMonthlyInvoices(),
                        "maxProducts", saved.getMaxProducts())));
        return saved;
    }

    /**
     * Toggles availability for new subscribers only. Existing customer subscriptions are never
     * terminated by deactivating a plan: they keep their entitlement until the term ends, which is
     * the grandfathering rule the product relies on. The count of grandfathered accounts is
     * returned so the console can state it explicitly.
     */
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public long setActive(long id, boolean active) {
        SubscriptionPlan plan = findById(id);
        if (active && plan.getPlanEndOn() != null && plan.getPlanEndOn().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("This plan's sale period ended on " + plan.getPlanEndOn()
                    + ". Extend it before making it available again.");
        }
        boolean before = plan.isActive(); plan.setActive(active);
        long grandfathered = plans.countActiveSubscriptions(id);
        audit.record(AuditEventCommand.success(null, "UPDATE_STATUS", "PLAN", "Subscription plan", id,
                active ? "Admin made a plan available to new subscribers."
                        : "Admin deactivated a plan for new subscribers; existing subscriptions continue unchanged.",
                Map.of("active", before),
                Map.of("active", active, "grandfatheredSubscriptions", grandfathered)));
        return grandfathered;
    }

    /** Retires a plan. Refused while accounts still hold it, so nothing is orphaned by accident. */
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public void softDelete(long id, String actor) {
        SubscriptionPlan plan = findById(id);
        long active = plans.countActiveSubscriptions(id);
        if (active > 0) {
            throw new IllegalArgumentException("This plan is the active subscription of " + active
                    + " account(s). Move them to another plan, or deactivate the plan to grandfather them.");
        }
        plan.setDeletedAt(java.time.Instant.now());
        plan.setDeletedBy(actor == null ? "admin" : actor);
        plan.setActive(false);
        audit.record(AuditEventCommand.success(null, "DELETE", "PLAN", "Subscription plan", id,
                "Admin retired a subscription plan; its historical subscriptions were preserved.",
                Map.of("name", plan.getName()), Map.of("deleted", true)));
    }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public SubscriptionPlan setAuditTypes(long id, Set<Long> auditTypeIds) {
        SubscriptionPlan plan = findById(id);
        int beforeCount = plan.getAuditTypes().size();
        plan.setAuditTypes(resolveAuditTypes(auditTypeIds));
        SubscriptionPlan saved = plans.save(plan);
        audit.record(AuditEventCommand.success(null, "UPDATE_AUDIT_TYPES", "PLAN", "Subscription plan", id,
                "Admin changed the audit types included in a plan.", Map.of("auditTypeCount", beforeCount),
                Map.of("auditTypeCount", saved.getAuditTypes().size())));
        return saved;
    }

    /** Replaces the templates bundled with a plan without touching the rest of the plan. */
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public SubscriptionPlan setTemplates(long id, Set<Long> templateIds) {
        SubscriptionPlan plan = findById(id);
        int beforeCount = plan.getTemplates().size();
        plan.setTemplates(resolveTemplates(templateIds));
        SubscriptionPlan saved = plans.save(plan);
        audit.record(AuditEventCommand.success(null, "UPDATE_TEMPLATES", "PLAN", "Subscription plan", id,
                "Admin changed the templates included in a plan.", Map.of("templateCount", beforeCount),
                Map.of("templateCount", saved.getTemplates().size())));
        return saved;
    }

    /** Only a live audit definition may be granted to a plan. */
    private Set<AuditType> resolveAuditTypes(Set<Long> auditTypeIds) {
        Set<AuditType> resolved = new LinkedHashSet<>();
        if (auditTypeIds == null || auditTypeIds.isEmpty()) return resolved;
        for (Long id : auditTypeIds) {
            AuditType type = id == null ? null : auditTypes.findById(id).orElse(null);
            if (type == null || !type.isActive() || type.isDeleted()) {
                throw new IllegalArgumentException("One of the selected audit types is not available.");
            }
            resolved.add(type);
        }
        return resolved;
    }

    private String normaliseReminders(String value) {
        if (value == null || value.isBlank()) return "30,7,3,1";
        String cleaned = java.util.Arrays.stream(value.split(",")).map(String::trim)
                .filter(v -> v.matches("\d{1,3}")).map(v -> Math.min(365, Integer.parseInt(v)))
                .distinct().sorted(java.util.Comparator.reverseOrder())
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        if (cleaned.isBlank()) throw new IllegalArgumentException("Expiry reminders must be a comma separated list of day offsets.");
        return cleaned;
    }

    private Set<BillTemplate> resolveTemplates(Set<Long> templateIds) {
        Set<BillTemplate> resolved = new LinkedHashSet<>();
        if (templateIds == null || templateIds.isEmpty()) return resolved;
        for (BillTemplate template : templates.findByIds(templateIds)) {
            if (template.isActive()) resolved.add(template);
        }
        if (resolved.size() != templateIds.size()) {
            throw new IllegalArgumentException("One of the selected templates is not available.");
        }
        return resolved;
    }

    private void validate(PlanCommand c) {
        if (c == null || c.code() == null || c.code().isBlank() || c.code().trim().length() > 40
                || c.name() == null || c.name().isBlank() || c.name().trim().length() > 100
                || c.features() == null || c.features().isBlank()) {
            throw new IllegalArgumentException("Enter a plan code, name and at least one feature.");
        }
        if (c.monthlyPrice() == null || c.monthlyPrice().signum() < 0 || c.monthlyPrice().scale() > 2
                || c.annualPrice() == null || c.annualPrice().signum() < 0 || c.annualPrice().scale() > 2) {
            throw new IllegalArgumentException("Plan prices must be non-negative with at most 2 decimal places.");
        }
        if (c.maxCustomers() < 1 || c.maxMonthlyInvoices() < 1 || c.maxProducts() < 1 || c.maxCustomTemplates() < 0)
            throw new IllegalArgumentException("Plan limits must be positive; custom template limits may be zero.");
        if (c.templateIds() != null && c.templateIds().isEmpty()) {
            throw new IllegalArgumentException("Add at least one bill template to the plan.");
        }
        if (c.billingCycle() == BillingCycle.CUSTOM && (c.billingPeriodDays() == null
                || c.billingPeriodDays() < 1 || c.billingPeriodDays() > 3650)) {
            throw new IllegalArgumentException("A custom billing cycle needs a period length in days.");
        }
        if (c.gracePeriodDays() < 0 || c.gracePeriodDays() > 180) {
            throw new IllegalArgumentException("A grace period must be between 0 and 180 days.");
        }
        if (c.usageWarningPercent() < 1 || c.usageWarningPercent() > 100) {
            throw new IllegalArgumentException("The usage warning threshold must be between 1 and 100 percent.");
        }
        if (c.overLimitBehavior() == OverLimitBehavior.WARN && !c.notifyAtWarning() && !c.notifyAtLimit()) {
            throw new IllegalArgumentException("A plan that warns instead of blocking must notify on at least one threshold.");
        }
        if (c.auditDefaultRetentionDays() < 1) {
            throw new IllegalArgumentException("Audit retention must be at least one day.");
        }
        if (c.planStartOn() != null && c.planEndOn() != null && c.planEndOn().isBefore(c.planStartOn())) {
            throw new IllegalArgumentException("A plan's sale period cannot end before it starts.");
        }
        if (!c.allowDowngrade() && !c.allowMidtermUpgrade() && !c.autoRenewalAllowed()) {
            throw new IllegalArgumentException("Allow at least one renewal or change rule; otherwise no subscriber could change plan.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
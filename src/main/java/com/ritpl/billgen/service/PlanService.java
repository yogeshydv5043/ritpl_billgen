package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;

/**
 * Subscription plans. Each plan carries the set of bill templates a customer unlocks when it
 * purchases that plan, which is the only way template access is granted.
 */
@Service
public class PlanService {
    private final SubscriptionPlanRepository plans;
    private final TemplateService templates;

    public PlanService(SubscriptionPlanRepository plans, TemplateService templates) {
        this.plans = plans; this.templates = templates;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> findAll() { return plans.findAllByOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "active-plans")
    public List<SubscriptionPlan> findActive() { return plans.findByActiveTrueOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    public SubscriptionPlan findById(long id) {
        return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found."));
    }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public SubscriptionPlan save(Long id, PlanCommand command) {
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
        plan.setCode(code); plan.setName(command.name().trim());
        plan.setTagline(blankToNull(command.tagline())); plan.setDescription(blankToNull(command.description()));
        plan.setMonthlyPrice(command.monthlyPrice().setScale(2)); plan.setAnnualPrice(command.annualPrice().setScale(2));
        plan.setCurrency(currency);
        plan.setMaxCustomers(command.maxCustomers()); plan.setMaxMonthlyInvoices(command.maxMonthlyInvoices());
        plan.setFeatures(command.features().trim());
        plan.setFeatured(command.featured()); plan.setActive(command.active()); plan.setSortOrder(command.sortOrder());
        plan.setTemplates(resolveTemplates(command.templateIds()));
        return plans.save(plan);
    }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public void setActive(long id, boolean active) { findById(id).setActive(active); }

    /** Replaces the templates bundled with a plan without touching the rest of the plan. */
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public SubscriptionPlan setTemplates(long id, Set<Long> templateIds) {
        SubscriptionPlan plan = findById(id);
        plan.setTemplates(resolveTemplates(templateIds));
        return plans.save(plan);
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
        if (c.maxCustomers() < 1 || c.maxMonthlyInvoices() < 1) throw new IllegalArgumentException("Plan usage limits must be at least 1.");
        if (c.templateIds() != null && c.templateIds().isEmpty()) {
            throw new IllegalArgumentException("Add at least one bill template to the plan.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;

@Service
public class PlanService {
    private final SubscriptionPlanRepository plans;
    public PlanService(SubscriptionPlanRepository plans) { this.plans = plans; }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> findAll() { return plans.findAllByOrderBySortOrderAscNameAsc(); }
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "active-plans")
    public List<SubscriptionPlan> findActive() { return plans.findByActiveTrueOrderBySortOrderAscNameAsc(); }
    @Transactional(readOnly = true)
    public SubscriptionPlan findById(long id) { return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found.")); }
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "dashboard"}, allEntries = true)
    public SubscriptionPlan save(Long id, PlanCommand c) {
        validate(c);
        String code = c.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = id == null ? plans.existsByCodeIgnoreCase(code) : plans.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicate) throw new IllegalArgumentException("That plan code is already in use.");
        String currency = c.currency() == null || c.currency().isBlank() ? "INR" : c.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a 3-letter code.");
        SubscriptionPlan p = id == null
                ? new SubscriptionPlan(code, c.name().trim(), blankToNull(c.tagline()), blankToNull(c.description()), c.monthlyPrice().setScale(2), c.annualPrice().setScale(2), currency, c.maxCustomers(), c.maxMonthlyInvoices(), c.features().trim(), c.featured(), c.sortOrder())
                : findById(id);
        p.setCode(code); p.setName(c.name().trim()); p.setTagline(blankToNull(c.tagline())); p.setDescription(blankToNull(c.description()));
        p.setMonthlyPrice(c.monthlyPrice().setScale(2)); p.setAnnualPrice(c.annualPrice().setScale(2)); p.setCurrency(currency);
        p.setMaxCustomers(c.maxCustomers()); p.setMaxMonthlyInvoices(c.maxMonthlyInvoices()); p.setFeatures(c.features().trim());
        p.setFeatured(c.featured()); p.setActive(c.active()); p.setSortOrder(c.sortOrder());
        return plans.save(p);
    }
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "dashboard"}, allEntries = true)
    public void setActive(long id, boolean active) { findById(id).setActive(active); }

    private void validate(PlanCommand c) {
        if (c == null || c.code() == null || c.code().isBlank() || c.code().trim().length() > 40
                || c.name() == null || c.name().isBlank() || c.name().trim().length() > 100
                || c.features() == null || c.features().isBlank()) throw new IllegalArgumentException("Enter a plan code, name and at least one feature.");
        if (c.monthlyPrice() == null || c.monthlyPrice().signum() < 0 || c.monthlyPrice().scale() > 2
                || c.annualPrice() == null || c.annualPrice().signum() < 0 || c.annualPrice().scale() > 2)
            throw new IllegalArgumentException("Plan prices must be non-negative with at most 2 decimal places.");
        if (c.maxCustomers() < 1 || c.maxMonthlyInvoices() < 1) throw new IllegalArgumentException("Plan usage limits must be at least 1.");
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

package com.ritpl.billgen.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.entity.SubscriptionStatus;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Plans, purchases and template entitlements.
 *
 * <p>A customer account unlocks exactly the templates of the plan it purchased. This service is
 * the single place that answers "which templates may this account use", and both the web and the
 * JSON API call it before an invoice is written, so a template that is not part of the plan can
 * never be attached to an invoice.</p>
 */
@Service
public class SubscriptionService {

    private static final Comparator<BillTemplate> TEMPLATE_ORDER =
            Comparator.comparingInt(BillTemplate::getSortOrder).thenComparing(BillTemplate::getName);

    private final CustomerSubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final SubscriptionPlanRepository plans;
    private final TemplateService templates;

    public SubscriptionService(CustomerSubscriptionRepository subscriptions, CustomerRepository customers,
            SubscriptionPlanRepository plans, TemplateService templates) {
        this.subscriptions = subscriptions; this.customers = customers; this.plans = plans; this.templates = templates;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerSubscription> findActiveSubscription(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return subscriptions.findFirstByCustomer_IdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .filter(subscription -> subscription.isEffective(java.time.LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<CustomerSubscription> findHistory(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return subscriptions.findHistoryForCustomer(customerId);
    }

    /**
     * The templates the given scope is allowed to issue invoices with. Administrators see the
     * whole catalogue; a customer only ever sees the templates bundled with its purchased plan.
     */
    @Transactional(readOnly = true)
    public List<BillTemplate> accessibleTemplates(TenantScope scope) {
        if (scope.isAdminScope()) return templates.findActive();
        return findActiveSubscription(scope, scope.requireCustomerId())
                .filter(subscription -> subscription.getPlan().isActive())
                .map(subscription -> subscription.getPlan().getTemplates().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(BillTemplate::isActive)
                        .sorted(TEMPLATE_ORDER)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Resolves the template for an invoice, rejecting anything outside the purchased plan.
     *
     * @throws IllegalArgumentException when the template is not part of the account's plan
     */
    @Transactional(readOnly = true)
    public BillTemplate requireAccessibleTemplate(TenantScope scope, Long templateId) {
        if (templateId == null) throw new IllegalArgumentException("Choose a bill template.");
        return accessibleTemplates(scope).stream()
                .filter(template -> template.getId().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose a template that is included in your plan."));
    }

    /**
     * Purchases (or switches) the plan of a customer account. A customer may only act for its own
     * account; an administrator may act for any account.
     */
    @Transactional
    @CacheEvict(cacheNames = {"customer-templates", "dashboard"}, allEntries = true)
    public CustomerSubscription activate(TenantScope scope, long customerId, long planId, String actor) {
        scope.requireCustomerAccess(customerId);
        Customer customer = customers.findById(customerId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        SubscriptionPlan plan = plans.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a valid plan."));
        if (!plan.isActive()) throw new IllegalArgumentException("That plan is no longer available.");
        cancelCurrentSubscription(customerId);
        // Flush the old ACTIVE -> CANCELLED transition before inserting a new ACTIVE row; the
        // partial unique index enforces one active subscription per account.
        subscriptions.flush();
        CustomerSubscription subscription = new CustomerSubscription(customer, plan, SubscriptionStatus.ACTIVE, actor);
        return subscriptions.saveAndFlush(subscription);
    }

    /** Cancels the active plan of an account, which immediately withdraws its template access. */
    @Transactional
    @CacheEvict(cacheNames = {"customer-templates", "dashboard"}, allEntries = true)
    public void cancel(TenantScope scope, long customerId, String actor) {
        scope.requireCustomerAccess(customerId);
        CustomerSubscription current = subscriptions.findFirstByCustomer_IdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("This account has no active plan."));
        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setAssignedBy(actor);
    }

    @Transactional(readOnly = true)
    public long subscriberCount(TenantScope scope, long planId) {
        if (!scope.isAdminScope()) throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        return subscriptions.countByPlan_IdAndStatus(planId, SubscriptionStatus.ACTIVE);
    }

    private void cancelCurrentSubscription(long customerId) {
        subscriptions.findFirstByCustomer_IdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .ifPresent(current -> current.setStatus(SubscriptionStatus.CANCELLED));
    }
}
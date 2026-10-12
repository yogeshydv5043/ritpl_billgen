package com.ritpl.billgen.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.entity.SubscriptionStatus;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.CustomInvoiceTemplateRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;
import com.ritpl.billgen.security.TenantScope;

@Service
public class SubscriptionService {
    private static final Comparator<BillTemplate> TEMPLATE_ORDER = Comparator.comparingInt(BillTemplate::getSortOrder).thenComparing(BillTemplate::getName);
    private final CustomerSubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final SubscriptionPlanRepository plans;
    private final TemplateService templates;
    private final InvoiceRepository invoices;
    private final CatalogProductRepository products;
    private final CustomInvoiceTemplateRepository customTemplates;
    private final AuditService audit;
    private final ZoneId zone;

    public SubscriptionService(CustomerSubscriptionRepository subscriptions, CustomerRepository customers,
            SubscriptionPlanRepository plans, TemplateService templates, InvoiceRepository invoices,
            CatalogProductRepository products, CustomInvoiceTemplateRepository customTemplates, AuditService audit,
            @Value("${app.subscription.time-zone:Asia/Kolkata}") String zoneId) {
        this.subscriptions = subscriptions; this.customers = customers; this.plans = plans; this.templates = templates;
        this.invoices = invoices; this.products = products; this.customTemplates = customTemplates; this.audit = audit;
        this.zone = ZoneId.of(zoneId);
    }

    public LocalDate today() { return LocalDate.now(zone); }
    public ZoneId zone() { return zone; }

    @Transactional(readOnly = true)
    public Optional<CustomerSubscription> findActiveSubscription(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return subscriptions.findFirstByCustomer_IdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .filter(subscription -> subscription.isEffective(today()) && subscription.getPlan().isActive());
    }

    @Transactional(readOnly = true)
    public List<CustomerSubscription> findHistory(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return subscriptions.findHistoryForCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public List<BillTemplate> accessibleTemplates(TenantScope scope) {
        if (scope.isAdminScope()) return templates.findActive();
        return findActiveSubscription(scope, scope.requireCustomerId())
                .map(subscription -> subscription.getPlan().getTemplates().stream().filter(java.util.Objects::nonNull)
                        .filter(BillTemplate::isActive).sorted(TEMPLATE_ORDER).toList()).orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public BillTemplate requireAccessibleTemplate(TenantScope scope, Long templateId) {
        if (templateId == null) throw new IllegalArgumentException("Choose a bill template.");
        return accessibleTemplates(scope).stream().filter(template -> template.getId().equals(templateId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose a template that is included in your plan."));
    }

    /** Locks the active row so competing invoice/product/template writes cannot overspend a limit. */
    @Transactional
    public CustomerSubscription requireActiveForUpdate(TenantScope scope) {
        long customerId = scope.requireCustomerId();
        CustomerSubscription current = subscriptions.findForUpdate(customerId, SubscriptionStatus.ACTIVE)
                .filter(s -> s.isEffective(today()) && s.getPlan().isActive())
                .orElseThrow(() -> new IllegalArgumentException("Choose or renew an active plan before creating billing data."));
        return current;
    }

    @Transactional(readOnly = true)
    public UsageQuota usage(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        Optional<CustomerSubscription> opt = findActiveSubscription(scope, customerId);
        if (opt.isEmpty()) return new UsageQuota(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null);
        CustomerSubscription sub = opt.get();
        long invoiceUsed = sub.getInvoiceUsageCarryForward() + invoices.countByUsageSubscription_IdAndUsageCountedTrue(sub.getId());
        long invoiceLimit = sub.getEffectiveInvoiceLimit();
        long productsUsed = products.countByCustomer_Id(customerId);
        long productLimit = sub.getEffectiveProductLimit();
        long templatesUsed = customTemplates.countByCustomer_IdAndActiveTrue(customerId);
        long templateLimit = sub.getEffectiveCustomTemplateLimit();
        return new UsageQuota(invoiceLimit, invoiceUsed, Math.max(0, invoiceLimit - invoiceUsed),
                productLimit, productsUsed, Math.max(0, productLimit - productsUsed),
                templateLimit, templatesUsed, Math.max(0, templateLimit - templatesUsed), sub.getStartedOn(), sub.getEndsOn());
    }

    @Transactional
    @CacheEvict(cacheNames = {"customer-templates", "dashboard"}, allEntries = true)
    public CustomerSubscription activate(TenantScope scope, long customerId, long planId, String actor) {
        return activate(scope, customerId, planId, actor, null, null);
    }

    @Transactional
    @CacheEvict(cacheNames = {"customer-templates", "dashboard"}, allEntries = true)
    public CustomerSubscription activate(TenantScope scope, long customerId, long planId, String actor,
            LocalDate requestedStart, LocalDate requestedEnd) {
        scope.requireCustomerAccess(customerId);
        Customer customer = customers.findById(customerId).filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        SubscriptionPlan plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Choose a valid plan."));
        if (!plan.isActive()) throw new IllegalArgumentException("That plan is no longer available.");
        LocalDate today = today();
        LocalDate start = requestedStart == null ? today : requestedStart;
        LocalDate end = requestedEnd == null ? start.plusMonths(1).minusDays(1) : requestedEnd;
        if (start.isAfter(today) || end.isBefore(today) || end.isBefore(start)) {
            throw new IllegalArgumentException("An active subscription must start on or before today and end on or after today.");
        }
        CustomerSubscription current = subscriptions.findForUpdate(customerId, SubscriptionStatus.ACTIVE).orElse(null);
        Long previousPlanId = current == null ? null : current.getPlan().getId();
        int carry = 0;
        if (current != null) {
            if (current.isEffective(today)) {
                long used = current.getInvoiceUsageCarryForward() + invoices.countByUsageSubscription_IdAndUsageCountedTrue(current.getId());
                carry = Math.toIntExact(Math.min(used, Integer.MAX_VALUE));
            }
            current.setStatus(SubscriptionStatus.CANCELLED);
            current.setAssignedBy(actor);
            subscriptions.flush();
        }
        CustomerSubscription next = new CustomerSubscription(customer, plan, SubscriptionStatus.ACTIVE, actor, start, end, carry);
        CustomerSubscription saved = subscriptions.saveAndFlush(next);
        audit.record(AuditEventCommand.success(customerId, "ASSIGN", "SUBSCRIPTION", "Customer subscription", saved.getId(),
                "Plan assigned immediately; active-term invoice usage was carried forward where applicable.",
                current == null ? null : Map.of("planId", previousPlanId, "status", "ACTIVE"),
                Map.of("planId", plan.getId(), "status", saved.getStatus().name(), "startedOn", start, "endsOn", end,
                        "invoiceUsageCarryForward", carry)).withActor(actor));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = {"customer-templates", "dashboard"}, allEntries = true)
    public void cancel(TenantScope scope, long customerId, String actor) {
        scope.requireCustomerAccess(customerId);
        CustomerSubscription current = subscriptions.findForUpdate(customerId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("This account has no active plan."));
        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setAssignedBy(actor);
        audit.record(AuditEventCommand.success(customerId, "CANCEL", "SUBSCRIPTION", "Customer subscription", current.getId(),
                "Subscription cancelled by an authorized actor.", Map.of("status", "ACTIVE", "planId", current.getPlan().getId()),
                Map.of("status", "CANCELLED")).withActor(actor));
    }

    @Transactional
    @CacheEvict(cacheNames = {"customer-templates", "dashboard"}, allEntries = true)
    public int markExpired() {
        LocalDate day = today();
        List<CustomerSubscription> expired = subscriptions.findByStatusAndEndsOnBefore(SubscriptionStatus.ACTIVE, day);
        expired.forEach(subscription -> {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            audit.record(AuditEventCommand.success(subscription.getCustomerId(), "EXPIRE", "SUBSCRIPTION", "Customer subscription",
                    subscription.getId(), "Subscription expired; historical read access remains available.",
                    Map.of("status", "ACTIVE"), Map.of("status", "EXPIRED", "endsOn", subscription.getEndsOn())).withActor("SYSTEM"));
        });
        return expired.size();
    }

    @Transactional
    public CustomerSubscription updateLimits(TenantScope scope, long subscriptionId, Integer invoiceLimit, Integer productLimit, Integer customTemplateLimit, String actor) {
        if (!scope.isAdminScope()) throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        CustomerSubscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found."));
        if (invoiceLimit != null && invoiceLimit < 1 || productLimit != null && productLimit < 1
                || customTemplateLimit != null && customTemplateLimit < 0) {
            throw new IllegalArgumentException("Invoice and product overrides must be positive; custom template override may be zero.");
        }
        Map<String, Object> before = Map.of("invoiceLimit", subscription.getInvoiceLimitOverride() == null ? 0 : subscription.getInvoiceLimitOverride(),
                "productLimit", subscription.getProductLimitOverride() == null ? 0 : subscription.getProductLimitOverride(),
                "customTemplateLimit", subscription.getCustomTemplateLimitOverride() == null ? 0 : subscription.getCustomTemplateLimitOverride());
        subscription.setInvoiceLimitOverride(invoiceLimit); subscription.setProductLimitOverride(productLimit);
        subscription.setCustomTemplateLimitOverride(customTemplateLimit);
        audit.record(AuditEventCommand.success(subscription.getCustomerId(), "LIMIT_OVERRIDE", "SUBSCRIPTION", "Customer subscription",
                subscriptionId, "Administrator changed subscription usage limit overrides.", before,
                Map.of("invoiceLimit", invoiceLimit == null ? 0 : invoiceLimit, "productLimit", productLimit == null ? 0 : productLimit,
                        "customTemplateLimit", customTemplateLimit == null ? 0 : customTemplateLimit)).withActor(actor));
        return subscription;
    }

    @Transactional(readOnly = true)
    public long subscriberCount(TenantScope scope, long planId) {
        if (!scope.isAdminScope()) throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        return subscriptions.countByPlan_IdAndStatus(planId, SubscriptionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<CustomerSubscription> expiringOnOrBefore(LocalDate date, LocalDate from) {
        return subscriptions.findByStatusAndEndsOnBetween(SubscriptionStatus.ACTIVE, from, date);
    }

    @Transactional(readOnly = true)
    public List<CustomerSubscription> expiredCandidates(LocalDate day) {
        return subscriptions.findByStatusAndEndsOnBefore(SubscriptionStatus.ACTIVE, day);
    }
}

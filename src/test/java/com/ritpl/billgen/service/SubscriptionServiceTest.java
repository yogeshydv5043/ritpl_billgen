package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.entity.SubscriptionStatus;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomInvoiceTemplateRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;
import com.ritpl.billgen.security.TenantScope;

/** Subscription limits, overrides, plan eligibility and renewal usage policy. */
class SubscriptionServiceTest {

    private static final long CUSTOMER_ID = 17L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final CustomerSubscriptionRepository subscriptions = mock(CustomerSubscriptionRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final TemplateService templates = mock(TemplateService.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final CatalogProductRepository products = mock(CatalogProductRepository.class);
    private final CustomInvoiceTemplateRepository customTemplates = mock(CustomInvoiceTemplateRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private final SubscriptionService service = new SubscriptionService(subscriptions, customers, plans, templates,
            invoices, products, customTemplates, audit, ZONE.getId());

    @Test
    void usageReportsEffectiveLimitsAndRemainingAllowance() {
        CustomerSubscription subscription = activeSubscription(plan(100, 50, 3), 0);
        activeSubscriptionIs(subscription);
        when(invoices.countByUsageSubscription_IdAndUsageCountedTrue(any())).thenReturn(40L);
        when(products.countByCustomer_Id(CUSTOMER_ID)).thenReturn(5L);
        when(customTemplates.countByCustomer_IdAndActiveTrue(CUSTOMER_ID)).thenReturn(3L);

        UsageQuota quota = service.usage(TenantScope.ofCustomer(CUSTOMER_ID), CUSTOMER_ID);

        assertEquals(100L, quota.invoiceLimit());
        assertEquals(40L, quota.invoicesUsed());
        assertEquals(60L, quota.remainingInvoices());
        assertEquals(50L, quota.productLimit());
        assertEquals(45L, quota.remainingProducts());
        assertEquals(3L, quota.customTemplateLimit());
        assertEquals(3L, quota.customTemplatesUsed());
        assertEquals(0L, quota.remainingCustomTemplates(), "an exhausted allowance never reports a negative remainder");
    }

    @Test
    void usageIncludesUsageCarriedForwardFromTheCurrentTerm() {
        activeSubscriptionIs(activeSubscription(plan(100, 50, 3), 10));
        when(invoices.countByUsageSubscription_IdAndUsageCountedTrue(any())).thenReturn(40L);
        when(products.countByCustomer_Id(CUSTOMER_ID)).thenReturn(0L);
        when(customTemplates.countByCustomer_IdAndActiveTrue(CUSTOMER_ID)).thenReturn(0L);

        UsageQuota quota = service.usage(TenantScope.ofCustomer(CUSTOMER_ID), CUSTOMER_ID);

        assertEquals(50L, quota.invoicesUsed(), "10 carried forward + 40 issued against this term");
        assertEquals(50L, quota.remainingInvoices());
    }

    @Test
    void limitOverridesApplyToOneSubscriptionWithoutChangingPlanDefaults() {
        SubscriptionPlan plan = plan(100, 50, 1);
        CustomerSubscription subscription = activeSubscription(plan, 0);
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));

        service.updateLimits(TenantScope.administrator(), 7L, 150, null, 2, "admin@ritpl.test");

        assertEquals(150, subscription.getEffectiveInvoiceLimit(), "the override wins for this subscription");
        assertEquals(2, subscription.getEffectiveCustomTemplateLimit());
        assertEquals(50, subscription.getEffectiveProductLimit(), "an omitted override falls back to the plan default");
        assertEquals(100, plan.getMaxMonthlyInvoices(), "plan defaults are shared and must not change");
        assertEquals(1, plan.getMaxCustomTemplates(), "plan defaults are shared and must not change");
        verify(audit).record(any(AuditEventCommand.class));
    }

    @Test
    void customersCannotOverrideTheirOwnLimits() {
        when(subscriptions.findById(7L)).thenReturn(Optional.of(activeSubscription(plan(100, 50, 1), 0)));

        assertThrows(AccessDeniedException.class,
                () -> service.updateLimits(TenantScope.ofCustomer(CUSTOMER_ID), 7L, 150, null, 2, "customer@acme.test"));
    }

    @Test
    void invalidLimitOverridesAreRejected() {
        when(subscriptions.findById(7L)).thenReturn(Optional.of(activeSubscription(plan(100, 50, 1), 0)));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateLimits(TenantScope.administrator(), 7L, 0, null, null, "admin@ritpl.test"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateLimits(TenantScope.administrator(), 7L, null, -1, null, "admin@ritpl.test"));
    }

    @Test
    void planChangeInsideAnActiveTermCarriesIssuedUsageForward() {
        CustomerSubscription current = activeSubscription(plan(100, 50, 1), 5);
        startRenewal(current, plan(200, 80, 2));
        when(invoices.countByUsageSubscription_IdAndUsageCountedTrue(any())).thenReturn(3L);

        CustomerSubscription renewed = service.activate(TenantScope.ofCustomer(CUSTOMER_ID), CUSTOMER_ID, 2L, "admin@ritpl.test");

        assertEquals(8, renewed.getInvoiceUsageCarryForward(), "5 carried forward + 3 already issued in this term");
        assertEquals(SubscriptionStatus.CANCELLED, current.getStatus(), "the previous term is closed, never deleted");
        assertTrue(renewed.isActive());
    }

    @Test
    void aNewTermAfterExpiryStartsWithACleanAllowance() {
        LocalDate today = LocalDate.now(ZONE);
        CustomerSubscription expired = new CustomerSubscription(customer(), plan(100, 50, 1), SubscriptionStatus.ACTIVE,
                "system", today.minusDays(40), today.minusDays(10), 5);
        startRenewal(expired, plan(200, 80, 2));

        CustomerSubscription renewed = service.activate(TenantScope.ofCustomer(CUSTOMER_ID), CUSTOMER_ID, 2L, "admin@ritpl.test");

        assertEquals(0, renewed.getInvoiceUsageCarryForward(), "an expired term does not carry usage into the new one");
        verify(invoices, never()).countByUsageSubscription_IdAndUsageCountedTrue(any());
    }

    @Test
    void expiredAndSuspendedSubscriptionsBlockRestrictedWrites() {
        LocalDate today = LocalDate.now(ZONE);
        CustomerSubscription expired = new CustomerSubscription(customer(), plan(100, 50, 1), SubscriptionStatus.ACTIVE,
                "system", today.minusDays(40), today.minusDays(1), 0);
        when(subscriptions.findForUpdate(CUSTOMER_ID, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(expired));

        assertThrows(IllegalArgumentException.class,
                () -> service.requireActiveForUpdate(TenantScope.ofCustomer(CUSTOMER_ID)));

        when(subscriptions.findForUpdate(CUSTOMER_ID, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.requireActiveForUpdate(TenantScope.ofCustomer(CUSTOMER_ID)));
    }

    @Test
    void customersOnlySeeTemplatesIncludedInTheirPlan() {
        BillTemplate included = template(11L, "Classic", true);
        BillTemplate retired = template(12L, "Retired layout", false);
        SubscriptionPlan plan = plan(100, 50, 1);
        when(plan.getTemplates()).thenReturn(Set.of(included, retired));
        activeSubscriptionIs(activeSubscription(plan, 0));

        List<BillTemplate> visible = service.accessibleTemplates(TenantScope.ofCustomer(CUSTOMER_ID));

        assertEquals(List.of(included), visible, "inactive templates are never selectable");
        assertThrows(IllegalArgumentException.class,
                () -> service.requireAccessibleTemplate(TenantScope.ofCustomer(CUSTOMER_ID), 12L),
                "a template outside the plan cannot be forced through a forged id");
    }

    private void startRenewal(CustomerSubscription current, SubscriptionPlan nextPlan) {
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(plans.findById(2L)).thenReturn(Optional.of(nextPlan));
        when(subscriptions.findForUpdate(CUSTOMER_ID, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(current));
        when(subscriptions.saveAndFlush(any(CustomerSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void activeSubscriptionIs(CustomerSubscription subscription) {
        when(subscriptions.findFirstByCustomer_IdAndStatus(CUSTOMER_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
    }

    private CustomerSubscription activeSubscription(SubscriptionPlan plan, int carryForward) {
        LocalDate today = LocalDate.now(ZONE);
        return new CustomerSubscription(customer(), plan, SubscriptionStatus.ACTIVE, "system",
                today.minusDays(5), today.plusDays(25), carryForward);
    }

    private Customer customer() {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(CUSTOMER_ID);
        when(customer.isSystemAccount()).thenReturn(false);
        return customer;
    }

    private SubscriptionPlan plan(int maxInvoices, int maxProducts, int maxCustomTemplates) {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        when(plan.getId()).thenReturn(1L);
        when(plan.isActive()).thenReturn(true);
        when(plan.getMaxMonthlyInvoices()).thenReturn(maxInvoices);
        when(plan.getMaxProducts()).thenReturn(maxProducts);
        when(plan.getMaxCustomTemplates()).thenReturn(maxCustomTemplates);
        return plan;
    }

    private BillTemplate template(long id, String name, boolean active) {
        BillTemplate template = mock(BillTemplate.class);
        when(template.getId()).thenReturn(id);
        when(template.getName()).thenReturn(name);
        when(template.isActive()).thenReturn(active);
        when(template.getSortOrder()).thenReturn((int) id);
        return template;
    }
}

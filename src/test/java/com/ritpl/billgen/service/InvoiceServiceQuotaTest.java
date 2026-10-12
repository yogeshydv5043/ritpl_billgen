package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.security.TenantScope;

/** Invoice generation rules: who may issue, what consumes allowance, and what never does. */
class InvoiceServiceQuotaTest {

    private static final long CUSTOMER_ID = 17L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CatalogService catalog = mock(CatalogService.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final CustomTemplateService customTemplates = mock(CustomTemplateService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);

    private final InvoiceService service = new InvoiceService(invoices, customers, catalog, new InvoiceCalculator(),
            subscriptions, customTemplates, jdbc, audit);

    @Test
    void aDraftInvoiceIsSavedWithoutConsumingAllowance() {
        Invoice persisted = issue(InvoiceStatus.DRAFT, subscription(10, 0), 0L);

        assertFalse(persisted.isUsageCounted(), "drafts are not billed against the allowance");
        verify(invoices, never()).countByUsageSubscription_IdAndUsageCountedTrue(any());
        verify(audit).record(any(AuditEventCommand.class));
    }

    @Test
    void issuingAnInvoiceConsumesExactlyOneSlotAndIsCountedAgainstTheActiveTerm() {
        CustomerSubscription subscription = subscription(10, 3);
        Invoice persisted = issue(InvoiceStatus.SENT, subscription, 4L);

        assertTrue(persisted.isUsageCounted(), "an issued invoice consumes one generation");
        assertEquals(subscription, persisted.getUsageSubscription(), "usage is attributed to the term that granted it");
        verify(invoices).countByUsageSubscription_IdAndUsageCountedTrue(7L);
    }

    @Test
    void issuingIsRejectedWhenTheAllowanceIsExhaustedAndNothingIsPersisted() {
        CustomerSubscription subscription = subscription(10, 0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> issue(InvoiceStatus.SENT, subscription, 10L));

        assertTrue(error.getMessage().toLowerCase().contains("allowance"), "the customer is told why the call failed");
        verify(invoices, never()).saveAndFlush(any(Invoice.class));
        verify(audit).recordFailure(any(AuditEventCommand.class));
    }

    @Test
    void usageCarriedForwardFromARenewalCountsTowardTheLimit() {
        // 2 slots carried forward + 8 issued in this term already exhausts the plan's 10.
        CustomerSubscription subscription = subscription(10, 2);

        assertThrows(IllegalArgumentException.class, () -> issue(InvoiceStatus.SENT, subscription, 8L));

        verify(invoices, never()).saveAndFlush(any(Invoice.class));
    }

    @Test
    void totalsAreCalculatedOnTheServerAndIgnoreAnyClientProvidedAmounts() {
        Customer owner = owner();
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription(10, 0));
        when(invoices.countByUsageSubscription_IdAndUsageCountedTrue(7L)).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));
        when(subscriptions.requireAccessibleTemplate(any(), eq(1L))).thenReturn(billTemplate());

        // Prices and tax come from the product record, never from the submitted line.
        var priced = new com.ritpl.billgen.entity.CatalogProduct(owner, "SKU-1", "Design workshop",
                com.ritpl.billgen.entity.ProductType.SERVICE, new BigDecimal("100.00"), new BigDecimal("18.00"));
        priced.setActive(true);
        when(catalog.findById(any(), eq(99L))).thenReturn(priced);

        CreateInvoiceCommand command = new CreateInvoiceCommand(CUSTOMER_ID, TODAY, TODAY.plusDays(7), "note", "INR",
                1L, List.of(new InvoiceLineInput("ignored client price", new BigDecimal("2"),
                        new BigDecimal("0.01"), BigDecimal.ZERO, 99L)), InvoiceStatus.DRAFT, null, new BigDecimal("18.00"));

        Invoice saved = service.create(TenantScope.ofCustomer(CUSTOMER_ID), command);

        assertEquals(new BigDecimal("200.00"), saved.getSubtotal());
        assertEquals(new BigDecimal("36.00"), saved.getTaxAmount());
        assertEquals(new BigDecimal("18.00"), saved.getDiscountAmount());
        assertEquals(new BigDecimal("218.00"), saved.getTotalAmount());
        assertEquals("INV-000042", saved.getInvoiceNumber(), "invoice numbers come from the database sequence");
        assertEquals("Design workshop", saved.getItems().get(0).getDescription(), "the product snapshot is stored on the line");
    }

    @Test
    void cancellingAnIssuedInvoiceKeepsTheConsumedSlotButCancellingADraftDoesNotCharge() {
        Customer owner = owner();
        Invoice issued = new Invoice();
        issued.setStatus(InvoiceStatus.SENT);
        issued.setUsageCounted(true);
        issued.setCustomer(owner);
        issued.setGeneratedAt(Instant.parse("2026-10-01T09:00:00Z"));
        when(invoices.findOwned(5L, CUSTOMER_ID)).thenReturn(Optional.of(issued));

        service.cancel(TenantScope.ofCustomer(CUSTOMER_ID), 5L);

        assertEquals(InvoiceStatus.CANCELLED, issued.getStatus());
        assertTrue(issued.isUsageCounted(), "a cancelled invoice does not silently restore allowance");

        Invoice draft = new Invoice();
        draft.setStatus(InvoiceStatus.DRAFT);
        draft.setCustomer(owner);
        when(invoices.findOwned(6L, CUSTOMER_ID)).thenReturn(Optional.of(draft));

        service.cancel(TenantScope.ofCustomer(CUSTOMER_ID), 6L);

        assertEquals(InvoiceStatus.CANCELLED, draft.getStatus());
        assertFalse(draft.isUsageCounted());
    }

    @Test
    void administratorsCannotIssueInvoicesOnBehalfOfACustomer() {
        Customer owner = owner();
        Invoice draft = new Invoice();
        draft.setStatus(InvoiceStatus.DRAFT);
        draft.setCustomer(owner);
        // The read succeeds (support visibility), the write is refused.
        when(invoices.findWithDetailsById(5L)).thenReturn(Optional.of(draft));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.create(TenantScope.administrator(), command(InvoiceStatus.SENT)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.markSent(TenantScope.administrator(), 5L));
        assertEquals(InvoiceStatus.DRAFT, draft.getStatus(), "the rejected transition changed nothing");
        verify(invoices, never()).saveAndFlush(any(Invoice.class));
    }

    @Test
    void aCustomerCannotIssueForAnotherTenant() {
        CreateInvoiceCommand forged = new CreateInvoiceCommand(999L, TODAY, TODAY.plusDays(7), null, "INR", 1L,
                List.of(new InvoiceLineInput("Design workshop", BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO)),
                InvoiceStatus.DRAFT, null, BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> service.create(TenantScope.ofCustomer(CUSTOMER_ID), forged));

        verify(invoices, never()).saveAndFlush(any(Invoice.class));
    }

    private Invoice issue(InvoiceStatus status, CustomerSubscription subscription, long alreadyCounted) {
        Customer owner = owner();
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription);
        when(invoices.countByUsageSubscription_IdAndUsageCountedTrue(7L)).thenReturn(alreadyCounted);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));
        when(subscriptions.requireAccessibleTemplate(any(), eq(1L))).thenReturn(billTemplate());
        return service.create(TenantScope.ofCustomer(CUSTOMER_ID), command(status));
    }

    private CreateInvoiceCommand command(InvoiceStatus status) {
        return new CreateInvoiceCommand(CUSTOMER_ID, TODAY, TODAY.plusDays(7), null, "INR", 1L,
                List.of(new InvoiceLineInput("Design workshop", BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO)),
                status, null, BigDecimal.ZERO);
    }

    private com.ritpl.billgen.entity.BillTemplate billTemplate() {
        com.ritpl.billgen.entity.BillTemplate template = mock(com.ritpl.billgen.entity.BillTemplate.class);
        when(template.getId()).thenReturn(1L);
        when(template.getName()).thenReturn("Classic");
        when(template.getStyleCode()).thenReturn("CLASSIC");
        when(template.getAccentColor()).thenReturn("#2563EB");
        return template;
    }

    private CustomerSubscription subscription(int invoiceLimit, int carryForward) {
        CustomerSubscription subscription = mock(CustomerSubscription.class);
        when(subscription.getId()).thenReturn(7L);
        when(subscription.getEffectiveInvoiceLimit()).thenReturn(invoiceLimit);
        when(subscription.getInvoiceUsageCarryForward()).thenReturn(carryForward);
        return subscription;
    }

    private Customer owner() {
        Customer owner = mock(Customer.class);
        when(owner.getId()).thenReturn(CUSTOMER_ID);
        when(owner.isSystemAccount()).thenReturn(false);
        when(owner.getCompanyName()).thenReturn("Acme Traders");
        return owner;
    }
}

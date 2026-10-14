package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.CustomInvoiceTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.repository.CustomInvoiceTemplateRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.security.TenantScope;

/** Custom invoice templates are tenant-owned and consume the plan's template allowance. */
class CustomTemplateServiceTest {

    private static final long CUSTOMER_ID = 17L;

    private final CustomInvoiceTemplateRepository templates = mock(CustomInvoiceTemplateRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final AuditService audit = mock(AuditService.class);

    private final CustomTemplateService service = new CustomTemplateService(templates, customers, subscriptions, audit);

    @Test
    void anotherTenantsTemplateIsNotVisibleThroughAForgedId() {
        when(templates.findByIdAndCustomer_Id(4L, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.findById(TenantScope.ofCustomer(CUSTOMER_ID), 4L));

        verify(templates, never()).findById(4L);
    }

    @Test
    void creatingATemplateIsBlockedWhenTheAllowanceIsExhausted() {
        Customer owner = customer();
        CustomerSubscription subscription = subscription(1);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription);
        when(templates.countByCustomer_IdAndDeletedAtIsNull(CUSTOMER_ID)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, command("Autumn brand", true)));

        verify(templates, never()).save(any(CustomInvoiceTemplate.class));
    }

    @Test
    void creatingATemplateIsAllowedWhileTheAllowanceRemains() {
        Customer owner = customer();
        CustomInvoiceTemplate saved = new CustomInvoiceTemplate(owner, "Autumn brand", BillTemplateStyle.MODERN, "#2563EB");
        CustomerSubscription subscription = subscription(1);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription);
        when(templates.countByCustomer_IdAndDeletedAtIsNull(CUSTOMER_ID)).thenReturn(0L);
        when(templates.save(any(CustomInvoiceTemplate.class))).thenReturn(saved);

        CustomInvoiceTemplate result = service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, command("Autumn brand", true));

        assertEquals(saved, result);
        assertEquals("Autumn brand", result.getName());
        verify(audit).record(any(AuditEventCommand.class));
    }

    @Test
    void unsafeAccentColoursAndBlankNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, command("  ")));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null,
                        new CustomTemplateCommand("Autumn", "CLASSIC", "javascript:alert(1)", true)));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null,
                        new CustomTemplateCommand("Autumn", "STYLE<script>", "#2563EB", true)));

        verify(templates, never()).save(any(CustomInvoiceTemplate.class));
    }

    @Test
    void deletingAnUnreferencedTemplateReleasesItsSlot() {
        CustomInvoiceTemplate template = ownedTemplate();
        when(templates.findByIdAndCustomer_Id(4L, CUSTOMER_ID)).thenReturn(Optional.of(template));
        when(templates.countInvoices(4L)).thenReturn(0L);

        service.delete(TenantScope.ofCustomer(CUSTOMER_ID), 4L);

        verify(templates).delete(template);
        verify(audit).record(any(AuditEventCommand.class));
    }

    @Test
    void deletingATemplateUsedByAnInvoiceArchivesItSoHistoryIsPreserved() {
        CustomInvoiceTemplate template = ownedTemplate();
        when(templates.findByIdAndCustomer_Id(4L, CUSTOMER_ID)).thenReturn(Optional.of(template));
        when(templates.countInvoices(4L)).thenReturn(2L);

        service.delete(TenantScope.ofCustomer(CUSTOMER_ID), 4L);

        assertFalse(template.isActive(), "the template is deactivated instead of destroyed");
        assertNotNull(template.getDeletedAt(), "and it is retired, which is what stops it using a plan slot");
        verify(templates, never()).delete(any(CustomInvoiceTemplate.class));
    }

    @Test
    void administratorsCanReadButNeverWriteCustomTemplates() {
        CustomInvoiceTemplate ownedBySomeoneElse = ownedTemplate();
        when(templates.findById(4L)).thenReturn(Optional.of(ownedBySomeoneElse));

        // Admin scope can resolve the row for support purposes, but never mutate tenant data.
        assertThrows(AccessDeniedException.class,
                () -> service.save(TenantScope.administrator(), null, command("Autumn brand", true)));
        assertThrows(AccessDeniedException.class, () -> service.delete(TenantScope.administrator(), 4L));
        verify(templates, never()).delete(any(CustomInvoiceTemplate.class));
        assertEquals(true, ownedBySomeoneElse.isActive(), "the record is untouched by the rejected request");
    }

    private CustomInvoiceTemplate ownedTemplate() {
        return new CustomInvoiceTemplate(customer(), "Autumn brand", BillTemplateStyle.CLASSIC, "#2563EB");
    }

    private CustomTemplateCommand command(String name) {
        return command(name, true);
    }

    private CustomTemplateCommand command(String name, boolean active) {
        return new CustomTemplateCommand(name, "CLASSIC", "#2563EB", active);
    }

    private Customer customer() {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(CUSTOMER_ID);
        when(customer.isSystemAccount()).thenReturn(false);
        return customer;
    }

    private CustomerSubscription subscription(int customTemplateLimit) {
        CustomerSubscription subscription = mock(CustomerSubscription.class);
        when(subscription.getEffectiveCustomTemplateLimit()).thenReturn(customTemplateLimit);
        return subscription;
    }

    /**
     * Hiding is a presentation choice, not a way to mint capacity. A design that is merely hidden still
     * occupies its slot, so an account on a one-design plan cannot keep an unlimited gallery by turning
     * each design off in turn.
     */
    @Test
    void aHiddenDesignStillOccupiesItsSlotInThePlan() {
        Customer owner = customer();
        CustomerSubscription subscription = subscription(1);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription);
        when(templates.countByCustomer_IdAndDeletedAtIsNull(CUSTOMER_ID)).thenReturn(1L);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, command("Second brand", false)));

        assertTrue(refusal.getMessage().contains("Hiding a design does not free its slot"), refusal.getMessage());
        verify(templates, never()).save(any(CustomInvoiceTemplate.class));
    }

    /** A retired design is kept for the documents that used it, not for the customer to bring back. */
    @Test
    void aRetiredDesignCannotBeEditedOrReactivated() {
        Customer owner = customer();
        CustomInvoiceTemplate retired = ownedTemplate();
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        retired.setDeletedAt(java.time.Instant.now());
        when(templates.findByIdAndCustomer_Id(4L, CUSTOMER_ID)).thenReturn(Optional.of(retired));

        assertThrows(IllegalArgumentException.class,
                () -> service.setActive(TenantScope.ofCustomer(CUSTOMER_ID), 4L, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), 4L, command("Autumn brand", true)));
        verify(templates, never()).save(any(CustomInvoiceTemplate.class));
    }
}

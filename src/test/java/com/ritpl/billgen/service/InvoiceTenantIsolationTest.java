package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.security.TenantScope;
import static org.mockito.Mockito.mock;

class InvoiceTenantIsolationTest {

    @Test
    void customerInvoiceLookupUsesTenantScopedQueryAndDoesNotLoadAnotherAccountInvoice() {
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(invoices.findOwned(902L, 17L)).thenReturn(Optional.empty());

        InvoiceService service = new InvoiceService(invoices, customers, mock(CatalogService.class), new InvoiceCalculator(),
                subscriptions, mock(CustomTemplateService.class), jdbc, mock(AuditService.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.findById(TenantScope.ofCustomer(17L), 902L));

        verify(invoices).findOwned(902L, 17L);
        verify(invoices, never()).findWithDetailsById(902L);
    }
}

package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.security.TenantScope;

/** Catalogue reads and writes must stay inside the caller's tenant and inside the plan limit. */
class CatalogServiceTest {

    private static final long CUSTOMER_ID = 17L;
    private static final long OTHER_CUSTOMER_ID = 18L;

    private final CatalogProductRepository products = mock(CatalogProductRepository.class);
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final AuditService audit = mock(AuditService.class);

    private final CatalogService service = new CatalogService(products, categories, customers, subscriptions, audit);

    @Test
    void anotherTenantsProductIsInvisibleRatherThanForbidden() {
        when(products.findOwned(55L, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.findById(TenantScope.ofCustomer(CUSTOMER_ID), 55L));

        // A forged id must never fall back to an unscoped lookup that would leak the row.
        verify(products, never()).findById(55L);
    }

    @Test
    void administratorReadsAcrossTenantsButCannotWriteCatalogueData() {
        CatalogProduct foreign = product(OTHER_CUSTOMER_ID);
        when(products.findById(55L)).thenReturn(Optional.of(foreign));

        assertDoesNotThrow(() -> service.findById(TenantScope.administrator(), 55L));

        assertThrows(AccessDeniedException.class,
                () -> service.save(TenantScope.administrator(), null, command()));
        assertThrows(AccessDeniedException.class,
                () -> service.setActive(TenantScope.administrator(), 55L, false));
        assertThrows(AccessDeniedException.class,
                () -> service.delete(TenantScope.administrator(), 55L));
    }

    @Test
    void newProductsAreBlockedOnceThePlanLimitIsReached() {
        Customer owner = owner();
        CustomerSubscription subscription = subscription(1);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription);
        when(products.countByCustomer_Id(CUSTOMER_ID)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, command()));

        verify(products, never()).save(any(CatalogProduct.class));
    }

    @Test
    void editingAnExistingProductIsStillAllowedWhenTheLimitWasLowered() {
        CatalogProduct existing = product(CUSTOMER_ID);
        Customer owner = owner();
        CustomerSubscription subscription = subscription(0);
        when(products.findOwned(55L, CUSTOMER_ID)).thenReturn(Optional.of(existing));
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(subscriptions.requireActiveForUpdate(any())).thenReturn(subscription);
        when(products.countByCustomer_Id(CUSTOMER_ID)).thenReturn(3L);
        when(products.save(any(CatalogProduct.class))).thenAnswer(call -> call.getArgument(0));

        service.save(TenantScope.ofCustomer(CUSTOMER_ID), 55L, command());

        verify(products).save(any(CatalogProduct.class));
        verify(audit).record(any(AuditEventCommand.class));
    }

    @Test
    void invalidPricesAndTaxRatesAreRejectedBeforeAnyWrite() {
        ProductCommand badPrice = new ProductCommand("SKU-9", "Widget", ProductType.PRODUCT, null, null,
                new BigDecimal("10.001"), BigDecimal.ZERO, true);
        ProductCommand badTax = new ProductCommand("SKU-9", "Widget", ProductType.PRODUCT, null, null,
                BigDecimal.ONE, new BigDecimal("101.00"), true);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, badPrice));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, badTax));

        verify(products, never()).save(any(CatalogProduct.class));
    }

    @Test
    void aDuplicateSkuIsRejectedInsideTheSameTenantOnly() {
        Customer owner = owner();
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(products.existsBySkuIgnoreCaseAndCustomer_Id("SKU-9", CUSTOMER_ID)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(TenantScope.ofCustomer(CUSTOMER_ID), null, command()));

        verify(products, never()).save(any(CatalogProduct.class));
    }

    private ProductCommand command() {
        return new ProductCommand("sku-9", "Widget", ProductType.PRODUCT, null, "A widget",
                new BigDecimal("99.00"), new BigDecimal("18.00"), true);
    }

    private CatalogProduct product(long ownerId) {
        return new CatalogProduct(customer(ownerId), "SKU-1", "Existing", ProductType.PRODUCT,
                new BigDecimal("10.00"), new BigDecimal("5.00"));
    }

    private Customer owner() {
        return customer(CUSTOMER_ID);
    }

    private Customer customer(long id) {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(id);
        when(customer.isSystemAccount()).thenReturn(false);
        return customer;
    }

    private CustomerSubscription subscription(int productLimit) {
        CustomerSubscription subscription = mock(CustomerSubscription.class);
        when(subscription.getId()).thenReturn(7L);
        when(subscription.getEffectiveProductLimit()).thenReturn(productLimit);
        when(subscription.getEffectiveInvoiceLimit()).thenReturn(productLimit);
        when(subscription.getInvoiceUsageCarryForward()).thenReturn(0);
        return subscription;
    }
}

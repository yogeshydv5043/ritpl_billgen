package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.SubscriptionStatus;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.security.TenantScope;

/** Customer-wise statistics. Read permission is checked before returning account-level details. */
@Service
public class AccountStatsService {
    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final InvoiceRepository invoices;
    private final CatalogProductRepository products;
    private final ProductCategoryRepository categories;
    private final CustomerSubscriptionRepository subscriptions;

    public AccountStatsService(CustomerRepository customers, AppUserRepository users, InvoiceRepository invoices,
            CatalogProductRepository products, ProductCategoryRepository categories,
            CustomerSubscriptionRepository subscriptions) {
        this.customers = customers; this.users = users; this.invoices = invoices; this.products = products;
        this.categories = categories; this.subscriptions = subscriptions;
    }

    /** Admins see every account; a customer receives only the statistics for its own id. */
    @Transactional(readOnly = true)
    public List<CustomerAccountStats> findAll(TenantScope scope, String search) {
        if (!scope.isAdminScope()) return List.of(findOne(scope, scope.requireCustomerId()));
        List<Customer> accounts;
        if (search == null || search.isBlank()) {
            accounts = customers.findBySystemAccountFalseOrderByCompanyNameAsc();
        } else {
            accounts = customers.searchVisible(search.trim());
        }
        return accounts.stream().map(this::toStats).toList();
    }

    @Transactional(readOnly = true)
    public CustomerAccountStats findOne(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        Customer customer = customers.findById(customerId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return toStats(customer);
    }

    private CustomerAccountStats toStats(Customer customer) {
        Long id = customer.getId();
        Optional<CustomerSubscription> subscription = subscriptions.findFirstByCustomer_IdAndStatus(id, SubscriptionStatus.ACTIVE);
        Optional<AppUser> login = users.findByCustomer_Id(id);
        BigDecimal billed = invoices.sumTotalByCustomer(id);
        LocalDate lastIssue = invoices.findLastIssueDateForCustomer(id).orElse(null);
        return new CustomerAccountStats(
                id,
                customer.getCompanyName(),
                customer.getContactName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getCity(),
                login.map(AppUser::getEmail).orElse(null),
                login.map(AppUser::isEnabled).orElse(false),
                subscription.map(value -> value.getPlan().getId()).orElse(null),
                subscription.map(value -> value.getPlan().getName()).orElse(null),
                subscription.map(value -> value.getPlan().getCode()).orElse(null),
                subscription.map(value -> value.getStatus().getDisplayName()).orElse("No plan"),
                subscription.map(CustomerSubscription::getStartedOn).orElse(null),
                subscription.map(CustomerSubscription::getEndsOn).orElse(null),
                invoices.countByCustomer_Id(id),
                products.countByCustomer_IdAndActiveTrue(id),
                categories.countByCustomer_Id(id),
                billed == null ? BigDecimal.ZERO : billed,
                lastIssue,
                customer.getCreatedAt());
    }
}
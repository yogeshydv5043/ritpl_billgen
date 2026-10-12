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
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.security.TenantScope;

@Service
public class AccountStatsService {
    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final InvoiceRepository invoices;
    private final CatalogProductRepository products;
    private final ProductCategoryRepository categories;
    private final SubscriptionService subscriptions;

    public AccountStatsService(CustomerRepository customers, AppUserRepository users, InvoiceRepository invoices,
            CatalogProductRepository products, ProductCategoryRepository categories, SubscriptionService subscriptions) {
        this.customers = customers; this.users = users; this.invoices = invoices; this.products = products;
        this.categories = categories; this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public List<CustomerAccountStats> findAll(TenantScope scope, String search) {
        if (!scope.isAdminScope()) return List.of(findOne(scope, scope.requireCustomerId()));
        List<Customer> accounts = search == null || search.isBlank()
                ? customers.findBySystemAccountFalseOrderByCompanyNameAsc() : customers.searchVisible(search.trim());
        return accounts.stream().map(this::toStats).toList();
    }

    @Transactional(readOnly = true)
    public CustomerAccountStats findOne(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        Customer customer = customers.findById(customerId).filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return toStats(customer);
    }

    private CustomerAccountStats toStats(Customer customer) {
        Long id = customer.getId();
        List<CustomerSubscription> history = subscriptions.findHistory(TenantScope.administrator(), id);
        CustomerSubscription current = history.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).findFirst()
                .orElse(history.isEmpty() ? null : history.get(0));
        Optional<AppUser> login = users.findByCustomer_Id(id);
        BigDecimal billed = invoices.sumTotalByCustomer(id);
        LocalDate lastIssue = invoices.findLastIssueDateForCustomer(id).orElse(null);
        UsageQuota quota = subscriptions.usage(TenantScope.administrator(), id);
        String subscriptionStatus = current == null ? "No plan"
                : current.getStatus() == SubscriptionStatus.ACTIVE && !current.isEffective(subscriptions.today()) ? "Expired"
                : current.getStatus().getDisplayName();
        return new CustomerAccountStats(id, customer.getCompanyName(), customer.getContactName(), customer.getEmail(),
                customer.getPhone(), customer.getCity(), login.map(AppUser::getEmail).orElse(null),
                login.map(AppUser::isEnabled).orElse(false), current == null ? null : current.getPlan().getId(),
                current == null ? null : current.getPlan().getName(), current == null ? null : current.getPlan().getCode(),
                subscriptionStatus, current == null ? null : current.getStartedOn(), current == null ? null : current.getEndsOn(),
                invoices.countByCustomer_IdAndUsageCountedTrue(id), quota.invoiceLimit(), quota.invoicesUsed(), quota.remainingInvoices(),
                products.countByCustomer_Id(id), quota.productLimit(), quota.customTemplatesUsed(), quota.customTemplateLimit(),
                categories.countByCustomer_Id(id), billed == null ? BigDecimal.ZERO : billed, lastIssue, customer.getCreatedAt());
    }
}

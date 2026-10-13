package com.ritpl.billgen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationStatusFilter;
import com.ritpl.billgen.entity.SubscriptionStatus;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;

/**
 * Decides who a bulk message is for. Eligibility is computed once when a job is created and stored
 * on the job as an ordered, paginable list, so a batch processed hours later still addresses the
 * same recipients even if accounts changed in the meantime.
 */
@Service
public class RecipientResolver {
    private final CustomerRepository customers;
    private final CustomerSubscriptionRepository subscriptions;

    public RecipientResolver(CustomerRepository customers, CustomerSubscriptionRepository subscriptions) {
        this.customers = customers; this.subscriptions = subscriptions;
    }

    /** One eligible recipient: the account plus the values available to its placeholders. */
    public record Recipient(Customer customer, String address, Map<String, String> values) { }

    @Transactional(readOnly = true)
    public List<Recipient> eligible(Notification notification, List<Long> explicitCustomerIds) {
        List<Customer> candidates = new ArrayList<>();
        if (explicitCustomerIds != null && !explicitCustomerIds.isEmpty()) {
            customers.findAllById(explicitCustomerIds).stream()
                    .filter(c -> !c.isSystemAccount() && !c.isDeleted())
                    .forEach(candidates::add);
        } else {
            customers.findBySystemAccountFalseAndDeletedAtIsNullOrderByCompanyNameAsc().forEach(candidates::add);
        }
        NotificationStatusFilter filter = notification == null || notification.getStatusFilter() == null
                ? NotificationStatusFilter.ACTIVE_ONLY : notification.getStatusFilter();
        List<Recipient> result = new ArrayList<>();
        for (Customer customer : candidates) {
            if (filter == NotificationStatusFilter.ACTIVE_ONLY && customer.getStatus() != AccountStatus.ACTIVE) continue;
            if (filter == NotificationStatusFilter.DISABLED_ONLY && customer.getStatus() != AccountStatus.DISABLED) continue;
            var activeTerm = subscriptions.findFirstByCustomer_IdAndStatus(customer.getId(), SubscriptionStatus.ACTIVE);
            boolean subscribed = activeTerm.isPresent();
            if (filter == NotificationStatusFilter.WITH_SUBSCRIPTION && !subscribed) continue;
            if (filter == NotificationStatusFilter.WITHOUT_SUBSCRIPTION && subscribed) continue;
            if (notification != null && !notification.includesPlan(activeTerm.map(s -> s.getPlan().getId()).orElse(null))) continue;
            result.add(new Recipient(customer, customer.getEmail(), values(customer, activeTerm.orElse(null))));
        }
        return result;
    }

    /** Placeholder values a notification may reference for this account. */
    static Map<String, String> values(Customer customer, com.ritpl.billgen.entity.CustomerSubscription subscription) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("companyName", customer.getCompanyName());
        values.put("customerName", customer.getContactName());
        values.put("contactName", customer.getContactName());
        values.put("accountStatus", customer.getStatus().getLabel());
        values.put("today", java.time.LocalDate.now().toString());
        values.put("adminContact", "support@billgen.local");
        if (subscription != null) {
            values.put("planName", subscription.getPlan().getName());
            values.put("planCode", subscription.getPlan().getCode());
            values.put("expiryDate", subscription.getEndsOn() == null ? "not set" : subscription.getEndsOn().toString());
            values.put("daysRemaining", subscription.getEndsOn() == null ? ""
                    : String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), subscription.getEndsOn())));
            values.put("invoiceLimit", String.valueOf(subscription.getEffectiveInvoiceLimit()));
        } else {
            values.put("planName", "No active plan");
            values.put("planCode", "");
            values.put("expiryDate", "");
            values.put("daysRemaining", "");
            values.put("invoiceLimit", "");
        }
        return values;
    }
}

package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AccountStatsService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.SubscriptionExpiryNotificationService;

/**
 * Home of the customer portal. Every query on this screen is pinned to the signed-in account, so
 * the page can only ever describe the customer's own data.
 */
@Controller
@RequestMapping("/portal")
public class PortalController {
    private final CustomerService customers;
    private final AccountStatsService accountStats;
    private final InvoiceService invoices;
    private final CatalogService catalog;
    private final ProductCategoryService categories;
    private final SubscriptionService subscriptions;
    private final SubscriptionExpiryNotificationService expiryNotifications;

    public PortalController(CustomerService customers, AccountStatsService accountStats, InvoiceService invoices,
            CatalogService catalog, ProductCategoryService categories, SubscriptionService subscriptions,
            SubscriptionExpiryNotificationService expiryNotifications) {
        this.customers = customers; this.accountStats = accountStats; this.invoices = invoices;
        this.catalog = catalog; this.categories = categories; this.subscriptions = subscriptions; this.expiryNotifications = expiryNotifications;
    }

    @GetMapping
    public String home(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        model.addAttribute("customer", customers.findById(scope, customerId));
        model.addAttribute("stats", accountStats.findOne(scope, customerId));
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, customerId).orElse(null));
        model.addAttribute("quota", subscriptions.usage(scope, customerId));
        model.addAttribute("notifications", expiryNotifications.findForCustomer(scope));
        model.addAttribute("templates", subscriptions.accessibleTemplates(scope));
        model.addAttribute("invoices", invoices.findForCustomer(scope, customerId));
        model.addAttribute("products", catalog.findActive(scope));
        model.addAttribute("categories", categories.findAll(scope));
        return "portal/home";
    }
}
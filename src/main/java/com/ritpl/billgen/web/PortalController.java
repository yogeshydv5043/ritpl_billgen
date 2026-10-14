package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomerDashboardService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.SubscriptionExpiryNotificationService;

/**
 * Home of the customer portal. Every query on this screen is pinned to the signed-in account, so
 * the page can only ever describe the customer's own data.
 */
@Controller
@RequestMapping("/portal")
public class PortalController {
    private final CustomerService customers;
    private final SubscriptionExpiryNotificationService expiryNotifications;
    private final CustomerDashboardService dashboard;

    public PortalController(CustomerService customers, SubscriptionExpiryNotificationService expiryNotifications,
            CustomerDashboardService dashboard) {
        this.customers = customers; this.expiryNotifications = expiryNotifications; this.dashboard = dashboard;
    }

    @GetMapping
    public String home(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        model.addAttribute("customer", customers.findById(scope, customerId));
        model.addAttribute("notifications", expiryNotifications.findForCustomer(scope));
        // Every card and list below is a live query over this account's own rows, and a dashboard that
        // cannot be computed says so instead of showing a placeholder figure.
        try {
            model.addAttribute("dashboard", dashboard.load(scope, customerId));
        } catch (RuntimeException e) {
            model.addAttribute("dashboardError", "Live figures could not be read: " + e.getMessage());
        }
        return "portal/home";
    }
}
package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.CustomerSettingsService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.UsageQuota;

/**
 * The account and security screen of a customer: who is signed in, which company account that login
 * belongs to, whether the account is enabled, and the password change. Profile and company details
 * live on their own page because they are also the identity printed on an invoice.
 *
 * <p>The sign-in address itself is deliberately not editable here. It is the account's external key -
 * administrators use it to find the tenant - so changing it is an administrator action, and saying so
 * on the page is better than silently allowing a half-change.</p>
 */
@Controller
@RequestMapping("/portal/account")
public class PortalAccountController {
    private final CustomerService customers;
    private final SubscriptionService subscriptions;
    private final CustomerSettingsService settings;

    public PortalAccountController(CustomerService customers, SubscriptionService subscriptions,
            CustomerSettingsService settings) {
        this.customers = customers; this.subscriptions = subscriptions; this.settings = settings;
    }

    @GetMapping
    public String account(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        var customer = customers.findById(scope, customerId);
        model.addAttribute("customer", customer);
        model.addAttribute("email", principal.getUsername());
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, customerId).orElse(null));
        UsageQuota quota = subscriptions.usage(scope, customerId);
        model.addAttribute("quota", quota);
        model.addAttribute("settings", settings.get(scope, customerId));
        model.addAttribute("minPasswordLength", 8);
        return "portal/account";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam(required = false) String confirmPassword, RedirectAttributes redirect) {
        if (confirmPassword != null && !confirmPassword.isBlank() && !confirmPassword.equals(newPassword)) {
            redirect.addFlashAttribute("errorMessage", "The new password and its confirmation do not match.");
            return "redirect:/portal/account";
        }
        if (currentPassword == null || newPassword == null) {
            redirect.addFlashAttribute("errorMessage", "Enter the current and the new password.");
            return "redirect:/portal/account";
        }
        try {
            customers.changeOwnPassword(principal, currentPassword, newPassword);
            redirect.addFlashAttribute("successMessage",
                    "Password changed. This session stays signed in; other sessions keep working until they expire.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/account";
    }
}

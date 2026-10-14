package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.SubscriptionExpiryNotificationService;

@Controller
@RequestMapping("/portal/notifications")
public class PortalNotificationController {
    private final SubscriptionExpiryNotificationService notifications;
    private final com.ritpl.billgen.service.CustomerSettingsService settings;
    private final com.ritpl.billgen.service.CustomerService customers;
    public PortalNotificationController(SubscriptionExpiryNotificationService notifications,
            com.ritpl.billgen.service.CustomerSettingsService settings,
            com.ritpl.billgen.service.CustomerService customers) {
        this.notifications = notifications; this.settings = settings; this.customers = customers;
    }

    /** The account's own inbox: expiry reminders, quota, payment and security notices, newest first. */
    @GetMapping
    public String inbox(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = principal.requireCustomerId();
        model.addAttribute("notifications", notifications.findForCustomer(scope));
        // The preferences travel with the list so the page can say which kinds of notice are switched off
        // rather than leaving a quiet gap the account has to explain for itself.
        model.addAttribute("settings", settings.get(scope, customerId));
        model.addAttribute("customer", customers.findById(scope, customerId));
        return "portal/notifications";
    }

    @PostMapping("/{id}/read")
    public String read(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        notifications.markRead(TenantScope.from(principal), id);
        redirect.addFlashAttribute("successMessage", "Marked as read.");
        // Back to the inbox, so the row visibly changes instead of dropping the reader somewhere else.
        return "redirect:/portal/notifications";
    }
}

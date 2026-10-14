package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public PortalNotificationController(SubscriptionExpiryNotificationService notifications,
            com.ritpl.billgen.service.CustomerSettingsService settings) { this.notifications = notifications; this.settings = settings; }
    /** The account's own inbox: expiry reminders, quota, payment and security notices, newest first. */
    @GetMapping
    public String inbox(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("notifications", notifications.findForCustomer(TenantScope.from(principal)));
        model.addAttribute("settings", settings.get(TenantScope.from(principal), principal.requireCustomerId()));
        return "portal/notifications";
    }

    @PostMapping("/{id}/read")
    public String read(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        notifications.markRead(TenantScope.from(principal), id);
        redirect.addFlashAttribute("successMessage", "Notification marked as read.");
        return "redirect:/portal";
    }
}

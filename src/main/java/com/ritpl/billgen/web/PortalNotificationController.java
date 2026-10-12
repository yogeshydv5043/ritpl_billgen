package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
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
    public PortalNotificationController(SubscriptionExpiryNotificationService notifications) { this.notifications = notifications; }
    @PostMapping("/{id}/read")
    public String read(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        notifications.markRead(TenantScope.from(principal), id);
        redirect.addFlashAttribute("successMessage", "Notification marked as read.");
        return "redirect:/portal";
    }
}

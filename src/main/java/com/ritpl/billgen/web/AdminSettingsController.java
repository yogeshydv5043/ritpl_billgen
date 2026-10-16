package com.ritpl.billgen.web;

import java.util.Map;
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
import com.ritpl.billgen.service.NotificationService;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.TemplateService;

/**
 * Admin Profile and Settings, plus the shell's global search. An administrator's own password is
 * changed here; nothing on this screen reaches another tenant's records.
 */
@Controller
@RequestMapping("/admin")
public class AdminSettingsController {
    private final CustomerService customers;
    private final PlanService plans;
    private final TemplateService templates;
    private final SubscriptionService subscriptions;
    private final NotificationService notifications;

    public AdminSettingsController(CustomerService customers, PlanService plans, TemplateService templates,
            SubscriptionService subscriptions, NotificationService notifications) {
        this.customers = customers; this.plans = plans; this.templates = templates; this.subscriptions = subscriptions;
        this.notifications = notifications;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("email", principal.getUsername());
        model.addAttribute("role", principal.getRole());
        model.addAttribute("planCount", plans.findActive().size());
        model.addAttribute("templateCount", templates.activeCount());
        model.addAttribute("notificationCount", notifications.findActive().size());
        model.addAttribute("expiryReminders", subscriptions.reminderOffsetsSummary());
        model.addAttribute("mailTransport", notifications.transportLabel());
        return "admin/profile";
    }

    @PostMapping("/profile/password")
    public String changeOwnPassword(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam String currentPassword, @RequestParam String newPassword, RedirectAttributes redirect) {
        if (currentPassword == null || newPassword == null) {
            redirect.addFlashAttribute("errorMessage", "Enter the current and the new password.");
            return "redirect:/admin/profile";
        }
        try {
            customers.changeAdminPassword(principal, currentPassword, newPassword);
            redirect.addFlashAttribute("successMessage", "Password changed. Other sessions stay signed in until they expire.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/profile";
    }

    /** Global search for the top bar: accounts first, then the configuration an admin may be after. */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        String term = q == null ? "" : q.trim();
        model.addAttribute("q", term);
        if (term.length() < 2) {
            model.addAttribute("results", Map.of());
            return "admin/search";
        }
        model.addAttribute("results", Map.of(
                "customers", customers.findAll(TenantScope.administrator(), term).stream().limit(8).toList(),
                "plans", plans.findLive().stream()
                        .filter(plan -> contains(plan.getName(), term) || contains(plan.getCode(), term)).limit(5).toList(),
                "templates", templates.findLive().stream()
                        .filter(t -> contains(t.getName(), term) || contains(t.getCode(), term)).limit(5).toList(),
                "notifications", notifications.findAll().stream()
                        .filter(n -> contains(n.getName(), term) || contains(n.getTitle(), term)).limit(5).toList()));
        return "admin/search";
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(term.toLowerCase(java.util.Locale.ROOT));
    }
}

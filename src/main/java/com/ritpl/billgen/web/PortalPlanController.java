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
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.SubscriptionService;

/**
 * Plan selection for a customer account. Choosing a plan immediately unlocks the bill templates
 * bundled with it; cancelling withdraws that access again.
 */
@Controller
@RequestMapping("/portal/plan")
public class PortalPlanController {
    private final PlanService plans;
    private final SubscriptionService subscriptions;

    public PortalPlanController(PlanService plans, SubscriptionService subscriptions) {
        this.plans = plans; this.subscriptions = subscriptions;
    }

    @GetMapping
    public String plan(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, customerId).orElse(null));
        model.addAttribute("history", subscriptions.findHistory(scope, customerId));
        model.addAttribute("plans", plans.findActive());
        model.addAttribute("templates", subscriptions.accessibleTemplates(scope));
        return "portal/plan";
    }

    @PostMapping
    public String select(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam long planId,
            RedirectAttributes redirect) {
        try {
            subscriptions.activate(TenantScope.from(principal), principal.requireCustomerId(), planId, principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Your plan is active. Its templates are now available.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/plan";
    }

    @PostMapping("/cancel")
    public String cancel(@AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            subscriptions.cancel(TenantScope.from(principal), principal.requireCustomerId(), principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Your plan has been cancelled.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/plan";
    }
}
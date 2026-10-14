package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
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
        var subscription = subscriptions.findActiveSubscription(scope, customerId).orElse(null);
        var published = plans.findActive();
        model.addAttribute("subscription", subscription);
        model.addAttribute("history", subscriptions.findHistory(scope, customerId));
        model.addAttribute("plans", published);
        model.addAttribute("templates", subscriptions.accessibleTemplates(scope));
        model.addAttribute("quota", subscriptions.usage(scope, customerId));
        java.time.LocalDate today = subscriptions.today();
        model.addAttribute("today", today);
        // The countdown is computed once, server-side, so the page never does date maths in a template.
        model.addAttribute("daysLeft", subscription == null || subscription.getEndsOn() == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(today, subscription.getEndsOn()));
        model.addAttribute("offers", offers(subscription, published, model));
        return "portal/plan";
    }

    /**
     * What each card's button means for this account right now - choose, renew, upgrade or downgrade -
     * and whether the plan's own mid-term rules allow it. The refusal is only stated here for the
     * customer's benefit; {@code SubscriptionService.activate} is what actually enforces it.
     */
    private Map<Long, PlanOffer> offers(com.ritpl.billgen.entity.CustomerSubscription current,
            java.util.List<com.ritpl.billgen.entity.SubscriptionPlan> published, Model model) {
        Map<Long, PlanOffer> offers = new LinkedHashMap<>();
        boolean midterm = current != null;
        if (midterm) {
            model.addAttribute("midtermChangeAllowed", current.getPlan().isAllowMidtermUpgrade());
        }
        for (var plan : published) {
            if (midterm && current.getPlan().getId().equals(plan.getId())) {
                offers.put(plan.getId(), new PlanOffer("Renew", true,
                        "Renewing buys another term of this plan and starts it with a clean allowance.",
                        plan.getBillingCycle().getLabel()));
                continue;
            }
            if (midterm) {
                boolean cheaper = plan.getMonthlyPrice() != null && current.getPlan().getMonthlyPrice() != null
                        && plan.getMonthlyPrice().compareTo(current.getPlan().getMonthlyPrice()) < 0;
                if (!current.getPlan().isAllowMidtermUpgrade()) {
                    offers.put(plan.getId(), new PlanOffer("Change at " + current.getEndsOn(), false,
                            "Your " + current.getPlan().getName() + " plan does not allow a mid-term change. Its access runs to "
                                    + current.getEndsOn() + ".",
                            plan.getBillingCycle().getLabel()));
                    continue;
                }
                if (cheaper && !plan.isAllowDowngrade()) {
                    offers.put(plan.getId(), new PlanOffer("Downgrade at " + current.getEndsOn(), false,
                            "That plan does not accept a mid-term downgrade. Switch from " + current.getEndsOn()
                                    + " instead, or keep your current plan.",
                            plan.getBillingCycle().getLabel()));
                    continue;
                }
                offers.put(plan.getId(), new PlanOffer(cheaper ? "Downgrade" : "Upgrade", true,
                        "Takes effect today. Invoice usage already recorded in this term carries over to the new plan; the allowance is not topped up until the next term.",
                        plan.getBillingCycle().getLabel()));
                continue;
            }
            offers.put(plan.getId(), new PlanOffer("Choose this plan", true,
                    "Access starts as soon as it is chosen - this workspace has no payment checkout, so no charge is taken here.",
                    plan.getBillingCycle().getLabel()));
        }
        return offers;
    }

    /** The button on a plan card, with the rule behind it in the customer's own words. */
    public record PlanOffer(String label, boolean allowed, String reason, String cycle) { }

    /**
     * Buying a term. {@code cycle} is MONTHLY or YEARLY; anything else (including nothing) buys the
     * plan's own billing period, so the dates always come from the plan rather than from the page.
     * Refusals - a retired plan, a plan no longer offered, a mid-term change the current plan forbids -
     * are raised by the service, so the same rules hold for an administrator's assignment.
     */
    @PostMapping
    public String select(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam long planId,
            @RequestParam(required = false, defaultValue = "") String cycle, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = principal.requireCustomerId();
        try {
            var plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Choose a valid plan."));
            String wanted = cycle == null ? "" : cycle.trim().toUpperCase(java.util.Locale.ROOT);
            if (!wanted.isEmpty() && !wanted.equals("MONTHLY") && !wanted.equals("YEARLY")) {
                throw new IllegalArgumentException("Choose a monthly or a yearly term.");
            }
            java.time.LocalDate start = subscriptions.today();
            java.time.LocalDate end = wanted.isEmpty() ? start.plus(plan.usageWindow()).minusDays(1)
                    : wanted.equals("YEARLY") ? start.plusYears(1).minusDays(1) : start.plusMonths(1).minusDays(1);
            var saved = subscriptions.activate(scope, customerId, planId, principal.getUsername(), start, end);
            redirect.addFlashAttribute("successMessage", plan.getName() + " is active from " + start + " to " + end
                    + ". Its templates are available now, and " + saved.getEffectiveInvoiceLimit()
                    + " invoice(s) are allowed for this term across " + saved.purchasedCycles()
                    + " billing period(s).");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/plan";
    }

    /** Renewal on the plan's own cycle, or the next monthly term for an account whose term has expired. */
    @PostMapping("/renew")
    public String renew(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false, defaultValue = "") String cycle, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = principal.requireCustomerId();
        try {
            var history = subscriptions.findHistory(scope, customerId);
            var current = subscriptions.findActiveSubscription(scope, customerId)
                    .or(() -> history.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(history.get(0)))
                    .orElseThrow(() -> new IllegalArgumentException("Choose a plan to renew first."));
            var plan = current.getPlan();
            String wanted = cycle == null ? "" : cycle.trim().toUpperCase(java.util.Locale.ROOT);
            java.time.LocalDate start = subscriptions.today();
            java.time.LocalDate end = wanted.equals("YEARLY") ? start.plusYears(1).minusDays(1)
                    : wanted.equals("MONTHLY") ? start.plusMonths(1).minusDays(1)
                            : start.plus(plan.usageWindow()).minusDays(1);
            subscriptions.activate(scope, customerId, plan.getId(), principal.getUsername(), start, end);
            redirect.addFlashAttribute("successMessage", plan.getName() + " renewed for the term " + start + " to " + end
                    + ". A new term starts with a clean allowance.");
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
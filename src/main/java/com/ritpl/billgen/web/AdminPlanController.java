package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.TemplateService;
import com.ritpl.billgen.web.form.PlanForm;
import jakarta.validation.Valid;

/** Administrator management of plans and the bill templates bundled with each plan. */
@Controller
@RequestMapping("/admin/plans")
public class AdminPlanController {
    private final PlanService plans;
    private final TemplateService templates;
    private final SubscriptionService subscriptions;
    private final com.ritpl.billgen.service.AuditTypeService auditTypes;

    public AdminPlanController(PlanService plans, TemplateService templates, SubscriptionService subscriptions,
            com.ritpl.billgen.service.AuditTypeService auditTypes) {
        this.plans = plans; this.templates = templates; this.subscriptions = subscriptions; this.auditTypes = auditTypes;
    }

    @GetMapping
    public String list(Model model) {
        var all = plans.findLive();
        java.util.Map<Long, Long> subscriberCounts = new java.util.HashMap<>();
        all.forEach(plan -> subscriberCounts.put(plan.getId(), subscriptions.subscriberCount(com.ritpl.billgen.security.TenantScope.administrator(), plan.getId())));
        java.util.Map<Long, Long> issuedCounts = new java.util.HashMap<>();
        all.forEach(plan -> issuedCounts.put(plan.getId(), plans.activeSubscriberCount(plan.getId())));
        model.addAttribute("plans", all);
        model.addAttribute("subscriberCounts", subscriberCounts);
        model.addAttribute("grandfatheredCounts", issuedCounts);
        model.addAttribute("activeCount", plans.findActive().size());
        return "plans/admin-list";
    }

    /** Read-only plan view: entitlements as subscribed accounts currently see them. */
    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        var plan = plans.findById(id);
        model.addAttribute("plan", plan);
        model.addAttribute("subscriberCount", subscriptions.subscriberCount(com.ritpl.billgen.security.TenantScope.administrator(), id));
        model.addAttribute("grandfatheredCount", plans.activeSubscriberCount(id));
        model.addAttribute("auditTypeCount", plan.getAuditTypes().size());
        return "plans/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("planForm", new PlanForm());
        return form(model, "Add subscription plan", "/admin/plans");
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("planForm") PlanForm planForm, BindingResult errors, Model model,
            RedirectAttributes redirect, @org.springframework.security.core.annotation.AuthenticationPrincipal com.ritpl.billgen.security.AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Add subscription plan", "/admin/plans");
        try {
            plans.save(null, planForm.toCommand(), (principal == null ? "admin" : principal.getUsername()));
            redirect.addFlashAttribute("successMessage", "Plan created.");
            return "redirect:/admin/plans";
        } catch (IllegalArgumentException e) {
            errors.reject("plan.invalid", e.getMessage());
            return form(model, "Add subscription plan", "/admin/plans");
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("planForm", PlanForm.from(plans.findById(id)));
        return form(model, "Edit subscription plan", "/admin/plans/" + id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("planForm") PlanForm planForm, BindingResult errors,
            Model model, RedirectAttributes redirect, @org.springframework.security.core.annotation.AuthenticationPrincipal com.ritpl.billgen.security.AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Edit subscription plan", "/admin/plans/" + id);
        try {
            plans.save(id, planForm.toCommand(), (principal == null ? "admin" : principal.getUsername()));
            redirect.addFlashAttribute("successMessage", "Plan updated.");
            return "redirect:/admin/plans";
        } catch (IllegalArgumentException e) {
            errors.reject("plan.invalid", e.getMessage());
            return form(model, "Edit subscription plan", "/admin/plans/" + id);
        }
    }

    /** Deactivating never terminates a subscription; the message states how many are grandfathered. */
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, RedirectAttributes redirect) {
        var plan = plans.findById(id);
        try {
            long grandfathered = plans.setActive(id, !plan.isActive());
            redirect.addFlashAttribute("successMessage", plan.isActive()
                    ? "Plan deactivated for new subscribers. " + grandfathered + " existing subscription(s) continue unchanged until their term ends."
                    : "Plan is available to new subscribers again.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/plans";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.ritpl.billgen.security.AppUserPrincipal principal,
            RedirectAttributes redirect) {
        try {
            plans.softDelete(id, principal == null ? "admin" : principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Plan retired. Historical subscriptions still reference it.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/plans";
    }

    private String form(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("templates", templates.findActive());
        model.addAttribute("auditTypes", auditTypes.findActive());
        model.addAttribute("billingCycles", java.util.List.of(com.ritpl.billgen.entity.BillingCycle.values()));
        model.addAttribute("overLimitBehaviors", java.util.List.of(com.ritpl.billgen.entity.OverLimitBehavior.values()));
        return "plans/form";
    }
}
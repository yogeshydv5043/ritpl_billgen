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

    public AdminPlanController(PlanService plans, TemplateService templates, SubscriptionService subscriptions) {
        this.plans = plans; this.templates = templates; this.subscriptions = subscriptions;
    }

    @GetMapping
    public String list(Model model) {
        var all = plans.findAll();
        java.util.Map<Long, Long> subscriberCounts = new java.util.HashMap<>();
        all.forEach(plan -> subscriberCounts.put(plan.getId(), subscriptions.subscriberCount(com.ritpl.billgen.security.TenantScope.administrator(), plan.getId())));
        model.addAttribute("plans", all);
        model.addAttribute("subscriberCounts", subscriberCounts);
        return "plans/admin-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("planForm", new PlanForm());
        return form(model, "Add subscription plan", "/admin/plans");
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("planForm") PlanForm planForm, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Add subscription plan", "/admin/plans");
        try {
            plans.save(null, planForm.toCommand());
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
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Edit subscription plan", "/admin/plans/" + id);
        try {
            plans.save(id, planForm.toCommand());
            redirect.addFlashAttribute("successMessage", "Plan updated.");
            return "redirect:/admin/plans";
        } catch (IllegalArgumentException e) {
            errors.reject("plan.invalid", e.getMessage());
            return form(model, "Edit subscription plan", "/admin/plans/" + id);
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, RedirectAttributes redirect) {
        var plan = plans.findById(id);
        plans.setActive(id, !plan.isActive());
        redirect.addFlashAttribute("successMessage", "Plan availability updated.");
        return "redirect:/admin/plans";
    }

    private String form(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("templates", templates.findActive());
        return "plans/form";
    }
}
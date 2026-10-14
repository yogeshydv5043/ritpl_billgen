package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomTemplateService;
import com.ritpl.billgen.web.form.CustomTemplateForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/portal/templates")
public class PortalCustomTemplateController {
    private final CustomTemplateService templates;
    private final com.ritpl.billgen.service.CustomerSettingsService settings;
    private final com.ritpl.billgen.service.SubscriptionService subscriptions;
    private final com.ritpl.billgen.service.CustomerService customers;
    public PortalCustomTemplateController(CustomTemplateService templates,
            com.ritpl.billgen.service.CustomerSettingsService settings,
            com.ritpl.billgen.service.SubscriptionService subscriptions,
            com.ritpl.billgen.service.CustomerService customers) {
        this.templates = templates; this.settings = settings; this.subscriptions = subscriptions; this.customers = customers;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        model.addAttribute("templates", templates.findAll(scope));
        model.addAttribute("planTemplates", subscriptions.accessibleTemplates(scope));
        model.addAttribute("quota", subscriptions.usage(scope, scope.requireCustomerId()));
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, scope.requireCustomerId()).orElse(null));
        model.addAttribute("settings", settings.get(scope, scope.requireCustomerId()));
        if (!model.containsAttribute("templateForm")) model.addAttribute("templateForm", new CustomTemplateForm());
        return "portal/templates";
    }
    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            @RequestParam(name = "style", required = false) String style) {
        CustomTemplateForm form = new CustomTemplateForm();
        // "Customize" on a plan template arrives with that layout already chosen, because a custom
        // design is a copy the account owns - the system template itself is never edited.
        if (style != null && !style.isBlank()) {
            for (BillTemplateStyle option : BillTemplateStyle.values()) {
                if (option.name().equalsIgnoreCase(style.trim())) { form.setStyleKey(option.name()); break; }
            }
        }
        model.addAttribute("templateForm", form);
        return form(principal, model, "Create custom template", "/portal/templates");
    }

    /** Choosing a plan template as the account's default; the entitlement is re-checked by the service. */
    @PostMapping("/plan-template/{id}/default")
    public String makePlanDefault(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        try {
            var chosen = subscriptions.requireAccessibleTemplate(scope, id);
            var current = settings.get(scope, customerId);
            settings.save(scope, new com.ritpl.billgen.service.CustomerSettingsCommand(
                    chosen.getId(), null, current.getDefaultCurrency(), current.getInvoiceNumberPrefix(),
                    current.getDefaultDueDays(), current.getDefaultTaxRate(), current.getDefaultPaymentTerms(),
                    current.getDefaultNotes(), current.getDefaultFooter(), current.getDefaultUnit(),
                    current.getDefaultCategoryId(), current.getListPageSize(), current.getAuditHistoryPeriod(),
                    current.isNotifyExpiryReminders(), current.isNotifyQuotaAlerts(), current.isNotifyPaymentUpdates(),
                    current.isNotifySecurityAlerts(), current.getExportRangeDays()));
            redirect.addFlashAttribute("successMessage", chosen.getName() + " is now your default template.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/templates";
    }

    /**
     * Saving a design. {@code action=preview} renders the same page with the values the form holds and
     * writes nothing, so the colours, fonts and text blocks can be judged before they are committed.
     */
    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("templateForm") CustomTemplateForm form, BindingResult errors,
            @RequestParam(name = "action", required = false) String action, Model model, RedirectAttributes redirect) {
        if ("preview".equalsIgnoreCase(action)) {
            model.addAttribute("preview", form);
            return form(principal, model, "Create custom template", "/portal/templates");
        }
        if (errors.hasErrors()) return form(principal, model, "Create custom template", "/portal/templates");
        try {
            templates.save(TenantScope.from(principal), null, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Custom invoice template created. The system template it started from is untouched.");
            return "redirect:/portal/templates";
        } catch (IllegalArgumentException e) {
            errors.reject("template.invalid", e.getMessage());
            return form(principal, model, "Create custom template", "/portal/templates");
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("templateForm", CustomTemplateForm.from(templates.findById(TenantScope.from(principal), id)));
        model.addAttribute("editingId", id);
        return form(principal, model, "Edit custom template", "/portal/templates/" + id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("templateForm") CustomTemplateForm form, BindingResult errors,
            @RequestParam(name = "action", required = false) String action, Model model, RedirectAttributes redirect) {
        if ("preview".equalsIgnoreCase(action)) {
            model.addAttribute("preview", form);
            model.addAttribute("editingId", id);
            return form(principal, model, "Edit custom template", "/portal/templates/" + id);
        }
        if (errors.hasErrors()) { model.addAttribute("editingId", id); return form(principal, model, "Edit custom template", "/portal/templates/" + id); }
        try {
            templates.save(TenantScope.from(principal), id, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Custom invoice template updated. Invoices already issued keep the layout they were sent with.");
            return "redirect:/portal/templates";
        } catch (IllegalArgumentException e) {
            errors.reject("template.invalid", e.getMessage());
            model.addAttribute("editingId", id);
            return form(principal, model, "Edit custom template", "/portal/templates/" + id);
        }
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try { templates.delete(TenantScope.from(principal), id); redirect.addFlashAttribute("successMessage", "Custom template deleted or archived."); }
        catch (IllegalArgumentException e) { redirect.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/portal/templates";
    }
    /** Makes one of the account's own templates the default for new invoices. */
    @PostMapping("/{id}/default")
    public String makeDefault(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        try {
            var template = templates.findById(scope, id);
            var current = settings.get(scope, scope.requireCustomerId());
            settings.save(scope, new com.ritpl.billgen.service.CustomerSettingsCommand(
                    current.getDefaultTemplateId(), template.isActive() ? template.getId() : null,
                    current.getDefaultCurrency(), current.getInvoiceNumberPrefix(), current.getDefaultDueDays(),
                    current.getDefaultTaxRate(), current.getDefaultPaymentTerms(), current.getDefaultNotes(),
                    current.getDefaultFooter(), current.getDefaultUnit(), current.getDefaultCategoryId(),
                    current.getListPageSize(), current.getAuditHistoryPeriod(), current.isNotifyExpiryReminders(),
                    current.isNotifyQuotaAlerts(), current.isNotifyPaymentUpdates(), current.isNotifySecurityAlerts(),
                    current.getExportRangeDays()));
            redirect.addFlashAttribute("successMessage", template.isActive()
                    ? "New invoices start on " + template.getName() + "."
                    : "The default was cleared because this template is hidden.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/templates";
    }

    /** A copy, so a design can be branched without touching the original. */
    @PostMapping("/{id}/duplicate")
    public String duplicate(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            var copy = templates.duplicate(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage", "Copied as " + copy.getName() + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/templates";
    }

    /** Back to the plain layout: the accent and text blocks this account added are dropped. */
    @PostMapping("/{id}/reset")
    public String reset(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            templates.resetPresentation(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage", "Template reset to its plain layout. Your invoices are unchanged.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/templates";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        try {
            var template = templates.findById(scope, id);
            templates.setActive(scope, id, !template.isActive());
            redirect.addFlashAttribute("successMessage", template.isActive()
                    ? "Template hidden from new invoices. Issued invoices keep their layout."
                    : "Template available again.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/templates";
    }

    /** The plan gate and the company details the design preview prints, so the form cannot offer what the plan forbids. */
    private String form(AppUserPrincipal principal, Model model, String title, String action) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        model.addAttribute("formTitle", title); model.addAttribute("formAction", action);
        model.addAttribute("styles", BillTemplateStyle.values());
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, customerId).orElse(null));
        model.addAttribute("quota", subscriptions.usage(scope, customerId));
        model.addAttribute("customer", customers.findById(scope, customerId));
        model.addAttribute("settings", settings.get(scope, customerId));
        return "portal/template-form";
    }
}

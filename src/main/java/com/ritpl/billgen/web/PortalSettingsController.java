package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.CustomerSettingsService;
import com.ritpl.billgen.service.CustomTemplateService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.web.form.CustomerSettingsForm;
import jakarta.validation.Valid;

/**
 * The account's own settings: invoice defaults, catalogue defaults, how far the audit trail is
 * browsable, which platform messages arrive, and how wide an export reaches. Everything here is scoped
 * to the signed-in account, and the entitlement to a default template is checked against the plan on
 * save rather than assumed from what the browser was shown.
 */
@Controller
@RequestMapping("/portal/settings")
public class PortalSettingsController {
    private final CustomerSettingsService settings;
    private final CustomerService customers;
    private final SubscriptionService subscriptions;
    private final CustomTemplateService customTemplates;
    private final ProductCategoryService categories;

    public PortalSettingsController(CustomerSettingsService settings, CustomerService customers,
            SubscriptionService subscriptions, CustomTemplateService customTemplates, ProductCategoryService categories) {
        this.settings = settings; this.customers = customers; this.subscriptions = subscriptions;
        this.customTemplates = customTemplates; this.categories = categories;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        if (!model.containsAttribute("settingsForm")) {
            model.addAttribute("settingsForm", CustomerSettingsForm.from(settings.get(scope, customerId)));
        }
        prepare(scope, customerId, model);
        return "portal/settings";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("settingsForm") CustomerSettingsForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        if (errors.hasErrors()) {
            prepare(scope, customerId, model);
            return "portal/settings";
        }
        try {
            settings.save(scope, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Settings saved. New invoices and lists use them straight away.");
        } catch (IllegalArgumentException e) {
            errors.reject("settings.invalid", e.getMessage());
            prepare(scope, customerId, model);
            return "portal/settings";
        }
        return "redirect:/portal/settings";
    }

    private void prepare(TenantScope scope, long customerId, Model model) {
        model.addAttribute("customer", customers.findById(scope, customerId));
        model.addAttribute("settings", settings.get(scope, customerId));
        model.addAttribute("planTemplates", subscriptions.accessibleTemplates(scope));
        model.addAttribute("customTemplates", customTemplates.findActive(scope));
        model.addAttribute("categories", categories.findAll(scope));
        model.addAttribute("quota", subscriptions.usage(scope, customerId));
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, customerId).orElse(null));
        model.addAttribute("auditPeriods", com.ritpl.billgen.entity.AuditHistoryPeriod.values());
        model.addAttribute("pageSizes", new int[] { 10, 20, 50, 100 });
    }
}

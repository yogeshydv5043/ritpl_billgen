package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AccountStatsService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.web.form.CustomerForm;
import jakarta.validation.Valid;

/**
 * Administrator view of customer accounts. Everything shown here is read-only across accounts;
 * the administrator provisions accounts and plans, while each account manages its own invoices,
 * products and categories.
 */
@Controller
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService customers;
    private final AccountStatsService accountStats;
    private final InvoiceService invoices;
    private final CatalogService catalog;
    private final ProductCategoryService categories;
    private final PlanService plans;
    private final SubscriptionService subscriptions;

    public CustomerController(CustomerService customers, AccountStatsService accountStats, InvoiceService invoices,
            CatalogService catalog, ProductCategoryService categories, PlanService plans, SubscriptionService subscriptions) {
        this.customers = customers; this.accountStats = accountStats; this.invoices = invoices;
        this.catalog = catalog; this.categories = categories; this.plans = plans; this.subscriptions = subscriptions;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("accountStats", accountStats.findAll(TenantScope.administrator(), q));
        model.addAttribute("q", q);
        return "customers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("customerForm", new CustomerForm());
        model.addAttribute("formTitle", "Add customer account");
        model.addAttribute("formAction", "/customers");
        return "customers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Add customer account", "/customers");
        try {
            var customer = customers.createAccount(TenantScope.administrator(), form.toCommand());
            redirect.addFlashAttribute("successMessage", "Customer account created.");
            return "redirect:/customers/" + customer.getId();
        } catch (IllegalArgumentException e) {
            errors.reject("customer.invalid", e.getMessage());
            return form(model, "Add customer account", "/customers");
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        TenantScope admin = TenantScope.administrator();
        model.addAttribute("customer", customers.findById(admin, id));
        model.addAttribute("stats", accountStats.findOne(admin, id));
        model.addAttribute("invoices", invoices.findForCustomer(admin, id));
        model.addAttribute("products", catalog.findForCustomer(admin, id));
        model.addAttribute("categories", categories.findForCustomer(admin, id));
        model.addAttribute("subscriptionHistory", subscriptions.findHistory(admin, id));
        model.addAttribute("plans", plans.findActive());
        return "customers/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        TenantScope admin = TenantScope.administrator();
        model.addAttribute("customerForm", CustomerForm.from(customers.findById(admin, id), customers.findLogin(admin, id).orElse(null)));
        model.addAttribute("formTitle", "Edit customer account");
        model.addAttribute("formAction", "/customers/" + id);
        return "customers/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("customerForm") CustomerForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Edit customer account", "/customers/" + id);
        try {
            customers.updateAccount(TenantScope.administrator(), id, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Customer account updated.");
            return "redirect:/customers/" + id;
        } catch (IllegalArgumentException e) {
            errors.reject("customer.invalid", e.getMessage());
            return form(model, "Edit customer account", "/customers/" + id);
        }
    }

    /** Assigns a plan to an account; this is what unlocks the plan's bill templates for it. */
    @PostMapping("/{id}/plan")
    public String assignPlan(@PathVariable long id, @RequestParam long planId, RedirectAttributes redirect) {
        try {
            var subscription = subscriptions.activate(TenantScope.administrator(), id, planId, "admin");
            redirect.addFlashAttribute("successMessage",
                    "Plan " + subscription.getPlan().getName() + " is now active for this account.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/plan/cancel")
    public String cancelPlan(@PathVariable long id, RedirectAttributes redirect) {
        try {
            subscriptions.cancel(TenantScope.administrator(), id, "admin");
            redirect.addFlashAttribute("successMessage", "Plan cancelled. Template access has been withdrawn.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/login/toggle")
    public String toggleLogin(@PathVariable long id, RedirectAttributes redirect) {
        var login = customers.findLogin(TenantScope.administrator(), id);
        if (login.isEmpty()) {
            redirect.addFlashAttribute("errorMessage", "This account has no login.");
            return "redirect:/customers/" + id;
        }
        boolean enabled = !login.get().isEnabled();
        customers.setLoginEnabled(TenantScope.administrator(), id, enabled);
        redirect.addFlashAttribute("successMessage", enabled ? "Login enabled." : "Login disabled.");
        return "redirect:/customers/" + id;
    }

    private String form(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        return "customers/form";
    }
}
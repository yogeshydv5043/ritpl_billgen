package com.ritpl.billgen.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AccountStatsService;
import com.ritpl.billgen.service.AuditFilter;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.NotificationService;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.repository.CustomerFilter;
import com.ritpl.billgen.web.form.CustomerForm;
import jakarta.validation.Valid;

/**
 * Module 2, Customer Management. The administrator provisions and supervises company accounts;
 * everything cross-account is read-only, because each account owns its own invoices, products and
 * audit history. Deletion is always soft: history is preserved and a mistake can be undone.
 */
@Controller
@RequestMapping("/customers")
public class CustomerController {
    /** Whitelisted sort keys; a request can never name an arbitrary entity property. */
    private static final Map<String, String> SORTS = Map.of(
            "company", "companyName", "contact", "contactName", "created", "createdAt", "status", "status", "email", "email");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CustomerService customers;
    private final AccountStatsService accountStats;
    private final InvoiceService invoices;
    private final CatalogService catalog;
    private final ProductCategoryService categories;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final AuditService audit;
    private final NotificationService notifications;

    public CustomerController(CustomerService customers, AccountStatsService accountStats, InvoiceService invoices,
            CatalogService catalog, ProductCategoryService categories, PlanService plans,
            SubscriptionService subscriptions, AuditService audit, NotificationService notifications) {
        this.customers = customers; this.accountStats = accountStats; this.invoices = invoices;
        this.catalog = catalog; this.categories = categories; this.plans = plans; this.subscriptions = subscriptions;
        this.audit = audit; this.notifications = notifications;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String subscribed,
            @RequestParam(required = false, defaultValue = "false") boolean expiringSoon,
            @RequestParam(required = false, defaultValue = "false") boolean expired,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long plan,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "created") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir,
            Model model) {
        CustomerFilter filter = new CustomerFilter(q, statusOf(status), boolOf(subscribed),
                expiringSoon ? Boolean.TRUE : null, expired ? Boolean.TRUE : null, from, to, plan, false);
        Page<com.ritpl.billgen.service.CustomerAccountStats> rows = accountStats.page(TenantScope.administrator(),
                filter, PageRequest.of(Math.max(0, page), DEFAULT_PAGE_SIZE, sortOf(sort, dir)));
        model.addAttribute("accounts", rows);
        model.addAttribute("accountStats", rows.getContent());
        model.addAttribute("plans", plans.findLive());
        model.addAttribute("filter", filter);
        model.addAttribute("status", status);
        model.addAttribute("subscribed", subscribed);
        model.addAttribute("expiringSoon", expiringSoon);
        model.addAttribute("expired", expired);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("plan", plan);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "customers/list";
    }

    /** The separate deleted-accounts view required by the soft-deletion rules. */
    @GetMapping("/deleted")
    public String deleted(Model model) {
        model.addAttribute("accounts", accountStats.findDeleted(TenantScope.administrator()));
        return "customers/deleted";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("customerForm", new CustomerForm());
        return form(model, "Add customer account", "/customers", null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Add customer account", "/customers", null);
        try {
            var customer = customers.createAccount(TenantScope.administrator(), form.toCommand());
            if (form.isDisabled()) {
                customers.setAccountStatus(TenantScope.administrator(), customer.getId(), AccountStatus.DISABLED, "admin");
            }
            if (form.getPlanId() != null) {
                subscriptions.activate(TenantScope.administrator(), customer.getId(), form.getPlanId(),
                        "admin", form.getStartedOn(), form.getEndsOn());
            }
            redirect.addFlashAttribute("successMessage", "Customer account created.");
            return "redirect:/customers/" + customer.getId();
        } catch (IllegalArgumentException e) {
            errors.reject("customer.invalid", e.getMessage());
            return form(model, "Add customer account", "/customers", null);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id,
            @RequestParam(required = false, defaultValue = "company") String tab,
            @RequestParam(required = false, defaultValue = "0") int page, Model model) {
        TenantScope admin = TenantScope.administrator();
        // Deleted accounts stay reachable from the deleted view so an admin can inspect and restore
        // them; findByIdIncludingDeleted is the only read path that permits that.
        model.addAttribute("customer", customers.findByIdIncludingDeleted(admin, id));
        model.addAttribute("stats", accountStats.findOne(admin, id));
        model.addAttribute("invoices", invoices.findForCustomer(admin, id));
        model.addAttribute("products", catalog.findForCustomer(admin, id));
        model.addAttribute("categories", categories.findForCustomer(admin, id));
        model.addAttribute("subscriptionHistory", subscriptions.findHistory(admin, id));
        model.addAttribute("plans", plans.findLive());
        model.addAttribute("login", customers.findLogin(admin, id).orElse(null));
        model.addAttribute("deletionAssessment", customers.deletionAssessment(admin, id));
        model.addAttribute("auditRecords", audit.search(admin, new AuditFilter(id, null, null, null, null, null, null),
                PageRequest.of(Math.max(0, page), 25)));
        model.addAttribute("deliveries", notifications.recentForCustomer(id));
        model.addAttribute("tab", tab);
        return "customers/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        TenantScope admin = TenantScope.administrator();
        model.addAttribute("customerForm", CustomerForm.from(customers.findById(admin, id),
                customers.findLogin(admin, id).orElse(null)));
        model.addAttribute("plans", plans.findLive());
        model.addAttribute("activeSubscription", subscriptions.findActiveSubscription(admin, id).orElse(null));
        return form(model, "Edit customer account", "/customers/" + id, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("customerForm") CustomerForm form,
            BindingResult errors, Model model, RedirectAttributes redirect,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        if (errors.hasErrors()) {
            model.addAttribute("plans", plans.findLive());
            return form(model, "Edit customer account", "/customers/" + id, id);
        }
        try {
            customers.updateAccount(TenantScope.administrator(), id, form.toCommand());
            AccountStatus wanted = form.isDisabled() ? AccountStatus.DISABLED : AccountStatus.ACTIVE;
            customers.setAccountStatus(TenantScope.administrator(), id, wanted, principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Customer account updated.");
            return "redirect:/customers/" + id;
        } catch (IllegalArgumentException e) {
            errors.reject("customer.invalid", e.getMessage());
            model.addAttribute("plans", plans.findLive());
            return form(model, "Edit customer account", "/customers/" + id, id);
        }
    }

    /** Assigns a plan to an account; this is what unlocks the plan's bill templates for it. */
    @PostMapping("/{id}/plan")
    public String assignPlan(@PathVariable long id, @RequestParam long planId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startedOn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            var subscription = subscriptions.activate(TenantScope.administrator(), id, planId, principal.getUsername(),
                    startedOn, endsOn);
            redirect.addFlashAttribute("successMessage",
                    "Plan " + subscription.getPlan().getName() + " is now active for this account.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/plan/cancel")
    public String cancelPlan(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        try {
            subscriptions.cancel(TenantScope.administrator(), id, principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Plan cancelled. Template access has been withdrawn.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable long id, @RequestParam String status,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        AccountStatus wanted = "ACTIVE".equalsIgnoreCase(status) ? AccountStatus.ACTIVE : AccountStatus.DISABLED;
        try {
            customers.setAccountStatus(TenantScope.administrator(), id, wanted, principal.getUsername());
            redirect.addFlashAttribute("successMessage", wanted == AccountStatus.ACTIVE
                    ? "Account enabled. The customer can sign in again."
                    : "Account disabled. Its data is untouched and the login is blocked.");
        } catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    /** Soft delete. The reason is mandatory and the confirmation is re-checked on the server. */
    @PostMapping("/{id}/delete")
    public String softDelete(@PathVariable long id, @RequestParam(required = false) String reason,
            @RequestParam(required = false, defaultValue = "false") boolean force,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            customers.softDelete(TenantScope.administrator(), id, reason, force, principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Account deleted. Invoices and audit history were preserved.");
            return "redirect:/customers";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/customers/" + id;
        }
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        try {
            var customer = customers.restore(TenantScope.administrator(), id, principal.getUsername());
            redirect.addFlashAttribute("successMessage", customer.getCompanyName()
                    + " was restored. Enable the account when you are ready for it to sign in.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers/deleted";
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

    private String form(Model model, String title, String action, Long entityId) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        // Plan and term changes are a subscription action, not a profile edit, so the same template
        // renders both modes and only the create form offers plan selection.
        model.addAttribute("editing", entityId != null);
        model.addAttribute("entityId", entityId);
        model.addAttribute("plans", plans.findLive());
        return "customers/form";
    }

    private static AccountStatus statusOf(String value) {
        if (value == null || value.isBlank()) return null;
        if ("ACTIVE".equalsIgnoreCase(value)) return AccountStatus.ACTIVE;
        if ("DISABLED".equalsIgnoreCase(value)) return AccountStatus.DISABLED;
        return null;
    }

    private static Boolean boolOf(String value) {
        if (value == null || value.isBlank()) return null;
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) return Boolean.FALSE;
        return null;
    }

    private static Sort sortOf(String sort, String dir) {
        String property = SORTS.getOrDefault(sort == null ? "created" : sort, "createdAt");
        return Sort.by("asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC, property);
    }

    static List<String> sortableKeys() { return List.copyOf(SORTS.keySet()); }
}

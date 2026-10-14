package com.ritpl.billgen.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.repository.InvoiceFilter;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import com.ritpl.billgen.service.CustomTemplateService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.UsageQuota;
import com.ritpl.billgen.web.form.InvoiceForm;
import jakarta.validation.Valid;

/**
 * Invoice screens of a customer account. The account comes from the session and the template is
 * validated against the purchased plan by the service, so neither the owner nor the template can be
 * chosen by the request.
 *
 * <p>Three things the specification asks for are enforced here rather than in the page: a preview is
 * computed by the same code that issues the document but writes nothing; a repeated submission of the
 * same form is refused by a one-time token; and a cancelled invoice is described in the same terms the
 * service uses, including that a slot already spent on an issued invoice is not returned.</p>
 */
@Controller
@RequestMapping("/portal/invoices")
public class PortalInvoiceController {
    /** Only these sort keys may reach the query; a request can never name an entity property. */
    private static final Map<String, String> SORTS = Map.of(
            "number", "invoiceNumber", "issued", "issueDate", "due", "dueDate",
            "amount", "totalAmount", "status", "status", "created", "createdAt");

    private final InvoiceService invoices;
    private final CatalogService catalog;
    private final SubscriptionService subscriptions;
    private final CustomTemplateService customTemplates;
    private final com.ritpl.billgen.service.CustomerSettingsService settings;
    private final FormTokenGuard tokens;
    private final com.ritpl.billgen.service.InvoicePdfService invoicePdf;

    public PortalInvoiceController(InvoiceService invoices, CatalogService catalog, SubscriptionService subscriptions,
            CustomTemplateService customTemplates, com.ritpl.billgen.service.CustomerSettingsService settings,
            FormTokenGuard tokens, com.ritpl.billgen.service.InvoicePdfService invoicePdf) {
        this.invoices = invoices; this.catalog = catalog; this.subscriptions = subscriptions;
        this.customTemplates = customTemplates; this.settings = settings; this.tokens = tokens; this.invoicePdf = invoicePdf;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "issued") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir,
            Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must not be before the start date.");
        }
        InvoiceFilter filter = new InvoiceFilter(q, statusOf(status), from, to,
                "overdue".equals(status) ? Boolean.TRUE : null, null);
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = SORTS.getOrDefault(sort, "issueDate");
        var listing = invoices.page(scope, filter,
                PageRequest.of(Math.max(0, page), settings.pageSize(customerId), Sort.by(direction, property)));
        model.addAttribute("page", listing);
        model.addAttribute("invoices", listing.getContent());
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("sort", SORTS.containsKey(sort) ? sort : "issued");
        model.addAttribute("dir", direction == Sort.Direction.ASC ? "asc" : "desc");
        model.addAttribute("activeFilters", filter.hasAnyFilter());
        prepare(scope, customerId, model);
        return "portal/invoices";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal principal,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        if (!model.containsAttribute("invoiceForm")) model.addAttribute("invoiceForm", blankForm(scope, customerId));
        model.addAttribute("submitToken", tokens.issue(request, "invoice"));
        prepare(scope, customerId, model);
        return "portal/invoice-form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("invoiceForm") InvoiceForm form, BindingResult errors,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "submitToken", required = false) String submitToken,
            jakarta.servlet.http.HttpServletRequest request, Model model, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        if (errors.hasErrors()) {
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
        // A preview is computed by the same maths the issue path uses, and writes nothing at all.
        if ("preview".equalsIgnoreCase(action)) {
            try {
                model.addAttribute("preview", invoices.preview(scope, form.toCommand()));
            } catch (IllegalArgumentException e) {
                errors.reject("invoice.preview", e.getMessage());
            }
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
        if (!tokens.consume(request, "invoice", submitToken)) {
            errors.reject("invoice.duplicate",
                    "This form has already been submitted. Check your invoice list before trying again - "
                            + "the earlier submission may already have created it.");
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
        if (form.getInitialStatus() != InvoiceStatus.DRAFT && form.getInitialStatus() != InvoiceStatus.SENT) {
            errors.reject("invoice.status", "A new invoice must start as a draft or sent invoice.");
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
        try {
            var invoice = invoices.create(scope, form.toCommand());
            redirect.addFlashAttribute("successMessage", form.getInitialStatus() == InvoiceStatus.DRAFT
                    ? "Draft saved. A draft does not use an invoice slot until you issue it."
                    : "Invoice issued and counted against your plan for this period.");
            return "redirect:/portal/invoices/" + invoice.getId();
        } catch (IllegalArgumentException e) {
            errors.reject("invoice.invalid", e.getMessage());
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        model.addAttribute("invoice", invoices.findById(scope, id));
        model.addAttribute("quota", subscriptions.usage(scope, scope.requireCustomerId()));
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, scope.requireCustomerId()).orElse(null));
        return "portal/invoice";
    }

    /**
     * Editing a draft. The form is filled from the stored invoice - the figures it was saved with, not
     * what the catalogue says today - so an edit is a deliberate change rather than a silent refresh.
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        Invoice invoice = invoices.findById(scope, id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            return "redirect:/portal/invoices/" + id;
        }
        if (!model.containsAttribute("invoiceForm")) model.addAttribute("invoiceForm", formOf(invoice));
        model.addAttribute("editing", invoice);
        model.addAttribute("submitToken", tokens.issue(request, "invoice"));
        prepare(scope, customerId, model);
        return "portal/invoice-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("invoiceForm") InvoiceForm form, BindingResult errors,
            @RequestParam(name = "submitToken", required = false) String submitToken,
            jakarta.servlet.http.HttpServletRequest request, Model model, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        if (!tokens.consume(request, "invoice", submitToken)) {
            errors.reject("invoice.duplicate",
                    "This form has already been submitted. Check the draft before saving it again - the numbers are unchanged.");
            model.addAttribute("editing", invoices.findById(scope, id));
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
        if (errors.hasErrors()) {
            model.addAttribute("editing", invoices.findById(scope, id));
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
        try {
            invoices.updateDraft(scope, id, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Draft updated. Totals were recomputed on the server and nothing was sent yet.");
            return "redirect:/portal/invoices/" + id;
        } catch (IllegalArgumentException e) {
            errors.reject("invoice.invalid", e.getMessage());
            model.addAttribute("editing", invoices.findById(scope, id));
            model.addAttribute("submitToken", tokens.issue(request, "invoice"));
            prepare(scope, customerId, model);
            return "portal/invoice-form";
        }
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        invoices.recordPrintExport(TenantScope.from(principal), id);
        model.addAttribute("invoice", invoices.findById(TenantScope.from(principal), id));
        model.addAttribute("backUrl", "/portal/invoices/" + id);
        // The shared print view links to the caller's own download route, never the other role's.
        model.addAttribute("pdfUrl", "/portal/invoices/" + id + "/invoice.pdf");
        return "invoices/print";
    }

    @GetMapping("/{id}/invoice.pdf")
    public org.springframework.http.ResponseEntity<byte[]> downloadPdf(@PathVariable long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        // Authorization, the audit entry and the snapshot values all come from the service layer.
        var invoice = invoices.preparePdfDownload(TenantScope.from(principal), id);
        return InvoicePdfDownload.of(invoice, id, invoicePdf.render(invoice));
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            invoices.markSent(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage", "Invoice issued. It now counts against your plan for this period.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/invoices/" + id;
    }

    @PostMapping("/{id}/paid")
    public String paid(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            invoices.markPaid(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage", "Payment recorded.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/invoices/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            invoices.cancel(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage",
                    "Invoice cancelled. Its document and history are kept; a slot used by an issued invoice stays used, "
                            + "which is what keeps the allowance honest across the period.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/invoices/" + id;
    }

    /** The stored invoice, laid back out into the form it was created from. */
    private InvoiceForm formOf(Invoice invoice) {
        InvoiceForm form = new InvoiceForm();
        form.setTemplateId(invoice.getTemplate() == null ? null : invoice.getTemplate().getId());
        form.setCustomTemplateId(invoice.getCustomTemplate() == null ? null : invoice.getCustomTemplate().getId());
        form.setIssueDate(invoice.getIssueDate());
        form.setDueDate(invoice.getDueDate());
        form.setCurrency(invoice.getCurrency());
        form.setNotes(invoice.getNotes());
        form.setPaymentTerms(invoice.getPaymentTerms());
        form.setDiscountAmount(invoice.getDiscountAmount());
        form.setInitialStatus(invoice.getStatus());
        List<com.ritpl.billgen.web.form.InvoiceLineForm> lines = new ArrayList<>();
        for (var item : invoice.getItems()) {
            com.ritpl.billgen.web.form.InvoiceLineForm line = new com.ritpl.billgen.web.form.InvoiceLineForm();
            line.setDescription(item.getDescription());
            line.setQuantity(item.getQuantity());
            line.setUnitPrice(item.getUnitPrice());
            line.setTaxRate(item.getTaxRate());
            lines.add(line);
        }
        if (lines.isEmpty()) lines.add(new com.ritpl.billgen.web.form.InvoiceLineForm());
        form.setLines(lines);
        return form;
    }

    /** A new form, pre-filled with this account's own defaults rather than guessed values. */
    private InvoiceForm blankForm(TenantScope scope, long customerId) {
        InvoiceForm form = new InvoiceForm();
        com.ritpl.billgen.entity.CustomerSettings preferences = settings.get(scope, customerId);
        form.setCurrency(preferences.getDefaultCurrency());
        form.setNotes(preferences.getDefaultNotes());
        if (preferences.getDefaultTemplateId() != null) form.setTemplateId(preferences.getDefaultTemplateId());
        if (preferences.getDefaultCustomTemplateId() != null) form.setCustomTemplateId(preferences.getDefaultCustomTemplateId());
        LocalDate issue = LocalDate.now(subscriptions.zone());
        form.setIssueDate(issue);
        form.setDueDate(issue.plusDays(preferences.getDefaultDueDays()));
        return form;
    }

    private void prepare(TenantScope scope, long customerId, Model model) {
        var templates = subscriptions.accessibleTemplates(scope);
        boolean activePlan = subscriptions.findActiveSubscription(scope, customerId).isPresent();
        var customTemplateList = activePlan ? customTemplates.findActive(scope) : List.<com.ritpl.billgen.entity.CustomInvoiceTemplate>of();
        model.addAttribute("templates", templates);
        model.addAttribute("customTemplates", customTemplateList);
        model.addAttribute("hasTemplates", activePlan && (!templates.isEmpty() || !customTemplateList.isEmpty()));
        model.addAttribute("products", catalog.findActive(scope));
        model.addAttribute("statuses", selectableStatuses());
        UsageQuota quota = subscriptions.usage(scope, customerId);
        model.addAttribute("quota", quota);
        model.addAttribute("subscription", subscriptions.findActiveSubscription(scope, customerId).orElse(null));
        model.addAttribute("settings", settings.get(scope, customerId));
    }

    private static InvoiceStatus statusOf(String value) {
        if (value == null || value.isBlank() || "overdue".equals(value)) return null;
        try {
            return InvoiceStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException expected) {
            return null;
        }
    }

    /** Statuses a customer may create a new invoice with. */
    private static List<InvoiceStatus> selectableStatuses() { return List.of(InvoiceStatus.DRAFT, InvoiceStatus.SENT); }
}

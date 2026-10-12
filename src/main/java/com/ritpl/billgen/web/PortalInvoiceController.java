package com.ritpl.billgen.web;

import java.util.List;
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
import com.ritpl.billgen.entity.CustomInvoiceTemplate;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomTemplateService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.web.form.InvoiceForm;
import jakarta.validation.Valid;

/**
 * Invoice screens of a customer account. The account is taken from the session, and the template
 * is validated against the purchased plan by the service, so neither the invoice owner nor the
 * template can be chosen by the request.
 */
@Controller
@RequestMapping("/portal/invoices")
public class PortalInvoiceController {
    private final InvoiceService invoices;
    private final CatalogService catalog;
    private final SubscriptionService subscriptions;
    private final CustomTemplateService customTemplates;
    private final com.ritpl.billgen.service.InvoicePdfService invoicePdf;

    public PortalInvoiceController(InvoiceService invoices, CatalogService catalog, SubscriptionService subscriptions,
            CustomTemplateService customTemplates, com.ritpl.billgen.service.InvoicePdfService invoicePdf) {
        this.invoices = invoices; this.catalog = catalog; this.subscriptions = subscriptions; this.customTemplates = customTemplates;
        this.invoicePdf = invoicePdf;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        model.addAttribute("invoices", invoices.findAll(scope));
        var templates = subscriptions.accessibleTemplates(scope);
        boolean activePlan = subscriptions.findActiveSubscription(scope, scope.requireCustomerId()).isPresent();
        var customTemplateList = activePlan ? customTemplates.findActive(scope) : List.<com.ritpl.billgen.entity.CustomInvoiceTemplate>of();
        model.addAttribute("templates", templates);
        model.addAttribute("customTemplates", customTemplateList);
        model.addAttribute("hasTemplates", activePlan && (!templates.isEmpty() || !customTemplateList.isEmpty()));
        return "portal/invoices";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        prepareForm(scope, model);
        model.addAttribute("invoiceForm", new InvoiceForm());
        return "portal/invoice-form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("invoiceForm") InvoiceForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        prepareForm(scope, model);
        if (errors.hasErrors()) return "portal/invoice-form";
        if (form.getInitialStatus() != InvoiceStatus.DRAFT && form.getInitialStatus() != InvoiceStatus.SENT) {
            errors.reject("invoice.status", "A new invoice must start as a draft or sent invoice.");
            return "portal/invoice-form";
        }
        try {
            var invoice = invoices.create(scope, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Invoice created.");
            return "redirect:/portal/invoices/" + invoice.getId();
        } catch (IllegalArgumentException e) {
            errors.reject("invoice.invalid", e.getMessage());
            return "portal/invoice-form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("invoice", invoices.findById(TenantScope.from(principal), id));
        return "portal/invoice";
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
            redirect.addFlashAttribute("successMessage", "Invoice marked as sent.");
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

    private void prepareForm(TenantScope scope, Model model) {
        var templates = subscriptions.accessibleTemplates(scope);
        boolean activePlan = subscriptions.findActiveSubscription(scope, scope.requireCustomerId()).isPresent();
        var customTemplateList = activePlan ? customTemplates.findActive(scope) : List.<com.ritpl.billgen.entity.CustomInvoiceTemplate>of();
        model.addAttribute("templates", templates);
        model.addAttribute("customTemplates", customTemplateList);
        model.addAttribute("products", catalog.findActive(scope));
        model.addAttribute("statuses", selectableStatuses());
        model.addAttribute("hasTemplates", activePlan && (!templates.isEmpty() || !customTemplateList.isEmpty()));
    }

    /** Statuses a customer may create a new invoice with. */
    private static List<InvoiceStatus> selectableStatuses() { return List.of(InvoiceStatus.DRAFT, InvoiceStatus.SENT); }
}
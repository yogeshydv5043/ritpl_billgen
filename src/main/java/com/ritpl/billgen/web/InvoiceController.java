package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.InvoiceService;

/**
 * Administrator view of every invoice across all customer accounts. Read-only: invoices are
 * generated and maintained by the owning account in its own portal.
 */
@Controller
@RequestMapping("/invoices")
public class InvoiceController {
    private final InvoiceService invoices;
    private final com.ritpl.billgen.service.InvoicePdfService invoicePdf;

    public InvoiceController(InvoiceService invoices, com.ritpl.billgen.service.InvoicePdfService invoicePdf) {
        this.invoices = invoices; this.invoicePdf = invoicePdf;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long customerId, Model model) {
        TenantScope scope = TenantScope.from(principal);
        var all = invoices.findAll(scope);
        model.addAttribute("invoices", customerId == null ? all
                : all.stream().filter(invoice -> invoice.getCustomer().getId().equals(customerId)).toList());
        model.addAttribute("stats", invoices.dashboardStats(TenantScope.from(principal)));
        model.addAttribute("customerId", customerId);
        return "invoices/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("invoice", invoices.findById(TenantScope.from(principal), id));
        return "invoices/detail";
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        invoices.recordPrintExport(TenantScope.from(principal), id);
        model.addAttribute("invoice", invoices.findById(TenantScope.from(principal), id));
        model.addAttribute("backUrl", "/invoices/" + id);
        model.addAttribute("pdfUrl", "/invoices/" + id + "/invoice.pdf");
        return "invoices/print";
    }

    /** Read-only download of an invoice PDF; the tenant check and the audit entry happen in the service. */
    @GetMapping("/{id}/invoice.pdf")
    public org.springframework.http.ResponseEntity<byte[]> downloadPdf(@PathVariable long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        var invoice = invoices.preparePdfDownload(TenantScope.from(principal), id);
        return InvoicePdfDownload.of(invoice, id, invoicePdf.render(invoice));
    }
}
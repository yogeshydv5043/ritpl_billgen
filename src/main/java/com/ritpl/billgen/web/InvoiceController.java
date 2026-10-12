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

    public InvoiceController(InvoiceService invoices) { this.invoices = invoices; }

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
        return "invoices/print";
    }
}
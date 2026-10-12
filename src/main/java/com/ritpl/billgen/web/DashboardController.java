package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.service.AccountStatsService;
import com.ritpl.billgen.service.InvoiceService;

/** Administrator overview, including the customer-wise statistics table. */
@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    private final InvoiceService invoices;
    private final AccountStatsService accountStats;

    public DashboardController(InvoiceService invoices, AccountStatsService accountStats) {
        this.invoices = invoices; this.accountStats = accountStats;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", invoices.dashboardStats(com.ritpl.billgen.security.TenantScope.administrator()));
        model.addAttribute("recentInvoices", invoices.findRecent(com.ritpl.billgen.security.TenantScope.administrator()));
        model.addAttribute("accountStats", accountStats.findAll(com.ritpl.billgen.security.TenantScope.administrator(), null));
        return "dashboard";
    }
}
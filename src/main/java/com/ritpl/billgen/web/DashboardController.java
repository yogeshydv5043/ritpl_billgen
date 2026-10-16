package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AccountStatsService;
import com.ritpl.billgen.service.AdminOverviewService;
import com.ritpl.billgen.service.AuditTypeService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.JobService;
import com.ritpl.billgen.service.NotificationService;

/**
 * Module 1, the Super Admin dashboard. Every number comes from an aggregate query over live data;
 * each card links to the list that produced it, so a figure is always explainable.
 */
@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    private final InvoiceService invoices;
    private final AccountStatsService accountStats;
    private final AdminOverviewService overview;
    private final JobService jobs;
    private final NotificationService notifications;
    private final AuditTypeService auditTypes;

    public DashboardController(InvoiceService invoices, AccountStatsService accountStats, AdminOverviewService overview,
            JobService jobs, NotificationService notifications, AuditTypeService auditTypes) {
        this.invoices = invoices; this.accountStats = accountStats; this.overview = overview; this.jobs = jobs;
        this.notifications = notifications; this.auditTypes = auditTypes;
    }

    @GetMapping
    public String dashboard(Model model) {
        TenantScope admin = TenantScope.administrator();
        // A dashboard widget that cannot be computed must say so; the page never shows a
        // placeholder number in its place.
        try {
            model.addAttribute("overview", overview.load());
        } catch (RuntimeException e) {
            model.addAttribute("overviewError", "Live counts could not be read: " + e.getMessage());
        }
        model.addAttribute("stats", invoices.dashboardStats(admin));
        model.addAttribute("recentInvoices", invoices.findRecent(admin));
        model.addAttribute("accountStats", accountStats.findAll(admin, null));
        model.addAttribute("jobCounts", jobs.monitoring());
        model.addAttribute("deliveryCounts", notifications.deliveryCounts());
        model.addAttribute("auditTypeCount", auditTypes.activeCount());
        model.addAttribute("mailTransport", notifications.transportLabel());
        return "dashboard";
    }
}

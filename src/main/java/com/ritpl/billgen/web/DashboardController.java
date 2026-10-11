package com.ritpl.billgen.web;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    private final InvoiceService invoices;
    private final CustomerService customers;
    public DashboardController(InvoiceService invoices,CustomerService customers){this.invoices=invoices;this.customers=customers;}
    @GetMapping public String dashboard(Model model){
        List<Invoice> recent=invoices.findRecent();
        model.addAttribute("stats",invoices.dashboardStats());
        model.addAttribute("recentInvoices",recent.stream().limit(7).toList());
        model.addAttribute("customers",customers.findAll(null).stream().limit(5).toList());
        return "dashboard";
    }
}

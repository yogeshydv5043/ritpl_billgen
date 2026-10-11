package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;

@Controller
@RequestMapping("/portal")
public class CustomerPortalController {
    private final InvoiceService invoices; private final CustomerService customers;
    public CustomerPortalController(InvoiceService invoices,CustomerService customers){this.invoices=invoices;this.customers=customers;}
    @GetMapping public String home(@AuthenticationPrincipal AppUserPrincipal principal,Model model){
        if(principal.getCustomerId()==null){model.addAttribute("customer",null);model.addAttribute("invoices",java.util.List.of());return "portal/home";}
        model.addAttribute("customer",customers.findById(principal.getCustomerId()));
        model.addAttribute("invoices",invoices.findForCustomer(principal.getCustomerId()));return "portal/home";
    }
    @GetMapping("/invoices/{id}") public String invoice(@PathVariable long id,@AuthenticationPrincipal AppUserPrincipal principal,Model model){
        var invoice=ownedInvoice(id,principal); model.addAttribute("invoice",invoice);return "portal/invoice";
    }
    @GetMapping("/invoices/{id}/print") public String print(@PathVariable long id,@AuthenticationPrincipal AppUserPrincipal principal,Model model){
        model.addAttribute("invoice",ownedInvoice(id,principal));return "invoices/print";
    }
    private com.ritpl.billgen.entity.Invoice ownedInvoice(long id,AppUserPrincipal principal){
        if(principal.getCustomerId()==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        var invoice=invoices.findById(id);
        if(!invoice.getCustomer().getId().equals(principal.getCustomerId()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return invoice;
    }
}

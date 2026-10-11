package com.ritpl.billgen.web;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.web.form.InvoiceForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {
    private final InvoiceService invoices; private final CustomerService customers; private final CatalogService catalog;
    public InvoiceController(InvoiceService invoices,CustomerService customers,CatalogService catalog){this.invoices=invoices;this.customers=customers;this.catalog=catalog;}

    @GetMapping public String list(Model model){model.addAttribute("invoices",invoices.findAll());model.addAttribute("stats",invoices.dashboardStats());return "invoices/list";}
    @GetMapping("/new") public String createForm(Model model){
        model.addAttribute("invoiceForm",new InvoiceForm()); prepareForm(model); return "invoices/form";
    }
    @PostMapping("/new") public String create(@Valid @ModelAttribute("invoiceForm") InvoiceForm form,BindingResult errors,Model model,RedirectAttributes redirect){
        if(errors.hasErrors()){prepareForm(model);return "invoices/form";}
        try {var invoice=invoices.create(form.toCommand());redirect.addFlashAttribute("successMessage","Invoice created.");return "redirect:/invoices/"+invoice.getId();}
        catch(IllegalArgumentException e){errors.reject("invoice.invalid",e.getMessage());prepareForm(model);return "invoices/form";}
    }
    @GetMapping("/{id}") public String detail(@PathVariable long id,Model model){model.addAttribute("invoice",invoices.findById(id));return "invoices/detail";}
    @GetMapping("/{id}/print") public String print(@PathVariable long id,Model model){model.addAttribute("invoice",invoices.findById(id));return "invoices/print";}
    @PostMapping("/{id}/send") public String send(@PathVariable long id,RedirectAttributes redirect){
        try{invoices.markSent(id);redirect.addFlashAttribute("successMessage","Invoice marked as sent.");}
        catch(IllegalArgumentException e){redirect.addFlashAttribute("errorMessage",e.getMessage());}
        return "redirect:/invoices/"+id;
    }
    @PostMapping("/{id}/paid") public String paid(@PathVariable long id,RedirectAttributes redirect){
        try{invoices.markPaid(id);redirect.addFlashAttribute("successMessage","Payment recorded.");}
        catch(IllegalArgumentException e){redirect.addFlashAttribute("errorMessage",e.getMessage());}
        return "redirect:/invoices/"+id;
    }
    private void prepareForm(Model model){
        model.addAttribute("customers",customers.findAll(null));model.addAttribute("products",catalog.findActive());
        model.addAttribute("templates",List.of("CLASSIC","MODERN","MINIMAL"));
        model.addAttribute("statuses",List.of(InvoiceStatus.DRAFT,InvoiceStatus.SENT));
    }
}

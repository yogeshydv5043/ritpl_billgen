package com.ritpl.billgen.web;

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
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.web.form.CustomerForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService customers; private final InvoiceService invoices;
    public CustomerController(CustomerService customers, InvoiceService invoices){this.customers=customers;this.invoices=invoices;}

    @GetMapping public String list(@RequestParam(required=false) String q, Model model){
        model.addAttribute("customers",customers.findAll(q)); model.addAttribute("q",q); return "customers/list";
    }
    @GetMapping("/new") public String createForm(Model model){
        model.addAttribute("customerForm",new CustomerForm()); model.addAttribute("formTitle","Add customer"); model.addAttribute("formAction","/customers"); return "customers/form";
    }
    @PostMapping public String create(@Valid @ModelAttribute("customerForm") CustomerForm form, BindingResult errors, Model model, RedirectAttributes redirect){
        if(errors.hasErrors()){model.addAttribute("formTitle","Add customer");model.addAttribute("formAction","/customers");return "customers/form";}
        try { var customer=customers.save(form.toEntity()); redirect.addFlashAttribute("successMessage","Customer added."); return "redirect:/customers/"+customer.getId(); }
        catch(IllegalArgumentException e){errors.reject("customer.duplicate",e.getMessage());model.addAttribute("formTitle","Add customer");model.addAttribute("formAction","/customers");return "customers/form";}
    }
    @GetMapping("/{id}") public String detail(@PathVariable long id, Model model){
        var customer=customers.findById(id); model.addAttribute("customer",customer); model.addAttribute("invoices",invoices.findForCustomer(id)); return "customers/detail";
    }
    @GetMapping("/{id}/edit") public String editForm(@PathVariable long id, Model model){
        model.addAttribute("customerForm",CustomerForm.from(customers.findById(id)));model.addAttribute("formTitle","Edit customer");model.addAttribute("formAction","/customers/"+id);return "customers/form";
    }
    @PostMapping("/{id}") public String update(@PathVariable long id,@Valid @ModelAttribute("customerForm") CustomerForm form,BindingResult errors,Model model,RedirectAttributes redirect){
        if(errors.hasErrors()){model.addAttribute("formTitle","Edit customer");model.addAttribute("formAction","/customers/"+id);return "customers/form";}
        try {customers.update(id,form.toEntity());redirect.addFlashAttribute("successMessage","Customer details updated.");return "redirect:/customers/"+id;}
        catch(IllegalArgumentException e){errors.reject("customer.duplicate",e.getMessage());model.addAttribute("formTitle","Edit customer");model.addAttribute("formAction","/customers/"+id);return "customers/form";}
    }
}

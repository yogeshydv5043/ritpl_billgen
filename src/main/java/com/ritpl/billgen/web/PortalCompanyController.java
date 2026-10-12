package com.ritpl.billgen.web;

import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.web.form.CompanyProfileForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/portal/company")
public class PortalCompanyController {
    private final CustomerService customers;
    public PortalCompanyController(CustomerService customers) { this.customers = customers; }

    @GetMapping
    public String form(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        long id = scope.requireCustomerId();
        if (!model.containsAttribute("companyProfileForm")) model.addAttribute("companyProfileForm", CompanyProfileForm.from(customers.findById(scope, id)));
        model.addAttribute("customer", customers.findById(scope, id));
        return "portal/company-form";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("companyProfileForm") CompanyProfileForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        if (errors.hasErrors()) {
            model.addAttribute("customer", customers.findById(scope, scope.requireCustomerId()));
            return "portal/company-form";
        }
        try {
            byte[] data = form.getLogo() == null || form.getLogo().isEmpty() ? null : form.getLogo().getBytes();
            String type = form.getLogo() == null || form.getLogo().isEmpty() ? null : form.getLogo().getContentType();
            customers.updateCompanyProfile(scope, form.toCommand(data, type));
            redirect.addFlashAttribute("successMessage", "Company profile updated. New invoices will use the updated identity.");
            return "redirect:/portal/company";
        } catch (IOException | IllegalArgumentException e) {
            errors.reject("company.invalid", e instanceof IOException ? "The logo upload could not be read." : e.getMessage());
            model.addAttribute("customer", customers.findById(scope, scope.requireCustomerId()));
            return "portal/company-form";
        }
    }
}

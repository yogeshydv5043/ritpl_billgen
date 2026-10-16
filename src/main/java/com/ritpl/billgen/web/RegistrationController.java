package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.web.form.RegistrationForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/register")
public class RegistrationController {
    private final CustomerService customers;
    public RegistrationController(CustomerService customers) { this.customers = customers; }

    @GetMapping
    public String form(Model model) {
        if (!model.containsAttribute("registrationForm")) model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form, BindingResult errors,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) return "register";
        try {
            customers.register(form.toCommand());
            redirect.addFlashAttribute("successMessage", "Your account is ready. Sign in, then choose a plan to activate immediately.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            errors.reject("registration.invalid", e.getMessage());
            return "register";
        }
    }
}

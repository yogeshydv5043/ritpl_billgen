package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.service.TemplateService;
import com.ritpl.billgen.web.form.TemplateForm;
import jakarta.validation.Valid;

/** Administrator management of the bill/invoice templates that plans are built from. */
@Controller
@RequestMapping("/admin/templates")
public class AdminTemplateController {
    private final TemplateService templates;

    public AdminTemplateController(TemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("templates", templates.findAll());
        return "admin/templates";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("templateForm", new TemplateForm());
        return form(model, "Add bill template", "/admin/templates");
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("templateForm") TemplateForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Add bill template", "/admin/templates");
        try {
            templates.save(null, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Template created. Add it to a plan to unlock it for customers.");
            return "redirect:/admin/templates";
        } catch (IllegalArgumentException e) {
            errors.reject("template.invalid", e.getMessage());
            return form(model, "Add bill template", "/admin/templates");
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("templateForm", TemplateForm.from(templates.findById(id)));
        return form(model, "Edit bill template", "/admin/templates/" + id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("templateForm") TemplateForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Edit bill template", "/admin/templates/" + id);
        try {
            templates.save(id, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Template updated.");
            return "redirect:/admin/templates";
        } catch (IllegalArgumentException e) {
            errors.reject("template.invalid", e.getMessage());
            return form(model, "Edit bill template", "/admin/templates/" + id);
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, RedirectAttributes redirect) {
        var template = templates.findById(id);
        templates.setActive(id, !template.isActive());
        redirect.addFlashAttribute("successMessage", template.isActive()
                ? "Template is available again." : "Template hidden from customers.");
        return "redirect:/admin/templates";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            templates.delete(id);
            redirect.addFlashAttribute("successMessage", "Template deleted.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/templates";
    }

    private String form(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("styles", BillTemplateStyle.values());
        model.addAttribute("accentColors", TemplateService.presetAccentColors());
        return "admin/template-form";
    }
}
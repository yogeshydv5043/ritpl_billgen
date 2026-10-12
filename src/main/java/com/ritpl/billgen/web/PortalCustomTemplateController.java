package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CustomTemplateService;
import com.ritpl.billgen.web.form.CustomTemplateForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/portal/templates")
public class PortalCustomTemplateController {
    private final CustomTemplateService templates;
    public PortalCustomTemplateController(CustomTemplateService templates) { this.templates = templates; }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        model.addAttribute("templates", templates.findAll(scope));
        if (!model.containsAttribute("templateForm")) model.addAttribute("templateForm", new CustomTemplateForm());
        return "portal/templates";
    }
    @GetMapping("/new")
    public String createForm(Model model) { model.addAttribute("templateForm", new CustomTemplateForm()); return form(model, "Create custom template", "/portal/templates"); }
    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal, @Valid @ModelAttribute("templateForm") CustomTemplateForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Create custom template", "/portal/templates");
        try { templates.save(TenantScope.from(principal), null, form.toCommand()); redirect.addFlashAttribute("successMessage", "Custom invoice template created."); return "redirect:/portal/templates"; }
        catch (IllegalArgumentException e) { errors.reject("template.invalid", e.getMessage()); return form(model, "Create custom template", "/portal/templates"); }
    }
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("templateForm", CustomTemplateForm.from(templates.findById(TenantScope.from(principal), id)));
        return form(model, "Edit custom template", "/portal/templates/" + id);
    }
    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("templateForm") CustomTemplateForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Edit custom template", "/portal/templates/" + id);
        try { templates.save(TenantScope.from(principal), id, form.toCommand()); redirect.addFlashAttribute("successMessage", "Custom invoice template updated."); return "redirect:/portal/templates"; }
        catch (IllegalArgumentException e) { errors.reject("template.invalid", e.getMessage()); return form(model, "Edit custom template", "/portal/templates/" + id); }
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try { templates.delete(TenantScope.from(principal), id); redirect.addFlashAttribute("successMessage", "Custom template deleted or archived."); }
        catch (IllegalArgumentException e) { redirect.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/portal/templates";
    }
    private String form(Model model, String title, String action) {
        model.addAttribute("formTitle", title); model.addAttribute("formAction", action); model.addAttribute("styles", BillTemplateStyle.values());
        return "portal/template-form";
    }
}

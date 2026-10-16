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
        var live = templates.findLive();
        java.util.Map<Long, Long> planCounts = new java.util.HashMap<>();
        live.forEach(template -> planCounts.put(template.getId(), templates.assignedPlanCount(template.getId())));
        model.addAttribute("templates", live);
        model.addAttribute("planCounts", planCounts);
        model.addAttribute("activeCount", templates.activeCount());
        return "admin/templates";
    }

    /**
     * Live preview of the layout with sample data. The same route serves the editor's iframe: every
     * form field is echoed back as a query parameter, so unsaved changes preview without touching
     * the stored template. Only a GET, never a write.
     */
    @GetMapping("/{id}/preview")
    public String preview(@PathVariable long id, @RequestParam(required = false) String company,
            @RequestParam java.util.Map<String, String> params, Model model) {
        var template = templates.findById(id);
        model.addAttribute("template", template);
        model.addAttribute("previewHtml", templates.previewHtml(draftCommand(params, template), company));
        model.addAttribute("assignedPlans", templates.assignedPlanCount(id));
        model.addAttribute("usage", template.getVersion());
        return "admin/template-preview";
    }

    /** Preview of a template that does not exist yet, used by the create form. */
    @GetMapping("/preview-draft")
    public String previewDraft(@RequestParam(required = false) String company,
            @RequestParam java.util.Map<String, String> params, Model model) {
        model.addAttribute("draft", Boolean.TRUE);
        model.addAttribute("previewHtml", templates.previewHtml(draftCommand(params, null), company));
        return "admin/template-preview";
    }

    /** Stored row with any preview overrides applied; blank parameters fall back to the saved value. */
    private static com.ritpl.billgen.service.TemplateCommand draftCommand(java.util.Map<String, String> params,
            com.ritpl.billgen.entity.BillTemplate base) {
        java.util.function.Function<String, String> pick = key -> {
            String value = params.get(key);
            return value == null || value.isBlank() ? null : value;
        };
        com.ritpl.billgen.entity.BillTemplateStyle style;
        try {
            style = com.ritpl.billgen.entity.BillTemplateStyle.valueOf(
                    orElse(pick.apply("style"), base == null ? "CLASSIC" : base.getStyleCode()));
        } catch (IllegalArgumentException e) {
            style = com.ritpl.billgen.entity.BillTemplateStyle.CLASSIC;
        }
        return new com.ritpl.billgen.service.TemplateCommand(
                orElse(pick.apply("code"), base == null ? "DRAFT" : base.getCode()),
                orElse(pick.apply("name"), base == null ? "Untitled template" : base.getName()),
                orElse(pick.apply("description"), base == null ? null : base.getDescription()),
                style,
                orElse(pick.apply("accentColor"), base == null ? null : base.getAccentColor()),
                true,
                intOr(orElse(pick.apply("sortOrder"), base == null ? "10" : String.valueOf(base.getSortOrder())), 10),
                orElse(pick.apply("headerText"), base == null ? null : base.getHeaderText()),
                orElse(pick.apply("footerText"), base == null ? null : base.getFooterText()),
                orElse(pick.apply("paymentTerms"), base == null ? null : base.getPaymentTerms()),
                orElse(pick.apply("termsAndConditions"), base == null ? null : base.getTermsAndConditions()));
    }

    private static String orElse(String value, String fallback) { return value != null ? value : fallback; }

    private static int intOr(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("templateForm", new TemplateForm());
        return form(model, "Add bill template", "/admin/templates", null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("templateForm") TemplateForm form, BindingResult errors, Model model,
            RedirectAttributes redirect, @org.springframework.security.core.annotation.AuthenticationPrincipal com.ritpl.billgen.security.AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Add bill template", "/admin/templates", null);
        try {
            templates.save(null, form.toCommand(), (principal == null ? "admin" : principal.getUsername()));
            redirect.addFlashAttribute("successMessage", "Template created. Add it to a plan to unlock it for customers.");
            return "redirect:/admin/templates";
        } catch (IllegalArgumentException e) {
            errors.reject("template.invalid", e.getMessage());
            return form(model, "Add bill template", "/admin/templates", null);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var template = templates.findById(id);
        model.addAttribute("templateForm", TemplateForm.from(template));
        model.addAttribute("templateVersion", template.getVersion());
        model.addAttribute("assignedPlans", templates.assignedPlanCount(id));
        return form(model, "Edit bill template", "/admin/templates/" + id, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("templateForm") TemplateForm form, BindingResult errors,
            Model model, RedirectAttributes redirect, @org.springframework.security.core.annotation.AuthenticationPrincipal com.ritpl.billgen.security.AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Edit bill template", "/admin/templates/" + id, id);
        try {
            templates.save(id, form.toCommand(), (principal == null ? "admin" : principal.getUsername()));
            redirect.addFlashAttribute("successMessage", "Template updated.");
            return "redirect:/admin/templates";
        } catch (IllegalArgumentException e) {
            errors.reject("template.invalid", e.getMessage());
            return form(model, "Edit bill template", "/admin/templates/" + id, id);
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

    private String form(Model model, String title, String action, Long entityId) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        // The iframe previews the saved row for an edit and the unsaved draft for a create.
        model.addAttribute("entityId", entityId);
        model.addAttribute("previewUrl", entityId == null ? "/admin/templates/preview-draft"
                : "/admin/templates/" + entityId + "/preview");
        model.addAttribute("styles", BillTemplateStyle.values());
        model.addAttribute("accentColors", TemplateService.presetAccentColors());
        return "admin/template-form";
    }
}
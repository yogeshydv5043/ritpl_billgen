package com.ritpl.billgen.web;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.ritpl.billgen.entity.AuditEventCategory;
import com.ritpl.billgen.entity.AuditType;
import com.ritpl.billgen.entity.LimitPeriod;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AuditFilter;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.AuditTypeService;
import com.ritpl.billgen.web.form.AuditTypeForm;
import jakarta.validation.Valid;

/**
 * Module 5, Audit Management: what is tracked, its quota and its retention. The history itself is
 * append-only and lives behind the audit trail screen; nothing here can rewrite it.
 */
@Controller
@RequestMapping("/admin/audit-types")
public class AdminAuditTypeController {
    private final AuditTypeService auditTypes;
    private final AuditService audit;

    public AdminAuditTypeController(AuditTypeService auditTypes, AuditService audit) {
        this.auditTypes = auditTypes; this.audit = audit;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String category, @RequestParam(required = false) String state,
            Model model) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>(auditTypes.overview());
        String wantedCategory = category == null || category.isBlank() ? null : category.trim().toUpperCase(java.util.Locale.ROOT);
        rows.removeIf(row -> {
            AuditType type = (AuditType) row.get("type");
            if (wantedCategory != null && !type.getEventCategory().name().equals(wantedCategory)) return true;
            if ("active".equalsIgnoreCase(state) && !type.isActive()) return true;
            return "paused".equalsIgnoreCase(state) && type.isActive();
        });
        model.addAttribute("filterCategory", category);
        model.addAttribute("filterState", state);
        model.addAttribute("rows", rows);
        model.addAttribute("categories", List.of(AuditEventCategory.values()));
        model.addAttribute("periods", List.of(LimitPeriod.values()));
        model.addAttribute("activeCount", auditTypes.activeCount());
        return "admin/audit/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("auditTypeForm", new AuditTypeForm());
        return form(model, "Create audit type", "/admin/audit-types", null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("auditTypeForm") AuditTypeForm form, BindingResult errors, Model model,
            RedirectAttributes redirect, @AuthenticationPrincipal AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Create audit type", "/admin/audit-types", null);
        try {
            auditTypes.save(TenantScope.administrator(), null, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Audit type created.");
            return "redirect:/admin/audit-types";
        } catch (IllegalArgumentException e) {
            errors.reject("auditType.invalid", e.getMessage());
            return form(model, "Create audit type", "/admin/audit-types", null);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        AuditType type = auditTypes.findById(id);
        model.addAttribute("auditTypeForm", AuditTypeForm.from(type));
        return form(model, "Edit audit type", "/admin/audit-types/" + id, type);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("auditTypeForm") AuditTypeForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, "Edit audit type", "/admin/audit-types/" + id, auditTypes.findById(id));
        try {
            auditTypes.save(TenantScope.administrator(), id, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Audit type updated. Existing history keeps its own retention.");
            return "redirect:/admin/audit-types";
        } catch (IllegalArgumentException e) {
            errors.reject("auditType.invalid", e.getMessage());
            return form(model, "Edit audit type", "/admin/audit-types/" + id, auditTypes.findById(id));
        }
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable long id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            auditTypes.setActive(TenantScope.administrator(), id, active);
            redirect.addFlashAttribute("successMessage", active ? "Activity is now tracked." : "Activity tracking paused.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/audit-types";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        try {
            auditTypes.delete(TenantScope.administrator(), id, principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Audit type retired. Recorded history was preserved.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/audit-types";
    }

    /** Quota usage and retention for one definition, including its most recent events. */
    @GetMapping("/{id}/usage")
    public String usage(@PathVariable long id, @RequestParam(required = false) Long customer, Model model) {
        AuditType type = auditTypes.findById(id);
        model.addAttribute("auditType", type);
        model.addAttribute("usage", auditTypes.usage(type, customer));
        model.addAttribute("records", audit.search(TenantScope.administrator(),
                new AuditFilter(customer, null, type.getAction(), type.getResourceType(), null, null, null),
                PageRequest.of(0, 25)));
        return "admin/audit/usage";
    }

    private String form(Model model, String title, String action, AuditType type) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("auditType", type);
        model.addAttribute("categories", List.of(AuditEventCategory.values()));
        model.addAttribute("periods", List.of(LimitPeriod.values()));
        return "admin/audit/form";
    }
}

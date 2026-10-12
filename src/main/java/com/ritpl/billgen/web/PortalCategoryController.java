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
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.web.form.CategoryForm;
import jakarta.validation.Valid;

/** A customer managing its own product categories. */
@Controller
@RequestMapping("/portal/categories")
public class PortalCategoryController {
    private final ProductCategoryService categories;
    private final CatalogService catalog;

    public PortalCategoryController(ProductCategoryService categories, CatalogService catalog) {
        this.categories = categories; this.catalog = catalog;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        var categoryList = categories.findAll(scope);
        var productList = catalog.findAll(scope);
        java.util.Map<Long, Long> productCounts = new java.util.HashMap<>();
        for (var product : productList) {
            if (product.getCategory() != null) productCounts.merge(product.getCategory().getId(), 1L, Long::sum);
        }
        model.addAttribute("categories", categoryList);
        model.addAttribute("products", productList);
        model.addAttribute("productCounts", productCounts);
        if (!model.containsAttribute("categoryForm")) model.addAttribute("categoryForm", new CategoryForm());
        return "portal/categories";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        if (errors.hasErrors()) return list(principal, model);
        try {
            categories.save(scope, null, form.getName(), form.getDescription());
            redirect.addFlashAttribute("successMessage", "Category added.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/categories";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        if (errors.hasErrors()) return list(principal, model);
        try {
            categories.save(scope, id, form.getName(), form.getDescription());
            redirect.addFlashAttribute("successMessage", "Category updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            categories.delete(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage", "Category deleted.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/categories";
    }
}
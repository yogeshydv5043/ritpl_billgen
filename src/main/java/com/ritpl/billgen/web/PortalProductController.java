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
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.web.form.ProductForm;
import jakarta.validation.Valid;

/**
 * A customer managing its own catalogue. Every read and write is scoped to the signed-in account;
 * an id belonging to another account is reported as "not found".
 */
@Controller
@RequestMapping("/portal/products")
public class PortalProductController {
    private final CatalogService catalog;
    private final ProductCategoryService categories;

    public PortalProductController(CatalogService catalog, ProductCategoryService categories) {
        this.catalog = catalog; this.categories = categories;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        model.addAttribute("products", catalog.findAll(scope));
        model.addAttribute("categories", categories.findAll(scope));
        return "portal/products";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("productForm", new ProductForm());
        return form(TenantScope.from(principal), model, "Add catalogue item", "/portal/products");
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("productForm") ProductForm productForm, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        if (errors.hasErrors()) return form(scope, model, "Add catalogue item", "/portal/products");
        try {
            catalog.save(scope, null, productForm.toCommand());
            redirect.addFlashAttribute("successMessage", "Catalogue item added.");
            return "redirect:/portal/products";
        } catch (IllegalArgumentException e) {
            errors.reject("product.invalid", e.getMessage());
            return form(scope, model, "Add catalogue item", "/portal/products");
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        model.addAttribute("productForm", ProductForm.from(catalog.findById(scope, id)));
        return form(scope, model, "Edit catalogue item", "/portal/products/" + id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("productForm") ProductForm productForm, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        if (errors.hasErrors()) return form(scope, model, "Edit catalogue item", "/portal/products/" + id);
        try {
            catalog.save(scope, id, productForm.toCommand());
            redirect.addFlashAttribute("successMessage", "Catalogue item updated.");
            return "redirect:/portal/products";
        } catch (IllegalArgumentException e) {
            errors.reject("product.invalid", e.getMessage());
            return form(scope, model, "Edit catalogue item", "/portal/products/" + id);
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        try {
            var product = catalog.findById(scope, id);
            catalog.setActive(scope, id, !product.isActive());
            redirect.addFlashAttribute("successMessage", "Catalogue item updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            catalog.delete(TenantScope.from(principal), id);
            redirect.addFlashAttribute("successMessage", "Catalogue item deleted.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/products";
    }

    private String form(TenantScope scope, Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("productTypes", ProductType.values());
        model.addAttribute("categories", categories.findAll(scope));
        return "portal/product-form";
    }
}
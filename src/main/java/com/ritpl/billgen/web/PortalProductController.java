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
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;
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
    /** Whitelisted sort keys, so a link can never name an arbitrary entity property. */
    private static final Map<String, String> SORTS = Map.of(
            "name", "name", "sku", "sku", "price", "unitPrice", "tax", "taxRate",
            "status", "active", "updated", "updatedAt", "created", "createdAt");

    private final CatalogService catalog;
    private final ProductCategoryService categories;
    private final com.ritpl.billgen.service.CustomerSettingsService settings;

    public PortalProductController(CatalogService catalog, ProductCategoryService categories,
            com.ritpl.billgen.service.CustomerSettingsService settings) {
        this.catalog = catalog; this.categories = categories; this.settings = settings;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir) {
        TenantScope scope = TenantScope.from(principal);
        long customerId = scope.requireCustomerId();
        com.ritpl.billgen.entity.ProductType productType = null;
        if (type != null && !type.isBlank()) {
            try { productType = com.ritpl.billgen.entity.ProductType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { productType = null; }
        }
        Boolean active = "active".equals(status) ? Boolean.TRUE : "hidden".equals(status) ? Boolean.FALSE : null;
        var filter = new com.ritpl.billgen.repository.ProductFilter(q, category, active, productType);
        org.springframework.data.domain.Sort.Direction direction =
                "desc".equalsIgnoreCase(dir) ? org.springframework.data.domain.Sort.Direction.DESC
                        : org.springframework.data.domain.Sort.Direction.ASC;
        String property = SORTS.getOrDefault(sort, "name");
        var listing = catalog.page(scope, filter, org.springframework.data.domain.PageRequest.of(Math.max(0, page),
                settings.pageSize(customerId), org.springframework.data.domain.Sort.by(direction, property)));
        model.addAttribute("page", listing);
        model.addAttribute("products", listing.getContent());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("sort", SORTS.containsKey(sort) ? sort : "name");
        model.addAttribute("dir", direction == org.springframework.data.domain.Sort.Direction.ASC ? "asc" : "desc");
        model.addAttribute("activeFilters", filter.hasAnyFilter());
        model.addAttribute("categories", categories.findAll(scope));
        model.addAttribute("counts", catalog.counts(scope));
        return "portal/products";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        TenantScope scope = TenantScope.from(principal);
        ProductForm form = new ProductForm();
        var preferences = settings.get(scope, scope.requireCustomerId());
        form.setUnit(preferences.getDefaultUnit());
        form.setCategoryId(preferences.getDefaultCategoryId());
        model.addAttribute("productForm", form);
        return form(scope, model, "Add catalogue item", "/portal/products");
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
            redirect.addFlashAttribute("successMessage", product.isActive()
                    ? "Hidden from new invoices. It stays on the invoices that already used it."
                    : "Available again on new invoices.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            catalog.delete(TenantScope.from(principal), id);
            // Deleting is safe here because an invoice never points at a live product row: the line keeps
            // the description, price and tax it was issued with, so no delivered document changes.
            redirect.addFlashAttribute("successMessage",
                    "Catalogue item deleted. Invoices already issued keep its name, price and tax as they were on the day you sent them.");
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
        // The amount field shows the account's own currency code rather than an assumed symbol.
        model.addAttribute("currencyCode", settings.get(scope, scope.requireCustomerId()).getDefaultCurrency());
        return "portal/product-form";
    }
}
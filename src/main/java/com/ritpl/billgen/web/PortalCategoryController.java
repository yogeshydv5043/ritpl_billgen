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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Map;
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
    private static final Map<String, String> SORTS = Map.of(
            "name", "name", "created", "createdAt", "updated", "updatedAt");

    private final ProductCategoryService categories;
    private final CatalogService catalog;
    private final com.ritpl.billgen.service.CustomerSettingsService settings;

    public PortalCategoryController(ProductCategoryService categories, CatalogService catalog,
            com.ritpl.billgen.service.CustomerSettingsService settings) {
        this.categories = categories; this.catalog = catalog; this.settings = settings;
    }

    /** Search, sort and paging for the account's own categories; the counts come from the database. */
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir) {
        TenantScope scope = TenantScope.from(principal);
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = SORTS.getOrDefault(sort, "name");
        var listing = categories.page(scope, q, PageRequest.of(Math.max(0, page),
                settings.pageSize(scope.requireCustomerId()), Sort.by(direction, property)));
        model.addAttribute("page", listing);
        model.addAttribute("categories", listing.getContent());
        model.addAttribute("q", q);
        model.addAttribute("sort", SORTS.containsKey(sort) ? sort : "name");
        model.addAttribute("dir", direction == Sort.Direction.ASC ? "asc" : "desc");
        model.addAttribute("activeFilters", q != null && !q.isBlank());
        // Counted in the database: summing the page would report a partial figure for a full category.
        model.addAttribute("productCounts", categories.productCounts(scope));
        model.addAttribute("totalCategories", categories.countAll(scope));
        if (!model.containsAttribute("categoryForm")) model.addAttribute("categoryForm", new CategoryForm());
        return "portal/categories";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        if (errors.hasErrors()) return list(principal, model, null, 0, "name", "asc");  // the form keeps its invalid input; the search box stays empty
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
        if (errors.hasErrors()) return list(principal, model, null, 0, "name", "asc");  // the form keeps its invalid input; the search box stays empty
        try {
            categories.save(scope, id, form.getName(), form.getDescription());
            redirect.addFlashAttribute("successMessage", "Category updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/categories";
    }

    /** Hiding a category keeps the products inside it; it only stops new items being filed there. */
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        TenantScope scope = TenantScope.from(principal);
        try {
            var category = categories.findById(scope, id);
            categories.setActive(scope, id, !category.isActive());
            redirect.addFlashAttribute("successMessage", category.isActive()
                    ? "Category hidden from new catalogue items." : "Category available again.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        try {
            var scope = TenantScope.from(principal);
            Long used = categories.productCounts(scope).get(id);
            categories.delete(scope, id);
            // Products are never destroyed with their category: the service unassigns them, which is why
            // a category that still holds items can be deleted without a cascade.
            redirect.addFlashAttribute("successMessage", used == null || used == 0
                    ? "Category deleted."
                    : "Category deleted. " + used + " product(s) stayed in your catalogue and are now uncategorised.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/portal/categories";
    }
}
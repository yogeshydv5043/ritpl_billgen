package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CatalogService;

/**
 * Administrator view of the catalogues of all customer accounts. Read-only across accounts; each
 * customer manages its own products in its portal.
 */
@Controller
@RequestMapping("/products")
public class ProductController {
    private final CatalogService catalog;

    public ProductController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("products", catalog.findAll(TenantScope.from(principal)));
        return "products/list";
    }
}
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
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.web.form.ProductForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/products")
public class ProductController {
    private final CatalogService catalog;
    public ProductController(CatalogService catalog){this.catalog=catalog;}
    @GetMapping public String list(Model model){model.addAttribute("products",catalog.findAll());return "products/list";}
    @GetMapping("/new") public String createForm(Model model){model.addAttribute("productForm",new ProductForm());model.addAttribute("formTitle","Add catalogue item");model.addAttribute("formAction","/products");prepare(model);return "products/form";}
    @PostMapping public String create(@Valid @ModelAttribute("productForm") ProductForm form,BindingResult errors,Model model,RedirectAttributes redirect){
        if(errors.hasErrors()){model.addAttribute("formTitle","Add catalogue item");model.addAttribute("formAction","/products");prepare(model);return "products/form";}
        try{catalog.save(null,form.toCommand());redirect.addFlashAttribute("successMessage","Catalogue item added.");return "redirect:/products";}
        catch(IllegalArgumentException e){errors.reject("product.invalid",e.getMessage());model.addAttribute("formTitle","Add catalogue item");model.addAttribute("formAction","/products");prepare(model);return "products/form";}
    }
    @GetMapping("/{id}/edit") public String editForm(@PathVariable long id,Model model){model.addAttribute("productForm",ProductForm.from(catalog.findById(id)));model.addAttribute("formTitle","Edit catalogue item");model.addAttribute("formAction","/products/"+id);prepare(model);return "products/form";}
    @PostMapping("/{id}") public String update(@PathVariable long id,@Valid @ModelAttribute("productForm") ProductForm form,BindingResult errors,Model model,RedirectAttributes redirect){
        if(errors.hasErrors()){model.addAttribute("formTitle","Edit catalogue item");model.addAttribute("formAction","/products/"+id);prepare(model);return "products/form";}
        try{catalog.save(id,form.toCommand());redirect.addFlashAttribute("successMessage","Catalogue item updated.");return "redirect:/products";}
        catch(IllegalArgumentException e){errors.reject("product.invalid",e.getMessage());model.addAttribute("formTitle","Edit catalogue item");model.addAttribute("formAction","/products/"+id);prepare(model);return "products/form";}
    }
    @PostMapping("/{id}/toggle") public String toggle(@PathVariable long id,RedirectAttributes redirect){var item=catalog.findById(id);catalog.setActive(id,!item.isActive());redirect.addFlashAttribute("successMessage","Catalogue item status updated.");return "redirect:/products";}
    private void prepare(Model model){model.addAttribute("productTypes",ProductType.values());}
}

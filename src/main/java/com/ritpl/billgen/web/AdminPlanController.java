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
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.web.form.PlanForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/plans")
public class AdminPlanController {
    private final PlanService plans;
    public AdminPlanController(PlanService plans){this.plans=plans;}
    @GetMapping public String list(Model model){model.addAttribute("plans",plans.findAll());return "plans/admin-list";}
    @GetMapping("/new") public String createForm(Model model){model.addAttribute("planForm",new PlanForm());model.addAttribute("formTitle","Add subscription plan");model.addAttribute("formAction","/admin/plans");return "plans/form";}
    @PostMapping public String create(@Valid @ModelAttribute("planForm") PlanForm form,BindingResult errors,Model model,RedirectAttributes redirect){
        if(errors.hasErrors()){model.addAttribute("formTitle","Add subscription plan");model.addAttribute("formAction","/admin/plans");return "plans/form";}
        try{plans.save(null,form.toCommand());redirect.addFlashAttribute("successMessage","Plan created.");return "redirect:/admin/plans";}
        catch(IllegalArgumentException e){errors.reject("plan.invalid",e.getMessage());model.addAttribute("formTitle","Add subscription plan");model.addAttribute("formAction","/admin/plans");return "plans/form";}
    }
    @GetMapping("/{id}/edit") public String editForm(@PathVariable long id,Model model){model.addAttribute("planForm",PlanForm.from(plans.findById(id)));model.addAttribute("formTitle","Edit subscription plan");model.addAttribute("formAction","/admin/plans/"+id);return "plans/form";}
    @PostMapping("/{id}") public String update(@PathVariable long id,@Valid @ModelAttribute("planForm") PlanForm form,BindingResult errors,Model model,RedirectAttributes redirect){
        if(errors.hasErrors()){model.addAttribute("formTitle","Edit subscription plan");model.addAttribute("formAction","/admin/plans/"+id);return "plans/form";}
        try{plans.save(id,form.toCommand());redirect.addFlashAttribute("successMessage","Plan updated.");return "redirect:/admin/plans";}
        catch(IllegalArgumentException e){errors.reject("plan.invalid",e.getMessage());model.addAttribute("formTitle","Edit subscription plan");model.addAttribute("formAction","/admin/plans/"+id);return "plans/form";}
    }
    @PostMapping("/{id}/toggle") public String toggle(@PathVariable long id,RedirectAttributes redirect){var plan=plans.findById(id);plans.setActive(id,!plan.isActive());redirect.addFlashAttribute("successMessage","Plan availability updated.");return "redirect:/admin/plans";}
}

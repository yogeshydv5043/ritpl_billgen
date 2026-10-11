package com.ritpl.billgen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.ritpl.billgen.service.PlanService;

@Controller
public class PageController {
    private final PlanService plans;
    public PageController(PlanService plans) { this.plans=plans; }
    @GetMapping("/") public String home(){return "redirect:/dashboard";}
    @GetMapping("/login") public String login(){return "login";}
    @GetMapping("/plans") public String publicPlans(Model model){model.addAttribute("plans",plans.findActive());return "plans/public";}
}

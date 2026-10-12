package com.ritpl.billgen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.service.PlanService;

@Controller
public class PageController {
    private final PlanService plans;

    public PageController(PlanService plans) { this.plans = plans; }

    /** Sends each role to its own home: administrators to the dashboard, customers to the portal. */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal != null && !principal.isAdmin()) return "redirect:/portal";
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/plans")
    public String publicPlans(Model model) {
        model.addAttribute("plans", plans.findActive());
        return "plans/public";
    }
}
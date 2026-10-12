package com.ritpl.billgen.web;

import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AuditFilter;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.SubscriptionService;

@Controller
public class AuditTrailController {
    private final AuditService audit;
    private final SubscriptionService subscriptions;
    public AuditTrailController(AuditService audit, SubscriptionService subscriptions) { this.audit = audit; this.subscriptions = subscriptions; }

    @GetMapping("/admin/audit")
    public String admin(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(defaultValue="0") int page, Model model) {
        return listing(TenantScope.from(principal), true, customerId, actor, action, resourceType, outcome, from, until, page, model);
    }

    @GetMapping("/portal/audit")
    public String customer(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action, @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(defaultValue="0") int page, Model model) {
        return listing(TenantScope.from(principal), false, null, actor, action, resourceType, outcome, from, until, page, model);
    }

    @GetMapping("/admin/audit/{id}")
    public String adminDetail(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("auditRecord", audit.findById(TenantScope.from(principal), id)); model.addAttribute("backUrl", "/admin/audit");
        return "audit/detail";
    }

    @GetMapping("/portal/audit/{id}")
    public String customerDetail(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("auditRecord", audit.findById(TenantScope.from(principal), id)); model.addAttribute("backUrl", "/portal/audit");
        return "audit/detail";
    }

    private String listing(TenantScope scope, boolean admin, Long customerId, String actor, String action,
            String resourceType, String outcome, LocalDate from, LocalDate until, int page, Model model) {
        if (from != null && until != null && until.isBefore(from)) throw new IllegalArgumentException("End date must not be before start date.");
        var zone = subscriptions.zone();
        AuditFilter filter = new AuditFilter(customerId, actor, action, resourceType, outcome,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                until == null ? null : until.plusDays(1).atStartOfDay(zone).toInstant());
        var records = audit.search(scope, filter, PageRequest.of(Math.max(0, page), 50, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("records", records); model.addAttribute("adminView", admin);
        model.addAttribute("customerId", customerId); model.addAttribute("actor", actor); model.addAttribute("action", action);
        model.addAttribute("resourceType", resourceType); model.addAttribute("outcome", outcome); model.addAttribute("from", from); model.addAttribute("until", until);
        return "audit/list";
    }
}

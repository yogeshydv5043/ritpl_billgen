package com.ritpl.billgen.web;

import com.ritpl.billgen.entity.AuditHistoryPeriod;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.CustomerSettingsService;
import com.ritpl.billgen.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/** Read-only audit access. Customers are forced to their account; administrators can choose one. */
@Controller
public class AuditTrailController {
    private final AuditService audits;
    private final SubscriptionService subscriptions;
    private final CustomerSettingsService settings;
    public AuditTrailController(AuditService audits, SubscriptionService subscriptions, CustomerSettingsService settings) {
        this.audits = audits; this.subscriptions = subscriptions; this.settings = settings;
    }

    @GetMapping("/admin/audit")
    public String admin(@AuthenticationPrincipal AppUserPrincipal principal, Model model, HttpServletRequest request,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required=false) String outcome,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate until, @RequestParam(defaultValue="0") int page) {
        return listing(TenantScope.from(principal), model, request, page, actor, action, resourceType, outcome, from, until, customerId, true);
    }
    @GetMapping("/portal/audit")
    public String customer(@AuthenticationPrincipal AppUserPrincipal principal, Model model, HttpServletRequest request,
            @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required=false) String outcome,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate until, @RequestParam(defaultValue="0") int page) {
        return listing(TenantScope.from(principal), model, request, page, actor, action, resourceType, outcome, from, until, null, false);
    }
    @GetMapping("/admin/audit/{id}")
    public String adminDetail(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable long id, Model model) {
        return detail(TenantScope.from(principal), id, model, true);
    }
    @GetMapping("/portal/audit/{id}")
    public String customerDetail(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable long id, Model model) {
        return detail(TenantScope.from(principal), id, model, false);
    }

    private String listing(TenantScope scope, Model model, HttpServletRequest request, int page, String actor, String action,
            String resourceType, String outcome, LocalDate from, LocalDate until, Long customerId, boolean admin) {
        ZoneId zone = subscriptions.zone();
        // A customer's view is bounded by the retention period their account is authorised for; the
        // filter is clamped to that window rather than ignored, and no window is a date picker of 1500.
        LocalDate earliest = null;
        if (!admin) {
            Instant windowStart = settings.auditWindowStart(scope.requireCustomerId());
            earliest = LocalDate.ofInstant(windowStart, zone);
            if (from == null || from.isBefore(earliest)) from = earliest;
        }
        Page<com.ritpl.billgen.entity.AuditRecord> records;
        try {
            records = audits.search(scope, new AuditFilter(customerId, actor, action, resourceType, outcome,
                    from == null ? null : from.atStartOfDay(zone).toInstant(),
                    until == null ? null : until.plusDays(1).atStartOfDay(zone).toInstant()),
                    org.springframework.data.domain.PageRequest.of(Math.max(0, page), 50));
        } catch (IllegalArgumentException e) {
            // A window whose end is before its start is a user error, not an authorization failure.
            model.addAttribute("errorMessage", e.getMessage());
            records = Page.empty();
        }
        model.addAttribute("records", records); model.addAttribute("adminView", admin); model.addAttribute("customerId", customerId);
        model.addAttribute("actor", actor); model.addAttribute("action", action); model.addAttribute("resourceType", resourceType);
        model.addAttribute("outcome", outcome); model.addAttribute("from", from); model.addAttribute("until", until);
        model.addAttribute("query", WebShellAdvice.currentQuery(request));
        if (!admin) {
            AuditHistoryPeriod period = settings.get(scope, scope.requireCustomerId()).getAuditHistoryPeriod();
            model.addAttribute("auditPeriod", period);
            model.addAttribute("earliestDate", earliest);
            model.addAttribute("retentionNote", period == AuditHistoryPeriod.FIFTEEN_DAYS
                    ? "Your " + period.getDisplayName() + " viewing window is in force. Older entries stay retained under company policy and are not deleted by shortening this choice."
                    : "Your " + period.getDisplayName() + " viewing window is in force. Deleting retained records is an authorised administrative action, not a setting.");
        }
        return "audit/list";
    }

    private String detail(TenantScope scope, long id, Model model, boolean admin) {
        var record = audits.find(scope, id);
        Map<String,Object> before=Map.of(), after=Map.of();
        try { before=audits.json.readValue(record.getBeforeJson(), Map.class); after=audits.json.readValue(record.getAfterJson(), Map.class); }
        catch (Exception ignored) { /* Missing or non-JSON audit details remain empty. */ }
        model.addAttribute("record", record); model.addAttribute("before", before); model.addAttribute("after", after); model.addAttribute("adminView", admin);
        return "audit/detail";
    }
}

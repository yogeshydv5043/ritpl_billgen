package com.ritpl.billgen.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.BackgroundJobQueryService;
import com.ritpl.billgen.service.SubscriptionExpiryNotificationService;

/**
 * Supplies what every HTML page needs from the shell - the breadcrumb trail, the notification bell
 * and the signed-in identity - so no template has to repeat it and no page can forget an
 * authorization check by building its own menu.
 */
@ControllerAdvice(basePackages = "com.ritpl.billgen.web")
public class WebShellAdvice {
    /** Section labels, keyed by the first path segment. */
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("dashboard", "Dashboard"), Map.entry("customers", "Customer management"),
            Map.entry("invoices", "Invoices"), Map.entry("products", "Catalogue"),
            Map.entry("plans", "Plans"), Map.entry("portal", "My workspace"), Map.entry("register", "Create account"),
            Map.entry("admin", "Administration"));
    private static final Map<String, String> PAGES = Map.ofEntries(
            Map.entry("new", "Create"), Map.entry("edit", "Edit"), Map.entry("deleted", "Deleted accounts"),
            Map.entry("templates", "Invoice templates"), Map.entry("plans", "Subscription plans"),
            Map.entry("notifications", "Dynamic notifications"), Map.entry("jobs", "Background jobs"),
            Map.entry("audit", "Audit history"), Map.entry("audit-types", "Audit management"),
            Map.entry("profile", "Profile and settings"), Map.entry("history", "Delivery history"),
            Map.entry("company", "Company profile"), Map.entry("categories", "Categories"),
            Map.entry("products", "Products"), Map.entry("invoices", "Invoices"),
            Map.entry("account", "Profile and security"), Map.entry("settings", "Settings"));

    private final BackgroundJobQueryService jobs;
    private final SubscriptionExpiryNotificationService notifications;

    public WebShellAdvice(BackgroundJobQueryService jobs, SubscriptionExpiryNotificationService notifications) {
        this.jobs = jobs; this.notifications = notifications;
    }

    @ModelAttribute
    public void shell(Model model, Authentication authentication, jakarta.servlet.http.HttpServletRequest request) {
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("contextPath", request.getContextPath() == null ? "" : request.getContextPath());
        model.addAttribute("breadcrumbs", breadcrumbs(request.getRequestURI(), request.getContextPath()));
        model.addAttribute("section", sectionOf(request.getRequestURI(), request.getContextPath()));
        model.addAttribute("currentQuery", currentQuery(request));
        boolean admin = authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isAdmin();
        model.addAttribute("shellRole", admin ? "ADMIN" : "CUSTOMER");
        if (admin) {
            model.addAttribute("attentionCount", jobs.attentionCount());
        } else if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.getCustomerId() != null) {
            model.addAttribute("unreadCount", notifications.unreadCount(TenantScope.ofCustomer(principal.requireCustomerId())));
        }
    }

    /**
     * Every current request parameter except {@code page}, re-encoded and ready to be appended to a
     * pagination link. Templates never hand-write a query string, so a filter can neither be dropped
     * from a page link nor smuggle an ampersand into the markup.
     */
    static String currentQuery(jakarta.servlet.http.HttpServletRequest request) {
        StringBuilder query = new StringBuilder();
        request.getParameterMap().forEach((name, values) -> {
            if ("page".equals(name)) return;
            for (String value : values) {
                if (value == null || value.isEmpty()) continue;
                query.append('&').append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                        .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return query.toString();
    }

    /** The active nav section: the first path segment, with the portal home given its own key. */
    static String sectionOf(String uri, String contextPath) {
        String path = uri == null ? "/" : uri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String[] segments = path.split("/");
        List<String> parts = new ArrayList<>();
        for (String segment : segments) if (!segment.isBlank()) parts.add(segment);
        if (parts.isEmpty()) return "dashboard";
        if (parts.size() == 1 && "portal".equals(parts.get(0))) return "portal-home";
        if (parts.size() > 1 && "admin".equals(parts.get(0))) return parts.get(1);
        if (parts.size() > 1 && "portal".equals(parts.get(0))) return parts.get(1);
        return parts.get(0);
    }

    /** Derives the trail from the URL so every page gets one without repeating the structure. */
    static List<Map<String, String>> breadcrumbs(String uri, String contextPath) {
        List<Map<String, String>> trail = new ArrayList<>();
        String path = uri == null ? "/" : uri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String[] segments = path.split("/");
        StringBuilder href = new StringBuilder();
        boolean first = true;
        for (String segment : segments) {
            if (segment.isBlank()) continue;
            href.append('/').append(segment);
            String label = first ? SECTIONS.get(segment) : PAGES.get(segment);
            first = false;
            if (label == null) continue;                      // ids and unknown hops are not labelled
            trail.add(Map.of("label", label, "href", href.toString()));
        }
        if (trail.isEmpty()) {
            String label = "/portal".equals(path) || path.startsWith("/portal") ? "My workspace" : "Dashboard";
            trail.add(Map.of("label", label, "href", "/portal".equals(path) || path.startsWith("/portal") ? "/portal" : "/dashboard"));
        }
        return trail;
    }
}

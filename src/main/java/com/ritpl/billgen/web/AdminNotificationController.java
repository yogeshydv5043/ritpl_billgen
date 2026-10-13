package com.ritpl.billgen.web;

import java.util.List;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationRecipientType;
import com.ritpl.billgen.entity.NotificationStatusFilter;
import com.ritpl.billgen.entity.NotificationTriggerEvent;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.NotificationPlaceholders;
import com.ritpl.billgen.service.NotificationService;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.web.form.NotificationForm;
import jakarta.validation.Valid;

/**
 * Module 6, Dynamic Notification Management. Content, routing and activation are data, not code, so
 * a wording change is an edit - and an edit that references an unknown placeholder cannot be
 * activated.
 */
@Controller
@RequestMapping("/admin/notifications")
public class AdminNotificationController {
    private final NotificationService notifications;
    private final PlanService plans;

    public AdminNotificationController(NotificationService notifications, PlanService plans) {
        this.notifications = notifications; this.plans = plans;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("notifications", notifications.findAll());
        model.addAttribute("deliveryCounts", notifications.deliveryCounts());
        model.addAttribute("triggers", NotificationService.triggers());
        model.addAttribute("mailTransport", notifications.transportLabel());
        return "admin/notifications/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        NotificationForm form = new NotificationForm();
        model.addAttribute("notificationForm", form);
        return form(model, "Create notification", "/admin/notifications", null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("notificationForm") NotificationForm form, BindingResult errors,
            Model model, RedirectAttributes redirect, @AuthenticationPrincipal AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Create notification", "/admin/notifications", null);
        try {
            Notification saved = notifications.save(TenantScope.administrator(), null, form.toCommand());
            redirect.addFlashAttribute("successMessage", saved.isActive()
                    ? "Notification created and activated." : "Notification created. Activate it when you are ready.");
            return "redirect:/admin/notifications";
        } catch (IllegalArgumentException e) {
            errors.reject("notification.invalid", e.getMessage());
            return form(model, "Create notification", "/admin/notifications", null);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        Notification notification = notifications.findById(id);
        model.addAttribute("notificationForm", NotificationForm.from(notification));
        model.addAttribute("notification", notification);
        return form(model, "Edit notification", "/admin/notifications/" + id, notification);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("notificationForm") NotificationForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        try {
            notifications.save(TenantScope.administrator(), id, form.toCommand());
            redirect.addFlashAttribute("successMessage", "Notification updated.");
            return "redirect:/admin/notifications";
        } catch (IllegalArgumentException e) {
            errors.reject("notification.invalid", e.getMessage());
            model.addAttribute("notification", notifications.findById(id));
            return form(model, "Edit notification", "/admin/notifications/" + id, notifications.findById(id));
        }
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable long id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            notifications.setActive(TenantScope.administrator(), id, active);
            redirect.addFlashAttribute("successMessage", active ? "Notification activated." : "Notification deactivated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            notifications.delete(TenantScope.administrator(), id);
            redirect.addFlashAttribute("successMessage", "Notification deleted. Its delivery history was kept.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/notifications";
    }

    /** Delivery history, per notification or across the system, with failure detail. */
    @GetMapping("/{id}/history")
    public String history(@PathVariable long id, Model model) {
        model.addAttribute("notification", notifications.findById(id));
        // Same page type as the system-wide view, so one template serves both.
        model.addAttribute("deliveries", new org.springframework.data.domain.PageImpl<>(
                notifications.historyForNotification(id)));
        return "admin/notifications/history";
    }

    @GetMapping("/history")
    public String allHistory(@RequestParam(required = false) String trigger, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("deliveries", notifications.history(TenantScope.administrator(), null, trigger,
                statusOf(status), PageRequest.of(Math.max(0, page), 50)));
        model.addAttribute("triggers", NotificationService.triggers());
        model.addAttribute("trigger", trigger);
        model.addAttribute("deliveryStatus", status);
        model.addAttribute("statuses", java.util.List.of(com.ritpl.billgen.entity.DeliveryStatus.values()));
        return "admin/notifications/history";
    }

    private com.ritpl.billgen.entity.DeliveryStatus statusOf(String value) {
        if (value == null || value.isBlank()) return null;
        try { return com.ritpl.billgen.entity.DeliveryStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String form(Model model, String title, String action, Notification notification) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("notification", notification);
        model.addAttribute("channels", List.of(NotificationChannel.values()));
        model.addAttribute("triggers", NotificationService.triggers());
        model.addAttribute("recipientTypes", List.of(NotificationRecipientType.values()));
        model.addAttribute("statusFilters", List.of(NotificationStatusFilter.values()));
        model.addAttribute("plans", plans.findLive());
        model.addAttribute("placeholders", NotificationPlaceholders.SUGGESTED);
        model.addAttribute("allowedPlaceholders", NotificationPlaceholders.ALLOWED);
        return "admin/notifications/form";
    }
}

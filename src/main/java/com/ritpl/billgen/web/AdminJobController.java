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
import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.JobRecipientStatus;
import com.ritpl.billgen.entity.JobStatus;
import com.ritpl.billgen.entity.JobType;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.BackgroundJobQueryService;
import com.ritpl.billgen.service.JobService;
import com.ritpl.billgen.service.NotificationService;
import com.ritpl.billgen.web.form.JobForm;
import jakarta.validation.Valid;

/**
 * Module 7, Job Management. Creating a job writes rows only; sending happens in the worker. A
 * cancel or retry that could affect customers always needs an explicit confirmation.
 */
@Controller
@RequestMapping("/admin/jobs")
public class AdminJobController {
    private final com.ritpl.billgen.repository.CustomerRepository customers;
    /** Whether the durable worker is switched on in this deployment; shown so the UI never lies. */
    @org.springframework.beans.factory.annotation.Value("${app.jobs.enabled:false}")
    private boolean jobWorkerEnabled;
    private final JobService jobs;
    private final NotificationService notifications;
    private final BackgroundJobQueryService jobQuery;

    public AdminJobController(JobService jobs, NotificationService notifications, BackgroundJobQueryService jobQuery,
            com.ritpl.billgen.repository.CustomerRepository customers) {
        this.jobs = jobs; this.notifications = notifications; this.jobQuery = jobQuery; this.customers = customers;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", jobs.search(statusOf(status), q, PageRequest.of(Math.max(0, page), 15)));
        model.addAttribute("counts", jobs.monitoring());
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("statuses", List.of(JobStatus.values()));
        model.addAttribute("attention", jobQuery.attentionCount());
        model.addAttribute("jobWorkerEnabled", jobWorkerEnabled);
        return "admin/jobs/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("jobForm", new JobForm());
        return form(model, "Schedule a job");
    }

    /**
     * "Count eligible recipients" — the same resolver the create path uses, run without writing a
     * single row, so an administrator sees the size of the fan-out before committing it.
     */
    @PostMapping(params = "action=preview")
    public String previewCount(@ModelAttribute("jobForm") JobForm form, Model model, RedirectAttributes redirect) {
        if (form.getNotificationId() == null) {
            model.addAttribute("previewError", "Choose the notification content first; eligibility comes from its recipients.");
            return form(model, "Schedule a job");
        }
        model.addAttribute("preview", jobs.preview(form.toCommand()));
        return form(model, "Schedule a job");
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("jobForm") JobForm form, BindingResult errors, Model model,
            RedirectAttributes redirect, @AuthenticationPrincipal AppUserPrincipal principal) {
        if (errors.hasErrors()) return form(model, "Schedule a job");
        try {
            BackgroundJob job = jobs.create(TenantScope.administrator(), form.toCommand(), principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Job #" + job.getId() + " created for "
                    + job.getEligibleCount() + " recipient(s) across " + job.getBatchCount() + " batch(es).");
            return "redirect:/admin/jobs/" + job.getId();
        } catch (IllegalArgumentException e) {
            errors.reject("job.invalid", e.getMessage());
            return form(model, "Schedule a job");
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, @RequestParam(required = false) String recipientStatus,
            @RequestParam(defaultValue = "0") int page, Model model) {
        BackgroundJob job = jobs.findById(id);
        model.addAttribute("job", job);
        model.addAttribute("batches", jobs.batches(id));
        model.addAttribute("recipients", jobs.recipients(id, recipientStatusOf(recipientStatus),
                PageRequest.of(Math.max(0, page), 25)));
        model.addAttribute("recipientStatus", recipientStatus);
        model.addAttribute("counts", jobs.monitoring());
        model.addAttribute("mailTransport", notifications.transportLabel());
        return "admin/jobs/detail";
    }

    /** Unblocks the job for the worker. The request never sends anything itself. */
    @PostMapping("/{id}/run")
    public String run(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        return act(() -> jobs.runNow(TenantScope.administrator(), id, principal.getUsername()), redirect,
                "Job queued. The worker picks it up on its next pass.");
    }

    @PostMapping("/{id}/pause")
    public String pause(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        return act(() -> jobs.pause(TenantScope.administrator(), id, principal.getUsername()), redirect,
                "Job paused. Undelivered batches stay queued.");
    }

    @PostMapping("/{id}/resume")
    public String resume(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirect) {
        return act(() -> jobs.resume(TenantScope.administrator(), id, principal.getUsername()), redirect,
                "Job resumed.");
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable long id, @RequestParam(defaultValue = "false") boolean confirmed,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        if (!confirmed) {
            redirect.addFlashAttribute("errorMessage", "Cancelling a messaging job stops delivery to customers. "
                    + "Use the confirm dialog to continue.");
            return "redirect:/admin/jobs/" + id;
        }
        return act(() -> jobs.cancel(TenantScope.administrator(), id, true, principal.getUsername()), redirect,
                "Job cancelled.");
    }

    @PostMapping("/{id}/retry")
    public String retry(@PathVariable long id, @RequestParam(defaultValue = "false") boolean confirmed,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirect) {
        if (!confirmed) {
            redirect.addFlashAttribute("errorMessage", "A retry sends email to customers who were not reached. "
                    + "Use the confirm dialog to continue.");
            return "redirect:/admin/jobs/" + id;
        }
        try {
            int retried = jobs.retryFailed(TenantScope.administrator(), id, principal.getUsername());
            redirect.addFlashAttribute("successMessage", "Requeued " + retried + " recipient(s). Recipients already "
                    + "reached are never sent to twice.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/jobs/" + id;
    }

    private String act(Runnable action, RedirectAttributes redirect, String success) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/jobs";
    }

    private String form(Model model, String title) {
        model.addAttribute("formTitle", title);
        model.addAttribute("triggerTypes", List.of(com.ritpl.billgen.entity.JobTriggerType.values()));
        model.addAttribute("recurrences", List.of(com.ritpl.billgen.entity.JobRecurrence.values()));
        model.addAttribute("customers", customerNames());
        model.addAttribute("jobTypes", List.of(JobType.values()));
        model.addAttribute("notifications", notifications.findActive());
        model.addAttribute("statuses", List.of(JobStatus.values()));
        return "admin/jobs/form";
    }

    /** Id + name list used by the target-audience picker, bounded so the page stays cheap. */
    private java.util.List<java.util.Map<String, Object>> customerNames() {
        return customers.searchVisible("").stream().limit(400)
                .map(account -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
                    row.put("id", account.getId());
                    row.put("name", account.getCompanyName());
                    row.put("status", account.getStatus().getLabel());
                    return row;
                }).toList();
    }

    private static JobStatus statusOf(String value) {
        if (value == null || value.isBlank()) return null;
        try { return JobStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static JobRecipientStatus recipientStatusOf(String value) {
        if (value == null || value.isBlank()) return null;
        try { return JobRecipientStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }
}

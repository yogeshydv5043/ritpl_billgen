package com.ritpl.billgen.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AuditEventCommand;
import com.ritpl.billgen.service.AuditFilter;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.ExcelExportService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.UsageQuota;

@Controller
public class ExportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final CatalogService catalog;
    private final InvoiceService invoices;
    private final AuditService audit;
    private final ExcelExportService excel;
    private final SubscriptionService subscriptions;

    public ExportController(CatalogService catalog, InvoiceService invoices, AuditService audit,
            ExcelExportService excel, SubscriptionService subscriptions) {
        this.catalog = catalog; this.invoices = invoices; this.audit = audit; this.excel = excel; this.subscriptions = subscriptions;
    }

    @GetMapping("/admin/exports/products")
    public ResponseEntity<byte[]> adminProducts(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long customerId) {
        TenantScope scope = TenantScope.from(principal);
        return generate(scope, "PRODUCT_EXPORT", "PRODUCT", "Products", Map.of("customerFilterApplied", customerId != null), () -> {
            var rows = customerId == null ? catalog.findAll(scope) : catalog.findForCustomer(scope, customerId);
            return new Payload(excel.products(rows), rows.size(), "product-export.xlsx");
        });
    }

    @GetMapping("/portal/exports/products")
    public ResponseEntity<byte[]> customerProducts(@AuthenticationPrincipal AppUserPrincipal principal) {
        TenantScope scope = TenantScope.from(principal);
        return generate(scope, "PRODUCT_EXPORT", "PRODUCT", "Products", Map.of(), () -> {
            var rows = catalog.findAll(scope); return new Payload(excel.products(rows), rows.size(), "my-products.xlsx");
        });
    }

    @GetMapping("/admin/exports/invoices")
    public ResponseEntity<byte[]> adminInvoices(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long customerId) {
        return invoiceExport(TenantScope.from(principal), customerId);
    }

    @GetMapping("/portal/exports/invoices")
    public ResponseEntity<byte[]> customerInvoices(@AuthenticationPrincipal AppUserPrincipal principal) {
        return invoiceExport(TenantScope.from(principal), null);
    }

    @GetMapping("/admin/exports/audit")
    public ResponseEntity<byte[]> adminAudit(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action, @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return auditExport(TenantScope.from(principal), customerId, actor, action, resourceType, outcome, from, until);
    }

    @GetMapping("/portal/exports/audit")
    public ResponseEntity<byte[]> customerAudit(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return auditExport(TenantScope.from(principal), null, actor, action, resourceType, outcome, from, until);
    }

    private ResponseEntity<byte[]> invoiceExport(TenantScope scope, Long customerId) {
        YearMonth current = YearMonth.from(subscriptions.today());
        Instant from = current.minusMonths(9).atDay(1).atStartOfDay(subscriptions.zone()).toInstant();
        Instant until = current.plusMonths(1).atDay(1).atStartOfDay(subscriptions.zone()).toInstant();
        Map<String, Object> filters = Map.of("customerFilterApplied", scope.isAdminScope() && customerId != null,
                "months", 10, "timezone", subscriptions.zone().getId());
        String filename = "invoice-report-" + current + ".xlsx";
        return generate(scope, "INVOICE_REPORT_EXPORT", "INVOICE", "Monthly invoice report", filters, () -> {
            var rows = invoices.findForReport(scope, customerId, from, until);
            UsageQuota quota = null;
            Long ownerId = scope.isAdminScope() ? customerId : scope.requireCustomerId();
            if (ownerId != null) quota = subscriptions.usage(scope, ownerId);
            byte[] bytes = excel.monthlyInvoices(rows, current, subscriptions.zone(), quota, Instant.now());
            return new Payload(bytes, rows.size(), filename);
        });
    }

    private ResponseEntity<byte[]> auditExport(TenantScope scope, Long customerId, String actor, String action,
            String resourceType, String outcome, LocalDate from, LocalDate until) {
        if (!scope.isAdminScope()) scope.requireCustomerAccess(customerId == null ? scope.requireCustomerId() : customerId);
        Instant lower = from == null ? null : from.atStartOfDay(subscriptions.zone()).toInstant();
        Instant upper = until == null ? null : until.plusDays(1).atStartOfDay(subscriptions.zone()).toInstant();
        if (from != null && until != null && until.isBefore(from)) throw new IllegalArgumentException("End date must not be before start date.");
        AuditFilter filter = new AuditFilter(customerId, actor, action, resourceType, outcome, lower, upper);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerFilterApplied", scope.isAdminScope() && customerId != null);
        summary.put("actorFilterApplied", actor != null && !actor.isBlank());
        summary.put("action", action == null ? "" : action.trim());
        summary.put("resourceType", resourceType == null ? "" : resourceType.trim());
        summary.put("outcome", outcome == null ? "" : outcome.trim());
        summary.put("dateRangeApplied", from != null || until != null);
        return generate(scope, "AUDIT_EXPORT", "AUDIT", "Audit records", summary, () -> {
            var rows = audit.forExport(scope, filter); return new Payload(excel.audit(rows), rows.size(), "audit-export.xlsx");
        });
    }

    private ResponseEntity<byte[]> generate(TenantScope scope, String action, String resourceType, String resourceName,
            Map<String, ?> filterSummary, Supplier<Payload> supplier) {
        Long customerId = scope.isAdminScope() ? null : scope.requireCustomerId();
        try {
            Payload payload = supplier.get();
            audit.record(AuditEventCommand.success(customerId, action, resourceType, resourceName, null,
                    "Authorized Excel export generated.", null,
                    Map.of("rowCount", payload.rowCount(), "filters", filterSummary)));
            return ResponseEntity.ok().contentType(XLSX)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename() + "\"")
                    .body(payload.bytes());
        } catch (RuntimeException ex) {
            audit.recordFailure(AuditEventCommand.failure(customerId, action + "_FAILED", resourceType, resourceName, null,
                    "Excel export generation failed; no sensitive exception details were stored.",
                    Map.of("failureType", ex.getClass().getSimpleName(), "filters", filterSummary)));
            throw ex;
        }
    }

    private record Payload(byte[] bytes, long rowCount, String filename) { }
}

package com.ritpl.billgen.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
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
    private final com.ritpl.billgen.service.CustomerSettingsService settings;
    /** A listing export is bounded so one click cannot build a workbook the size of the table. */
    private static final int EXPORT_ROW_LIMIT = 5000;

    public ExportController(CatalogService catalog, InvoiceService invoices, AuditService audit,
            ExcelExportService excel, SubscriptionService subscriptions, com.ritpl.billgen.service.CustomerSettingsService settings) {
        this.catalog = catalog; this.invoices = invoices; this.audit = audit; this.excel = excel;
        this.subscriptions = subscriptions; this.settings = settings;
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

    /** Exports the catalogue as the page is currently filtered and sorted. */
    @GetMapping("/portal/exports/products")
    public ResponseEntity<byte[]> customerProducts(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String q, @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status, @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir) {
        TenantScope scope = TenantScope.from(principal);
        com.ritpl.billgen.entity.ProductType productType = null;
        if (type != null && !type.isBlank()) {
            try { productType = com.ritpl.billgen.entity.ProductType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { productType = null; }
        }
        Boolean active = "active".equals(status) ? Boolean.TRUE : "hidden".equals(status) ? Boolean.FALSE : null;
        var filter = new com.ritpl.billgen.repository.ProductFilter(q, category, active, productType);
        String property = List.of("name", "sku", "unitPrice", "taxRate", "active", "updatedAt", "createdAt").contains(sort) ? sort : "name";
        var order = org.springframework.data.domain.Sort.by("desc".equalsIgnoreCase(dir)
                ? org.springframework.data.domain.Sort.Direction.DESC : org.springframework.data.domain.Sort.Direction.ASC, property);
        Map<String, Object> summary = Map.of("search", q == null ? "" : q, "categoryId", category == null ? "" : category.toString(),
                "status", status == null ? "all" : status, "type", type == null ? "all" : type);
        return generate(scope, "PRODUCT_EXPORT", "PRODUCT", "Products", summary, () -> {
            var rows = catalog.findAll(scope, filter, order);
            return new Payload(excel.products(rows), rows.size(), "my-products.xlsx");
        });
    }

    /** The invoice rows the invoices page is showing, with the same filters applied. */
    @GetMapping("/portal/exports/invoices/list")
    public ResponseEntity<byte[]> customerInvoiceListing(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(required = false, defaultValue = "issued") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir) {
        TenantScope scope = TenantScope.from(principal);
        if (from != null && until != null && until.isBefore(from)) throw new IllegalArgumentException("End date must not be before start date.");
        InvoiceStatus invoiceStatus = null;
        if (status != null && !status.isBlank()) {
            try { invoiceStatus = InvoiceStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { invoiceStatus = null; }
        }
        var filter = new com.ritpl.billgen.repository.InvoiceFilter(q, invoiceStatus, from, until, overdue, null);
        String property = List.of("issueDate", "dueDate", "invoiceNumber", "status", "totalAmount", "createdAt").contains(sort) ? sort : "issueDate";
        var order = org.springframework.data.domain.Sort.by("asc".equalsIgnoreCase(dir)
                ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC, property);
        var page = invoices.page(scope, filter, org.springframework.data.domain.PageRequest.of(0, EXPORT_ROW_LIMIT, order));
        Map<String, Object> summary = Map.of("search", q == null ? "" : q, "status", status == null ? "all" : status,
                "from", from == null ? "" : from.toString(), "until", until == null ? "" : until.toString(), "overdue", overdue);
        ZoneId zone = subscriptions.zone();
        Instant generatedAt = Instant.now();
        List<Invoice> rows = page.getContent();
        return generate(scope, "INVOICE_EXPORT", "INVOICE", "Invoices", summary, () ->
                new Payload(excel.invoiceListing(rows, zone, Map.of("Filters", summary.toString()), generatedAt),
                        rows.size(), "my-invoices.xlsx"));
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

    /** An audit export sees exactly the account's own records, inside its authorised viewing window. */
    @GetMapping("/portal/exports/audit")
    public ResponseEntity<byte[]> customerAudit(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        TenantScope scope = TenantScope.from(principal);
        LocalDate earliest = LocalDate.ofInstant(settings.auditWindowStart(scope.requireCustomerId()), subscriptions.zone());
        if (until != null && until.isBefore(earliest)) {
            throw new IllegalArgumentException("That period is outside your account's audit viewing window.");
        }
        if (from == null || from.isBefore(earliest)) from = earliest;
        return auditExport(scope, null, actor, action, resourceType, outcome, from, until);
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

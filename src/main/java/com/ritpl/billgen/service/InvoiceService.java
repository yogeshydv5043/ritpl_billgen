package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.CustomInvoiceTemplate;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceItem;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.security.TenantScope;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final CatalogService catalog;
    private final InvoiceCalculator calculator;
    private final SubscriptionService subscriptions;
    private final CustomTemplateService customTemplates;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CustomerSettingsService settings;
    private final CustomerAlertService alerts;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers, CatalogService catalog,
            InvoiceCalculator calculator, SubscriptionService subscriptions, CustomTemplateService customTemplates,
            JdbcTemplate jdbc, AuditService audit, CustomerSettingsService settings, CustomerAlertService alerts) {
        this.invoices = invoices; this.customers = customers; this.catalog = catalog; this.calculator = calculator;
        this.subscriptions = subscriptions; this.customTemplates = customTemplates; this.jdbc = jdbc; this.audit = audit;
        this.settings = settings; this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll(TenantScope scope) {
        return scope.isAdminScope() ? invoices.findAllByOrderByCreatedAtDesc()
                : invoices.findByCustomer_IdOrderByIssueDateDesc(scope.requireCustomerId());
    }
    /**
     * A customer's invoice list: optional search, status and issue-date filters, a whitelisted sort and
     * the page size the account chose in its own settings. Ownership is applied from the scope, so an
     * invoice belonging to another company is not merely hidden - it is never selected.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Invoice> page(TenantScope scope,
            com.ritpl.billgen.repository.InvoiceFilter filter, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Invoice> tenant = scope.isAdminScope() ? null
                : com.ritpl.billgen.repository.InvoiceSpecs.ofCustomer(scope.requireCustomerId());
        org.springframework.data.jpa.domain.Specification<Invoice> conditions =
                com.ritpl.billgen.repository.InvoiceSpecs.forFilter(filter, subscriptions.today());
        org.springframework.data.jpa.domain.Specification<Invoice> combined =
                tenant == null ? conditions : tenant.and(conditions);
        return invoices.findAll(combined, CatalogService.safePage(pageable));
    }

    /** The account's most recent invoices, newest issue date first, for its dashboard. */
    @Transactional(readOnly = true)
    public List<Invoice> recent(TenantScope scope, int limit) {
        return invoices.findByCustomer_IdOrderByIssueDateDesc(scope.requireCustomerId(),
                CatalogService.bounded(limit));
    }

    @Transactional(readOnly = true)
    public List<Invoice> findForCustomer(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId); return invoices.findByCustomer_IdOrderByIssueDateDesc(customerId);
    }
    @Transactional(readOnly = true)
    public List<Invoice> findRecent(TenantScope scope) {
        return scope.isAdminScope() ? invoices.findTop7ByOrderByCreatedAtDesc()
                : invoices.findTop7ByCustomer_IdOrderByCreatedAtDesc(scope.requireCustomerId());
    }
    @Transactional(readOnly = true)
    public Invoice findById(TenantScope scope, long id) {
        if (scope.isAdminScope()) return invoices.findWithDetailsById(id).orElseThrow(() -> notFound());
        return invoices.findOwned(id, scope.requireCustomerId()).orElseThrow(() -> notFound());
    }

    /**
     * What the invoice will say, computed by exactly the same maths {@link #create} uses - product
     * snapshots, per-line tax, discount, rounding - and nothing is written, so previewing can never
     * consume an allowance. The allowance is reported anyway, because a customer needs to know before
     * pressing the button whether the invoice will be allowed at all.
     */
    @Transactional(readOnly = true)
    public InvoicePreview preview(TenantScope scope, CreateInvoiceCommand command) {
        if (command == null) throw new IllegalArgumentException("Nothing to preview.");
        long ownerId = resolveOwnerId(scope, command.customerId());
        validateDates(command.issueDate(), command.dueDate());
        List<InvoiceLineInput> lines = resolveProductSnapshots(scope, command.lines());
        if (lines.isEmpty()) throw new IllegalArgumentException("Add at least one line before previewing the invoice.");
        InvoiceTotals totals = calculator.calculate(lines, command.discountAmount());
        UsageQuota quota = subscriptions.usage(scope, ownerId);
        boolean counts = command.initialStatus() != InvoiceStatus.DRAFT;
        long remainingAfter = counts ? quota.remainingInvoices() - 1 : quota.remainingInvoices();
        return new InvoicePreview(normalizeCurrency(command.currency()), totals, counts,
                Math.max(0, remainingAfter), counts && remainingAfter < 0);
    }

    /** The figures a preview shows: the document as it would be issued, plus its effect on the allowance. */
    public record InvoicePreview(String currency, InvoiceTotals totals, boolean consumesAllowance,
            long remainingAfter, boolean allowanceBlocked) { }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice create(TenantScope scope, CreateInvoiceCommand command) {
        Long auditCustomerId = null;
        try {
            scope.requireWriteAccess();
            if (command == null) throw new IllegalArgumentException("Nothing to save.");
            long ownerId = resolveOwnerId(scope, command.customerId()); auditCustomerId = ownerId;
            validateDates(command.issueDate(), command.dueDate());
            InvoiceStatus status = command.initialStatus() == null ? InvoiceStatus.DRAFT : command.initialStatus();
            if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.SENT)
                throw new IllegalArgumentException("A new invoice must start as DRAFT or SENT.");
            Customer owner = customers.findById(ownerId).filter(account -> !account.isSystemAccount())
                    .orElseThrow(() -> new IllegalArgumentException("Choose a valid customer account."));
            CustomerSubscription activeSubscription = subscriptions.requireActiveForUpdate(scope);
            List<InvoiceLineInput> validatedLines = resolveProductSnapshots(scope, command.lines());
            InvoiceTotals totals = calculator.calculate(validatedLines, command.discountAmount());
            ResolvedTemplate chosen = resolveTemplate(scope, command, ownerId);
            if (status == InvoiceStatus.SENT) ensureAllowance(activeSubscription, ownerId);

            Invoice invoice = new Invoice();
            long sequence = jdbc.queryForObject("SELECT nextval('invoice_number_seq')", Long.class);
            // The sequence is central and gapless per tenant boundary, so numbers stay unique however an
            // account likes them to look; only the prefix is the account's own choice.
            invoice.setInvoiceNumber(settings.invoiceNumberPrefix(ownerId) + "%06d".formatted(sequence));
            invoice.setCustomer(owner); invoice.setCurrency(normalizeCurrency(command.currency()));
            invoice.setIssueDate(command.issueDate()); invoice.setDueDate(command.dueDate());
            invoice.setTemplate(chosen.standard()); invoice.setCustomTemplate(chosen.custom());
            invoice.setTemplateNameSnapshot(chosen.name()); invoice.setTemplateStyleSnapshot(chosen.style());
            invoice.setTemplateAccentSnapshot(chosen.accentColor());
            applyPresentation(scope, invoice, chosen.custom(), command, ownerId);
            invoice.setNotes(blankToNull(command.notes()));
            invoice.setSubtotal(totals.subtotal()); invoice.setTaxAmount(totals.taxAmount());
            invoice.setDiscountAmount(totals.discountAmount()); invoice.setTotalAmount(totals.totalAmount());
            invoice.setStatus(status); snapshotCompany(invoice, owner); addItems(invoice, totals);
            if (status == InvoiceStatus.SENT) { invoice.setUsageCounted(true); invoice.setUsageSubscription(activeSubscription); }
            Invoice saved = invoices.saveAndFlush(invoice);
            // The notice follows the commit of the count, not a guess about it: a draft never warns.
            if (status == InvoiceStatus.SENT) alerts.quotaAlert(activeSubscription);
            audit.record(AuditEventCommand.success(ownerId, "CREATE", "INVOICE", "Invoice", saved.getId(),
                    status == InvoiceStatus.DRAFT ? "Draft invoice created; no allowance was consumed." : "Invoice created as sent and counted against the active subscription.",
                    null, Map.of("invoiceNumber", saved.getInvoiceNumber(), "status", status.name(), "currency", saved.getCurrency(),
                            "subtotal", saved.getSubtotal(), "tax", saved.getTaxAmount(), "discount", saved.getDiscountAmount(),
                            "total", saved.getTotalAmount(), "usageCounted", saved.isUsageCounted())));
            return saved;
        } catch (RuntimeException ex) {
            audit.recordFailure(AuditEventCommand.failure(auditCustomerId, "CREATE_FAILED", "INVOICE", "Invoice", null,
                    "Invoice creation attempt failed validation, authorization, subscription, or quota checks.",
                    Map.of("failureType", ex.getClass().getSimpleName())));
            throw ex;
        }
    }

    /**
     * What the document will print, frozen at issue time: the layout the account had given its own
     * template, with the account's defaults behind it, and a per-invoice override in front of both.
     * A later edit - or deletion - of that template therefore cannot alter a delivered document.
     */
    private void applyPresentation(TenantScope scope, Invoice invoice, CustomInvoiceTemplate presentation,
            CreateInvoiceCommand command, long ownerId) {
        if (presentation != null) {
            invoice.setTemplateFontSnapshot(presentation.getFontKey());
            invoice.setTemplateDensitySnapshot(presentation.getLayoutDensity());
            invoice.setTemplateTermsSnapshot(presentation.getPaymentTerms());
            invoice.setTemplateFooterSnapshot(presentation.getFooterText());
            invoice.setTemplateShowLogoSnapshot(presentation.isShowLogo());
            if (presentation.isShowBankDetails()) invoice.setTemplateBankSnapshot(presentation.getBankDetails());
            invoice.setTemplateSignatureLabelSnapshot(presentation.getSignatureLabel());
            invoice.setTemplateSignatureNameSnapshot(presentation.getSignatureName());
        }
        var preferences = settings.get(scope, ownerId);
        // Terms: the value typed on this invoice wins, then the template's, then the account's default.
        if (command != null && !isBlank(command.paymentTerms())) {
            invoice.setTemplateTermsSnapshot(command.paymentTerms().trim());
        } else if (isBlank(invoice.getPaymentTerms())) {
            // Nothing typed and nothing on the template: the account's own default fills in.
            invoice.setTemplateTermsSnapshot(blankToNull(preferences.getDefaultPaymentTerms()));
        }
        if (isBlank(invoice.getFooterText())) invoice.setTemplateFooterSnapshot(blankToNull(preferences.getDefaultFooter()));
    }

    /**
     * Editing a draft. The same validation and the same maths as {@link #create}, applied in place: the
     * invoice keeps its number, its owner and its issue history, and only a document that has not been
     * issued may change. An issued invoice is corrected by cancelling it and issuing another, which is
     * what keeps a delivered figure from being rewritten.
     */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice updateDraft(TenantScope scope, long id, CreateInvoiceCommand command) {
        Invoice invoice = findById(scope, id);
        long ownerId = invoice.getCustomerId();
        Map<String, Object> before = describe(invoice);
        try {
            scope.requireWriteAccess();
            if (command == null) throw new IllegalArgumentException("Nothing to save.");
            if (invoice.getStatus() != InvoiceStatus.DRAFT) {
                throw new IllegalArgumentException("Only a draft can be edited. This invoice was already issued, so it is kept exactly as it was delivered - cancel it and issue another instead.");
            }
            if (command.initialStatus() != null && command.initialStatus() != InvoiceStatus.DRAFT) {
                throw new IllegalArgumentException("A draft stays a draft here; use the issue action to send it.");
            }
            validateDates(command.issueDate(), command.dueDate());
            List<InvoiceLineInput> lines = resolveProductSnapshots(scope, command.lines());
            if (lines.isEmpty()) throw new IllegalArgumentException("An invoice needs at least one line.");
            InvoiceTotals totals = calculator.calculate(lines, command.discountAmount());
            ResolvedTemplate chosen = resolveTemplate(scope, command, ownerId);

            invoice.setIssueDate(command.issueDate()); invoice.setDueDate(command.dueDate());
            invoice.setCurrency(normalizeCurrency(command.currency()));
            invoice.setTemplate(chosen.standard()); invoice.setCustomTemplate(chosen.custom());
            invoice.setTemplateNameSnapshot(chosen.name()); invoice.setTemplateStyleSnapshot(chosen.style());
            invoice.setTemplateAccentSnapshot(chosen.accentColor());
            applyPresentation(scope, invoice, chosen.custom(), command, ownerId);
            invoice.setNotes(blankToNull(command.notes()));
            invoice.setSubtotal(totals.subtotal()); invoice.setTaxAmount(totals.taxAmount());
            invoice.setDiscountAmount(totals.discountAmount()); invoice.setTotalAmount(totals.totalAmount());
            // Replacing the lines: the old rows are removed by orphanRemoval, so a draft never keeps a
            // stale description or amount next to a recomputed total.
            invoice.getItems().clear(); addItems(invoice, totals);
            Invoice saved = invoices.saveAndFlush(invoice);
            audit.record(AuditEventCommand.success(ownerId, "UPDATE", "INVOICE", "Invoice", id,
                    "Draft invoice edited; totals recomputed on the server.", before, describe(saved)));
            return saved;
        } catch (RuntimeException ex) {
            audit.recordFailure(AuditEventCommand.failure(ownerId, "UPDATE_FAILED", "INVOICE", "Invoice", id,
                    "Draft edit attempt failed validation or authorization checks.",
                    Map.of("failureType", ex.getClass().getSimpleName())));
            throw ex;
        }
    }

    /** The before/after pair an audit entry stores for an invoice: the figures, never a secret. */
    private Map<String, Object> describe(Invoice invoice) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("invoiceNumber", invoice.getInvoiceNumber());
        values.put("status", invoice.getStatus() == null ? null : invoice.getStatus().name());
        values.put("issueDate", String.valueOf(invoice.getIssueDate()));
        values.put("dueDate", String.valueOf(invoice.getDueDate()));
        values.put("currency", invoice.getCurrency());
        values.put("template", invoice.getTemplateName());
        values.put("subtotal", invoice.getSubtotal());
        values.put("taxAmount", invoice.getTaxAmount());
        values.put("discountAmount", invoice.getDiscountAmount());
        values.put("totalAmount", invoice.getTotalAmount());
        values.put("lines", invoice.getItems().size());
        return values;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice markSent(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id); scope.requireWriteAccess();
        try {
            if (invoice.getStatus() != InvoiceStatus.DRAFT) throw new IllegalArgumentException("Only a draft invoice can be issued.");
            CustomerSubscription current = subscriptions.requireActiveForUpdate(scope);
            ensureAllowance(current, invoice.getCustomerId());
            invoice.setStatus(InvoiceStatus.SENT); invoice.setUsageCounted(true); invoice.setUsageSubscription(current);
            alerts.quotaAlert(current);
            audit.record(AuditEventCommand.success(invoice.getCustomerId(), "ISSUE", "INVOICE", "Invoice", id,
                    "Draft invoice issued and counted against the active subscription allowance.",
                    Map.of("status", "DRAFT", "usageCounted", false), Map.of("status", "SENT", "usageCounted", true)));
            return invoice;
        } catch (RuntimeException ex) {
            audit.recordFailure(AuditEventCommand.failure(invoice.getCustomerId(), "ISSUE_FAILED", "INVOICE", "Invoice", id,
                    "Invoice issue attempt failed validation, expiry, or quota checks.",
                    Map.of("failureType", ex.getClass().getSimpleName())));
            throw ex;
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice markPaid(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id); scope.requireWriteAccess();
        if (invoice.getStatus() != InvoiceStatus.SENT) throw new IllegalArgumentException("Only a sent invoice can be marked as paid.");
        invoice.setStatus(InvoiceStatus.PAID); invoice.setPaidAt(LocalDateTime.now());
        alerts.paymentAlert(invoice);
        audit.record(AuditEventCommand.success(invoice.getCustomerId(), "UPDATE", "INVOICE", "Invoice", id,
                "Payment recorded for invoice.", Map.of("status", "SENT"), Map.of("status", "PAID")));
        return invoice;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice cancel(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id); scope.requireWriteAccess();
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED)
            throw new IllegalArgumentException("Only draft or sent invoices can be cancelled.");
        String before = invoice.getStatus().name(); boolean charged = invoice.isUsageCounted();
        invoice.setStatus(InvoiceStatus.CANCELLED); invoice.setCancelledAt(Instant.now());
        audit.record(AuditEventCommand.success(invoice.getCustomerId(), "CANCEL", "INVOICE", "Invoice", id,
                charged ? "Issued invoice cancelled; its used allowance is not restored." : "Draft cancelled before issue; no allowance was consumed.",
                Map.of("status", before, "usageCounted", charged), Map.of("status", "CANCELLED", "usageCounted", charged)));
        return invoice;
    }

    /**
     * Authorises a PDF download and records it. The returned invoice carries its own snapshots, so
     * the renderer never needs to read the current company profile, catalogue or template.
     */
    @Transactional
    public Invoice preparePdfDownload(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id);
        audit.record(AuditEventCommand.success(invoice.getCustomerId(), "EXPORT", "INVOICE", "Invoice", id,
                "Invoice PDF downloaded.", null,
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "exportType", "PDF",
                        "totalAmount", invoice.getTotalAmount(), "currency", invoice.getCurrency())));
        return invoice;
    }

    @Transactional
    public void recordPrintExport(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id);
        audit.record(AuditEventCommand.success(invoice.getCustomerId(), "EXPORT", "INVOICE", "Invoice", id,
                "Invoice print or PDF preview opened.", null, Map.of("invoiceNumber", invoice.getInvoiceNumber(), "exportType", "PRINT_VIEW")));
    }

    @Transactional(readOnly = true)
    public List<Invoice> findForReport(TenantScope scope, Long customerId, Instant from, Instant until) {
        if (scope.isAdminScope()) return customerId == null
                ? invoices.findByGeneratedAtGreaterThanEqualAndGeneratedAtLessThanOrderByGeneratedAtAsc(from, until)
                : invoices.findByCustomer_IdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThanOrderByGeneratedAtAsc(customerId, from, until);
        long ownId = scope.requireCustomerId(); scope.requireCustomerAccess(customerId == null ? ownId : customerId);
        return invoices.findByCustomer_IdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThanOrderByGeneratedAtAsc(ownId, from, until);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "dashboard", key = "'stats'", condition = "#p0.isAdminScope()", unless = "#result == null")
    public DashboardStats dashboardStats(TenantScope scope) {
        if (!scope.isAdminScope()) throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        long total = invoices.count(); long customerCount = customers.countBySystemAccountFalse();
        long overdue = invoices.countByStatusAndDueDateBefore(InvoiceStatus.SENT, subscriptions.today());
        BigDecimal paid = invoices.sumTotalByStatus(InvoiceStatus.PAID); BigDecimal outstanding = invoices.sumTotalByStatus(InvoiceStatus.SENT);
        return new DashboardStats(total, customerCount, overdue, paid == null ? BigDecimal.ZERO : paid,
                outstanding == null ? BigDecimal.ZERO : outstanding);
    }

    private void ensureAllowance(CustomerSubscription subscription, long ownerId) {
        long used = subscription.getInvoiceUsageCarryForward() + invoices.countByUsageSubscription_IdAndUsageCountedTrue(subscription.getId());
        if (used >= subscription.getEffectiveInvoiceLimit())
            throw new IllegalArgumentException("Your subscription invoice allowance is exhausted. Renew or upgrade your plan.");
    }

    private List<InvoiceLineInput> resolveProductSnapshots(TenantScope scope, List<InvoiceLineInput> inputs) {
        if (inputs == null) return List.of();
        List<InvoiceLineInput> result = new ArrayList<>(inputs.size());
        for (InvoiceLineInput input : inputs) {
            if (input == null || input.productId() == null) { result.add(input); continue; }
            var product = catalog.findById(scope, input.productId());
            if (!product.isActive()) throw new IllegalArgumentException("An invoice line references an inactive product.");
            result.add(new InvoiceLineInput(product.getName(), input.quantity(), product.getUnitPrice(), product.getTaxRate(), product.getId()));
        }
        return result;
    }

    private ResolvedTemplate resolveTemplate(TenantScope scope, CreateInvoiceCommand command, long ownerId) {
        boolean standardChosen = command.templateId() != null; boolean customChosen = command.customTemplateId() != null;
        if (standardChosen == customChosen) throw new IllegalArgumentException("Choose exactly one available bill template or custom template.");
        if (standardChosen) {
            BillTemplate t = subscriptions.requireAccessibleTemplate(scope, command.templateId());
            return new ResolvedTemplate(t, null, t.getName(), t.getStyleCode(), t.getAccentColor());
        }
        CustomInvoiceTemplate t = customTemplates.findById(scope, command.customTemplateId());
        if (!t.isActive() || !Long.valueOf(ownerId).equals(t.getCustomerId()))
            throw new IllegalArgumentException("Choose one of your active custom invoice templates.");
        return new ResolvedTemplate(null, t, t.getName(), t.getStyleKey(), t.getAccentColor());
    }

    private List<InvoiceLineInput> notNullLines(List<InvoiceLineInput> values) { return values == null ? List.of() : values; }
    private void snapshotCompany(Invoice invoice, Customer customer) {
        invoice.setCompanyNameSnapshot(customer.getCompanyName()); invoice.setCompanyContactSnapshot(customer.getContactName());
        invoice.setCompanyEmailSnapshot(customer.getEmail()); invoice.setCompanyPhoneSnapshot(customer.getPhone());
        invoice.setCompanyAddressSnapshot(customer.getFullAddress()); invoice.setCompanyGstinSnapshot(customer.getGstin());
        invoice.setCompanyLogoSnapshot(customer.getLogoData()); invoice.setCompanyLogoContentTypeSnapshot(customer.getLogoContentType());
    }
    private void addItems(Invoice invoice, InvoiceTotals totals) {
        for (InvoiceTotals.CalculatedLine line : totals.lines()) {
            InvoiceLineInput input = line.input();
            invoice.addItem(new InvoiceItem(input.description().trim(), input.quantity(), input.unitPrice(),
                    input.taxRate() == null ? BigDecimal.ZERO : input.taxRate(), line.subtotal(), line.tax(), line.total()));
        }
    }
    private String normalizeCurrency(String value) {
        String currency = value == null || value.isBlank() ? "INR" : value.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a 3-letter code.");
        return currency;
    }
    private void validateDates(LocalDate issueDate, LocalDate dueDate) {
        if (issueDate == null || dueDate == null || dueDate.isBefore(issueDate)) throw new IllegalArgumentException("The due date must be on or after the issue date.");
    }
    private long resolveOwnerId(TenantScope scope, Long requestedCustomerId) {
        scope.requireWriteAccess(); long ownId = scope.requireCustomerId();
        if (requestedCustomerId != null && requestedCustomerId != ownId) throw new IllegalArgumentException("You can only create invoices for your own account.");
        return ownId;
    }
    private String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Invoice not found."); }
    private record ResolvedTemplate(BillTemplate standard, CustomInvoiceTemplate custom, String name, String style, String accentColor) { }
}

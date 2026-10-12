package com.ritpl.billgen.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceItem;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Invoices, owned by exactly one customer account.
 *
 * <p>Reads are filtered by the caller {@link TenantScope}; a record that belongs to another
 * account is reported as "not found". Writes additionally resolve the template through
 * {@link SubscriptionService}, so an invoice can only ever be created with a template that is
 * included in the plan the account purchased.</p>
 */
@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final InvoiceCalculator calculator;
    private final SubscriptionService subscriptions;
    private final JdbcTemplate jdbc;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers, InvoiceCalculator calculator,
            SubscriptionService subscriptions, JdbcTemplate jdbc) {
        this.invoices = invoices; this.customers = customers; this.calculator = calculator;
        this.subscriptions = subscriptions; this.jdbc = jdbc;
    }

    // --- reads -----------------------------------------------------------------------------------------------------

    /** Invoices of the caller's account, or every invoice for an administrator. */
    @Transactional(readOnly = true)
    public List<Invoice> findAll(TenantScope scope) {
        return scope.isAdminScope()
                ? invoices.findAllByOrderByCreatedAtDesc()
                : invoices.findByCustomer_IdOrderByIssueDateDesc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public List<Invoice> findForCustomer(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        return invoices.findByCustomer_IdOrderByIssueDateDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> findRecent(TenantScope scope) {
        return scope.isAdminScope()
                ? invoices.findTop7ByOrderByCreatedAtDesc()
                : invoices.findTop7ByCustomer_IdOrderByCreatedAtDesc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public Invoice findById(TenantScope scope, long id) {
        if (scope.isAdminScope()) {
            return invoices.findWithDetailsById(id).orElseThrow(() -> notFound());
        }
        return invoices.findOwned(id, scope.requireCustomerId()).orElseThrow(() -> notFound());
    }

    // --- writes ----------------------------------------------------------------------------------------------------

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice create(TenantScope scope, CreateInvoiceCommand command) {
        scope.requireWriteAccess();
        if (command == null) throw new IllegalArgumentException("Nothing to save.");
        long ownerId = resolveOwnerId(scope, command.customerId());
        if (command.issueDate() == null || command.dueDate() == null || command.dueDate().isBefore(command.issueDate())) {
            throw new IllegalArgumentException("The due date must be on or after the issue date.");
        }
        Customer customer = customers.findById(ownerId)
                .filter(account -> !account.isSystemAccount())
                .orElseThrow(() -> new IllegalArgumentException("Choose a valid customer account."));
        InvoiceTotals totals = calculator.calculate(command.lines());
        BillTemplate template = subscriptions.requireAccessibleTemplate(scope, command.templateId());
        String currency = command.currency() == null || command.currency().isBlank()
                ? "INR" : command.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a 3-letter code.");

        Invoice invoice = new Invoice();
        long sequence = jdbc.queryForObject("SELECT nextval('invoice_number_seq')", Long.class);
        invoice.setInvoiceNumber("INV-%06d".formatted(sequence));
        invoice.setCustomer(customer);
        invoice.setCurrency(currency);
        invoice.setIssueDate(command.issueDate());
        invoice.setDueDate(command.dueDate());
        invoice.setTemplate(template);
        invoice.setNotes(command.notes() == null || command.notes().isBlank() ? null : command.notes().trim());
        invoice.setSubtotal(totals.subtotal());
        invoice.setTaxAmount(totals.taxAmount());
        invoice.setTotalAmount(totals.totalAmount());
        invoice.setStatus(command.initialStatus() == null ? InvoiceStatus.SENT : command.initialStatus());
        if (invoice.getStatus() == InvoiceStatus.PAID) invoice.setPaidAt(LocalDateTime.now());
        for (InvoiceTotals.CalculatedLine line : totals.lines()) {
            var input = line.input();
            invoice.addItem(new InvoiceItem(input.description().trim(), input.quantity(), input.unitPrice(),
                    input.taxRate() == null ? java.math.BigDecimal.ZERO : input.taxRate(),
                    line.subtotal(), line.tax(), line.total()));
        }
        return invoices.saveAndFlush(invoice);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice markSent(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id);
        scope.requireWriteAccess();
        if (invoice.getStatus() != InvoiceStatus.DRAFT) throw new IllegalArgumentException("Only a draft invoice can be sent.");
        invoice.setStatus(InvoiceStatus.SENT);
        return invoice;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice markPaid(TenantScope scope, long id) {
        Invoice invoice = findById(scope, id);
        scope.requireWriteAccess();
        if (invoice.getStatus() != InvoiceStatus.SENT) throw new IllegalArgumentException("Only a sent invoice can be marked as paid.");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoice;
    }

    // --- reporting ------------------------------------------------------------------------------------------------

        @Transactional(readOnly = true)
    @Cacheable(cacheNames = "dashboard", key = "'stats'", condition = "#p0.isAdminScope()", unless = "#result == null")
    public DashboardStats dashboardStats(TenantScope scope) {
        if (!scope.isAdminScope()) throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        long total = invoices.count();
        long customersTotal = customers.countBySystemAccountFalse();
        long overdue = invoices.countByStatusAndDueDateBefore(InvoiceStatus.SENT, LocalDate.now());
        var paid = invoices.sumTotalByStatus(InvoiceStatus.PAID);
        var outstanding = invoices.sumTotalByStatus(InvoiceStatus.SENT);
        return new DashboardStats(total, customersTotal, overdue,
                paid == null ? java.math.BigDecimal.ZERO : paid,
                outstanding == null ? java.math.BigDecimal.ZERO : outstanding);
    }

    /**
     * A customer account may only invoice itself. An administrator must name the account, but
     * administrators have read-only access to customer records, so creation is refused for them.
     */
    private long resolveOwnerId(TenantScope scope, Long requestedCustomerId) {
        scope.requireWriteAccess();
        long ownId = scope.requireCustomerId();
        if (requestedCustomerId != null && requestedCustomerId != ownId) {
            throw new IllegalArgumentException("You can only create invoices for your own account.");
        }
        return ownId;
    }

    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Invoice not found."); }
}
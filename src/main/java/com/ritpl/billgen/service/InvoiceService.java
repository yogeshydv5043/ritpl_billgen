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
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceItem;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final InvoiceCalculator calculator;
    private final JdbcTemplate jdbc;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers, InvoiceCalculator calculator, JdbcTemplate jdbc) {
        this.invoices = invoices;
        this.customers = customers;
        this.calculator = calculator;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll() { return invoices.findAllByOrderByCreatedAtDesc(); }

    @Transactional(readOnly = true)
    public List<Invoice> findRecent() { return invoices.findTop7ByOrderByCreatedAtDesc(); }

    @Transactional(readOnly = true)
    public Invoice findById(long id) {
        return invoices.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice not found."));
    }

    @Transactional(readOnly = true)
    public List<Invoice> findForCustomer(long customerId) { return invoices.findByCustomer_IdOrderByIssueDateDesc(customerId); }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice create(CreateInvoiceCommand command) {
        if (command == null || command.customerId() == null) throw new IllegalArgumentException("Choose a customer.");
        if (command.issueDate() == null || command.dueDate() == null || command.dueDate().isBefore(command.issueDate())) {
            throw new IllegalArgumentException("The due date must be on or after the issue date.");
        }
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Choose a valid customer."));
        InvoiceTotals totals = calculator.calculate(command.lines());
        String template = command.templateCode() == null ? "CLASSIC" : command.templateCode().toUpperCase(Locale.ROOT);
        if (!List.of("CLASSIC", "MODERN", "MINIMAL").contains(template)) {
            throw new IllegalArgumentException("Choose one of the available invoice designs.");
        }
        String currency = command.currency() == null || command.currency().isBlank() ? "INR" : command.currency().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a 3-letter code.");
        Invoice invoice = new Invoice();
        long sequence = jdbc.queryForObject("SELECT nextval('invoice_number_seq')", Long.class);
        invoice.setInvoiceNumber("INV-%06d".formatted(sequence));
        invoice.setCustomer(customer);
        invoice.setCurrency(currency);
        invoice.setIssueDate(command.issueDate());
        invoice.setDueDate(command.dueDate());
        invoice.setTemplateCode(template);
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
    public Invoice markSent(long id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) throw new IllegalArgumentException("Only a draft invoice can be sent.");
        invoice.setStatus(InvoiceStatus.SENT);
        return invoice;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Invoice markPaid(long id) {
        Invoice invoice = findById(id);
        if (invoice.getStatus() != InvoiceStatus.SENT) throw new IllegalArgumentException("Only a sent invoice can be marked as paid.");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        return invoice;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "dashboard", key = "'stats'", unless = "#result == null")
    public DashboardStats dashboardStats() {
        long total = invoices.count();
        long customersTotal = customers.count();
        long overdue = invoices.countByStatusAndDueDateBefore(InvoiceStatus.SENT, LocalDate.now());
        var paid = invoices.sumTotalByStatus(InvoiceStatus.PAID);
        var outstanding = invoices.sumTotalByStatus(InvoiceStatus.SENT);
        return new DashboardStats(total, customersTotal, overdue,
                paid == null ? java.math.BigDecimal.ZERO : paid,
                outstanding == null ? java.math.BigDecimal.ZERO : outstanding);
    }
}

package com.ritpl.billgen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.entity.AuditRecord;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AuditFilter;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CompanyProfileCommand;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceLineInput;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.PlanCommand;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.ProductCommand;
import com.ritpl.billgen.service.RegistrationCommand;
import com.ritpl.billgen.service.ResourceNotFoundException;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.TemplateCommand;
import com.ritpl.billgen.service.TemplateService;

/**
 * Runs the real application against a PostgreSQL container: Flyway migrations, JPA schema
 * validation, the service layer and the database constraints all participate. This is where the
 * concurrency guarantee and the snapshot guarantee are actually proven; the tests are skipped when
 * Docker is unavailable so a normal unit-test run still succeeds.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.demo.seed-enabled=false",
        "spring.session.store-type=none",
        "spring.cache.type=none",
        "app.subscription.time-zone=UTC",
        "spring.datasource.hikari.maximum-pool-size=40"
})
class BillingIntegrationTest {

    private static final int ALLOWANCE = 4;
    private static final int CONCURRENT_ATTEMPTS = 12;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billgen_it")
            .withUsername("billgen")
            .withPassword("billgen_it_only");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TemplateService templateService;
    @Autowired PlanService planService;
    @Autowired CustomerService customerService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired CatalogService catalogService;
    @Autowired InvoiceService invoiceService;
    @Autowired AuditService auditService;
    @Autowired InvoiceRepository invoices;
    @Autowired CustomerSubscriptionRepository subscriptionRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsAndMappingsAgreeOnTheDatabaseSchema() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'", Integer.class);
        assertTrue(tables != null && tables >= 12, "Flyway created the full schema, found " + tables + " tables");
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'invoices' and column_name = 'discount_amount'",
                Integer.class), "the invoice discount column exists");
        assertEquals(0, jdbc.queryForObject("select count(*) from app_users where role = 'FINANCE'", Integer.class),
                "only the two supported roles exist");
    }

    @Test
    void concurrentIssuanceCanNeverExceedTheSubscriptionAllowance() throws Exception {
        Tenant tenant = newTenant("concurrent", ALLOWANCE);

        List<String> rejections = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> attempts = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_ATTEMPTS; i++) {
            attempts.add(pool.submit(() -> {
                go.await(30, TimeUnit.SECONDS);
                try {
                    return invoiceService.create(tenant.scope(), sentInvoice(tenant)).getInvoiceNumber();
                } catch (RuntimeException rejected) {
                    rejections.add(rejected.getClass().getSimpleName() + ": " + rejected.getMessage());
                    return null;
                }
            }));
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "the concurrent batch finished");

        List<String> issued = new ArrayList<>();
        for (Future<String> attempt : attempts) {
            String number = attempt.get();
            if (number != null) issued.add(number);
        }

        assertEquals(ALLOWANCE, issued.size(), "exactly the allowance may be issued, however many requests race");
        assertEquals(ALLOWANCE, issued.stream().distinct().count(), "invoice numbers stay unique under contention");
        assertEquals(ALLOWANCE, invoices.countByUsageSubscription_IdAndUsageCountedTrue(tenant.subscriptionId()),
                "usage recorded in the database matches the issued count");
        assertEquals(ALLOWANCE, jdbc.queryForObject("select count(*) from invoices where customer_id = ?",
                Integer.class, tenant.customerId()).intValue());

        assertTrue(rejections.stream().allMatch(message -> message.startsWith("IllegalArgumentException")),
                "losers are rejected with a business message, not a locking or integrity error: " + rejections);
        assertTrue(rejections.stream().allMatch(message -> message.toLowerCase().contains("allowance")
                        || message.toLowerCase().contains("plan")),
                "every rejection explains the allowance: " + rejections);
        // The losing callers were rejected, not silently accepted: one more attempt still fails.
        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(tenant.scope(), sentInvoice(tenant)));
    }

    @Test
    void invoiceSnapshotsSurviveLaterProfileAndProductChanges() {
        Tenant tenant = newTenant("snapshot", 10);
        CatalogProduct product = catalogService.save(tenant.scope(), null, new ProductCommand("SNAP-1", "Consulting",
                ProductType.SERVICE, null, "Senior hours", new BigDecimal("500.00"), new BigDecimal("18.00"), true));

        Invoice created = invoiceService.create(tenant.scope(), new CreateInvoiceCommand(tenant.customerId(),
                LocalDate.now(), LocalDate.now().plusDays(7), null, "INR", tenant.templateId(),
                List.of(new InvoiceLineInput("ignored", new BigDecimal("2"), new BigDecimal("1.00"), BigDecimal.ZERO,
                        product.getId())),
                InvoiceStatus.SENT, null, new BigDecimal("18.00")));
        Long invoiceId = created.getId();

        // Change the catalogue and the company identity after the fact.
        catalogService.save(tenant.scope(), product.getId(), new ProductCommand("SNAP-1", "Consulting deluxe",
                ProductType.SERVICE, null, "Changed", new BigDecimal("9999.00"), BigDecimal.ZERO, true));
        customerService.updateCompanyProfile(tenant.scope(), new CompanyProfileCommand("Renamed Traders", "Owner",
                tenant.email(), null, null, null, null, null, "India", null, null, null, false));

        Invoice reloaded = invoiceService.findById(tenant.scope(), invoiceId);
        assertEquals(new BigDecimal("1000.00"), reloaded.getSubtotal(), "prices are the ones in force at issue time");
        assertEquals(new BigDecimal("180.00"), reloaded.getTaxAmount());
        assertEquals(new BigDecimal("1162.00"), reloaded.getTotalAmount(), "the flat discount is applied after line taxes");
        assertEquals("Acme Traders " + tenant.suffix(), reloaded.getCompanyNameSnapshot(), "the company snapshot is preserved");
        assertEquals("Consulting", reloaded.getItems().get(0).getDescription(), "the line description is snapshotted");
        assertEquals(new BigDecimal("500.00"), reloaded.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("18.00"), reloaded.getItems().get(0).getTaxRate());
    }

    @Test
    void expiryBlocksNewInvoicesButNeverHidesHistoryFromTheOwner() {
        Tenant tenant = newTenant("expiry", 10);
        Invoice issued = invoiceService.create(tenant.scope(), sentInvoice(tenant));
        Long invoiceId = issued.getId();

        // Expire the term the way the calendar does, then run the scheduled expiry pass.
        CustomerSubscription subscription = subscriptionRepository.findById(tenant.subscriptionId()).orElseThrow();
        subscription.setEndsOn(LocalDate.now().minusDays(1));
        subscriptionRepository.saveAndFlush(subscription);
        assertEquals(1, subscriptionService.markExpired());

        assertThrows(IllegalArgumentException.class, () -> invoiceService.create(tenant.scope(), sentInvoice(tenant)));

        Invoice stillReadable = invoiceService.findById(tenant.scope(), invoiceId);
        assertEquals(issued.getInvoiceNumber(), stillReadable.getInvoiceNumber(), "history stays available to its owner");
        assertEquals(InvoiceStatus.SENT, stillReadable.getStatus(), "expiry never rewrites invoice history");
    }

    @Test
    void auditRecordsFollowTenantScopeAndNeverLeakAcrossAccounts() {
        Tenant first = newTenant("audit-a", 10);
        Tenant second = newTenant("audit-b", 10);
        Long firstCustomerId = first.customerId();
        Invoice visible = invoiceService.create(first.scope(), sentInvoice(first));

        Page<AuditRecord> ownAudit = auditService.search(first.scope(),
                new AuditFilter(null, null, "CREATE", "INVOICE", null, null, null), PageRequest.of(0, 20));
        assertTrue(ownAudit.getTotalElements() >= 1, "the tenant sees its own invoice generation event");
        ownAudit.forEach(record -> assertEquals(firstCustomerId,
                record.getCustomer() == null ? firstCustomerId : record.getCustomer().getId(),
                "every row the tenant can read belongs to that tenant"));

        Page<AuditRecord> otherAudit = auditService.search(second.scope(),
                new AuditFilter(null, null, "CREATE", "INVOICE", null, null, null), PageRequest.of(0, 20));
        assertEquals(0, otherAudit.getTotalElements(), "another tenant's activity is not visible or exportable");

        // Cross-tenant reads are refused, including the row behind a PDF or print download.
        Long foreignInvoice = visible.getId();
        assertThrows(ResourceNotFoundException.class, () -> invoiceService.findById(second.scope(), foreignInvoice));
    }

    private CreateInvoiceCommand sentInvoice(Tenant tenant) {
        return new CreateInvoiceCommand(tenant.customerId(), LocalDate.now(), LocalDate.now().plusDays(7), null,
                "INR", tenant.templateId(),
                List.of(new InvoiceLineInput("Advisory hours", new BigDecimal("1"), new BigDecimal("100.00"),
                        new BigDecimal("18.00"))),
                InvoiceStatus.SENT, null, BigDecimal.ZERO);
    }

    private Tenant newTenant(String label, int invoiceAllowance) {
        String suffix = label + "-" + System.nanoTime();
        BillTemplate template = templateService.save(null, new TemplateCommand("IT-" + suffix, "IT layout " + suffix,
                null, BillTemplateStyle.CLASSIC, "#2563EB", true, 1));
        SubscriptionPlan plan = planService.save(null, new PlanCommand("IT-" + suffix, "IT plan " + suffix, "test",
                "integration", new BigDecimal("999.00"), new BigDecimal("9990.00"), "INR", 1, invoiceAllowance, 50, 1,
                "testing", false, true, 1, Set.of(template.getId())));
        String email = suffix + "@example.test";
        Customer customer = customerService.register(new RegistrationCommand("Acme Traders " + suffix, "Owner", email,
                "9999999999", "India", "Integration!2026"));
        CustomerSubscription subscription = subscriptionService.activate(TenantScope.administrator(), customer.getId(),
                plan.getId(), "it-admin");
        return new Tenant(customer.getId(), subscription.getId(), template.getId(), suffix, email);
    }

    private record Tenant(Long customerId, Long subscriptionId, Long templateId, String suffix, String email) {
        TenantScope scope() { return TenantScope.ofCustomer(customerId); }
    }
}

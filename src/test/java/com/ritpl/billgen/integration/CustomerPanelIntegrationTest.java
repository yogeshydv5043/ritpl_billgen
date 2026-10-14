package com.ritpl.billgen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ritpl.billgen.entity.AuditHistoryPeriod;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSettings;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.InvoiceFilter;
import com.ritpl.billgen.repository.ProductFilter;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AuditFilter;
import com.ritpl.billgen.service.AuditService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import com.ritpl.billgen.service.CustomerDashboardService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.CustomerSettingsCommand;
import com.ritpl.billgen.service.CustomerSettingsService;
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
 * The customer panel end to end, against a real PostgreSQL container: an account's own settings, the
 * dashboard's live figures, invoice preview and draft editing, the audit viewing window, and the paging
 * queries that must never cross a tenant boundary. Everything asserted here is read back from the
 * database, because a figure the panel cannot prove it computed is a figure it should not show.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.demo.seed-enabled=false",
        "app.jobs.enabled=false",
        "spring.session.store-type=none",
        "spring.cache.type=none",
        "app.subscription.time-zone=UTC",
        "spring.datasource.hikari.maximum-pool-size=20"
})
class CustomerPanelIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billgen_panel")
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
    @Autowired CustomerSettingsService settingsService;
    @Autowired CustomerDashboardService dashboardService;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void accountDefaultsShapeTheNextInvoiceAndCannotRewriteItAfterwards() {
        Tenant tenant = newTenant("defaults", 10);
        settingsService.save(tenant.scope(), command("eur", "acme-", 30, 20, AuditHistoryPeriod.ONE_MONTH));

        CustomerSettings saved = settingsService.get(tenant.scope(), tenant.customerId());
        assertEquals("EUR", saved.getDefaultCurrency(), "a lower-case currency code is normalised, not rejected");
        assertEquals("ACME-", saved.getInvoiceNumberPrefix(), "and so is a lower-case prefix");
        assertEquals(30, saved.getDefaultDueDays());
        assertEquals(20, saved.getListPageSize());
        assertEquals(AuditHistoryPeriod.ONE_MONTH, saved.getAuditHistoryPeriod());

        Invoice invoice = invoiceService.create(tenant.scope(), new CreateInvoiceCommand(tenant.customerId(),
                LocalDate.now(), LocalDate.now().plusDays(saved.getDefaultDueDays()), null, saved.getDefaultCurrency(),
                tenant.templateId(), List.of(line("Advisory", "1", "100.00", "18.00")), InvoiceStatus.SENT,
                null, BigDecimal.ZERO));
        assertTrue(invoice.getInvoiceNumber().startsWith("ACME-"),
                "the account's own prefix is what the invoice carries: " + invoice.getInvoiceNumber());
        assertEquals("EUR", invoice.getCurrency());
        assertEquals(new BigDecimal("118.00"), invoice.getTotalAmount(), "tax is computed on the server");

        // A later settings change cannot rewrite the document that was already issued.
        settingsService.save(tenant.scope(), command("EUR", "other-", 30, 20, AuditHistoryPeriod.ONE_MONTH));
        Invoice reloaded = invoiceService.findById(tenant.scope(), invoice.getId());
        assertEquals(invoice.getInvoiceNumber(), reloaded.getInvoiceNumber(), "an issued number is immutable");
        assertEquals("EUR", reloaded.getCurrency());
    }

    @Test
    void previewIsExactlyTheInvoiceThatWouldBeIssuedAndUsesNoAllowance() {
        Tenant tenant = newTenant("preview", 1);
        CreateInvoiceCommand draft = new CreateInvoiceCommand(tenant.customerId(), LocalDate.now(),
                LocalDate.now().plusDays(7), "note", "INR", tenant.templateId(),
                List.of(line("Design work", "3", "500.00", "20.00")), InvoiceStatus.SENT, null,
                new BigDecimal("100.00"));

        InvoiceService.InvoicePreview preview = invoiceService.preview(tenant.scope(), draft);
        assertEquals(0L, invoiceService.page(tenant.scope(), new InvoiceFilter(null, null, null, null, null, null),
                PageRequest.of(0, 10)).getTotalElements(), "a preview writes nothing at all");
        assertEquals(0L, countedSlots(tenant), "and it uses no allowance");
        assertTrue(preview.consumesAllowance(), "an invoice that would be issued says so");
        assertEquals(0L, preview.remainingAfter(), "the plan's single slot would be used by it");
        assertEquals(new BigDecimal("1500.00"), preview.totals().subtotal(), "three units at 500.00");
        assertEquals(new BigDecimal("300.00"), preview.totals().taxAmount(), "20% of the line subtotal");
        assertEquals(new BigDecimal("1700.00"), preview.totals().totalAmount(), "then the flat discount comes off");

        // The same command, issued for real, agrees with the preview to the paisa.
        Invoice issued = invoiceService.create(tenant.scope(), draft);
        assertEquals(preview.totals().subtotal(), issued.getSubtotal());
        assertEquals(preview.totals().taxAmount(), issued.getTaxAmount());
        assertEquals(preview.totals().totalAmount(), issued.getTotalAmount());
        assertEquals(1L, countedSlots(tenant), "issuing is what takes the slot");

        // One more would be refused, for the reason the preview warned about.
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> invoiceService.create(tenant.scope(), draft));
        assertTrue(refusal.getMessage().toLowerCase().contains("allowance")
                || refusal.getMessage().toLowerCase().contains("plan"), refusal.getMessage());
        InvoiceService.InvoicePreview blocked = invoiceService.preview(tenant.scope(), draft);
        assertTrue(blocked.allowanceBlocked(), "the preview also tells the customer before they press the button");
        assertEquals(0L, blocked.remainingAfter(), "a preview never reports a negative allowance");
    }

    @Test
    void onlyADraftCanBeEditedAndAnEditRecomputesTheTotalsOnTheServer() {
        Tenant tenant = newTenant("draft-edit", 5);
        Invoice draft = invoiceService.create(tenant.scope(), new CreateInvoiceCommand(tenant.customerId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "first version", "INR", tenant.templateId(),
                List.of(line("Work", "1", "100.00", "0.00")), InvoiceStatus.DRAFT, null, BigDecimal.ZERO));
        assertEquals("DRAFT", jdbc.queryForObject("select status from invoices where id = ?", String.class,
                draft.getId()));
        assertEquals(0L, countedSlots(tenant), "a draft uses no slot");

        Invoice edited = invoiceService.updateDraft(tenant.scope(), draft.getId(), new CreateInvoiceCommand(
                tenant.customerId(), LocalDate.now(), LocalDate.now().plusDays(21), "second version", "INR",
                tenant.templateId(), List.of(line("Work", "4", "250.00", "10.00"), line("Extras", "1", "90.00", "0.00")),
                InvoiceStatus.DRAFT, null, new BigDecimal("40.00")));

        assertEquals(draft.getInvoiceNumber(), edited.getInvoiceNumber(), "the number belongs to the draft, it is not re-issued");
        assertEquals(new BigDecimal("1090.00"), edited.getSubtotal());
        assertEquals(new BigDecimal("100.00"), edited.getTaxAmount());
        assertEquals(new BigDecimal("40.00"), edited.getDiscountAmount());
        assertEquals(new BigDecimal("1150.00"), edited.getTotalAmount());
        assertEquals(2, edited.getItems().size(), "the old line is replaced, not appended to");
        assertEquals("second version", edited.getNotes());
        assertEquals(0L, countedSlots(tenant), "editing a draft still uses nothing");

        // A request that tries to move the draft to another account is refused outright.
        assertThrows(IllegalArgumentException.class, () -> invoiceService.updateDraft(tenant.scope(), draft.getId(),
                new CreateInvoiceCommand(999_999L, LocalDate.now(), LocalDate.now().plusDays(7), null, "INR",
                        tenant.templateId(), List.of(line("Work", "1", "1.00", "0.00")), InvoiceStatus.DRAFT)));

        // An issued invoice is frozen; the correction path is cancel and reissue.
        invoiceService.markSent(tenant.scope(), draft.getId());
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> invoiceService.updateDraft(tenant.scope(), draft.getId(), new CreateInvoiceCommand(
                        tenant.customerId(), LocalDate.now(), LocalDate.now().plusDays(7), null, "INR",
                        tenant.templateId(), List.of(line("Work", "1", "1.00", "0.00")), InvoiceStatus.DRAFT)));
        assertTrue(refusal.getMessage().contains("Only a draft"), refusal.getMessage());
        assertEquals(1L, countedSlots(tenant), "issuing is what consumes the slot");
        assertEquals(1L, count("select count(*) from audit_records where customer_id = ? and action = 'UPDATE'"
                + " and resource_type = 'INVOICE'", tenant.customerId()), "the edit is audited once");
    }

    @Test
    void dashboardFiguresFollowTheRowsOfThisAccountOnly() {
        Tenant tenant = newTenant("dashboard", 8);
        CustomerDashboardService.Dashboard empty = dashboardService.load(tenant.scope(), tenant.customerId());
        assertEquals("0", value(empty, "products"), "a new account has nothing to count");
        assertTrue(empty.hasPlan(), "the harness gave this account a plan");
        assertEquals(1L, empty.cards().stream().filter(card -> card.key().equals("subscription")).count(),
                "exactly one card speaks for the subscription");

        catalogService.save(tenant.scope(), null, new ProductCommand("DASH-1", "Retainer", ProductType.SERVICE, null,
                null, new BigDecimal("1000.00"), new BigDecimal("18.00"), true, "month"));
        catalogService.save(tenant.scope(), null, new ProductCommand("DASH-2", "Hidden thing", ProductType.PRODUCT,
                null, null, new BigDecimal("10.00"), BigDecimal.ZERO, false));
        invoiceService.create(tenant.scope(), new CreateInvoiceCommand(tenant.customerId(), LocalDate.now(),
                LocalDate.now().plusDays(7), null, "INR", tenant.templateId(),
                List.of(line("Work", "1", "100.00", "0.00")), InvoiceStatus.SENT, null, BigDecimal.ZERO));

        CustomerDashboardService.Dashboard loaded = dashboardService.load(tenant.scope(), tenant.customerId());
        assertEquals("2", value(loaded, "products"), "both items are counted, active or hidden");
        assertEquals("1", value(loaded, "products-active"));
        assertEquals("1", value(loaded, "products-inactive"));
        assertEquals("1", value(loaded, "invoices"));
        assertEquals("1", value(loaded, "invoices-month"));
        assertEquals("7", value(loaded, "remaining-quota"), "one of eight was used");
        assertEquals("0", value(loaded, "categories"), "no category was created, so none is claimed");
        assertEquals(1, loaded.recentInvoices().size(), "the dashboard lists the invoice it just gained");
        assertEquals(2, loaded.recentProducts().size(), "and the two catalogue rows");
        assertTrue(loaded.recentActivity().size() >= 3,
                "the create actions this account performed appear in its own recent activity");
        assertTrue(loaded.recentActivity().size() <= 10,
                "the audit list is bounded, however busy the account is");

        // A second account sees its own emptiness, and cannot ask for the first account's dashboard.
        Tenant second = newTenant("dashboard-other", 3);
        CustomerDashboardService.Dashboard foreign = dashboardService.load(second.scope(), second.customerId());
        assertEquals("0", value(foreign, "products"), "another account's catalogue never appears");
        assertEquals("0", value(foreign, "invoices"));
        assertThrows(RuntimeException.class, () -> dashboardService.load(second.scope(), tenant.customerId()),
                "and the dashboard refuses to render another account's figures");
    }

    @Test
    void auditWindowLimitsWhatTheAccountCanBrowseWithoutDeletingAnything() {
        Tenant tenant = newTenant("audit-window", 5);
        // An aged entry is inserted directly: audit rows are append-only, so nothing could have moved one.
        jdbc.update("insert into audit_records (customer_id, actor, action, resource_type, resource_name, outcome,"
                + " summary, created_at) values (?, ?, 'CREATE', 'PRODUCT', 'Backdated item', 'SUCCESS',"
                + " 'Inserted before the account shortened its window.', now() - interval '60 days')",
                tenant.customerId(), tenant.email());
        jdbc.update("insert into audit_records (customer_id, actor, action, resource_type, resource_name, outcome,"
                + " summary, created_at) values (?, ?, 'CREATE', 'PRODUCT', 'Recent item', 'SUCCESS',"
                + " 'Inside every window.', now())", tenant.customerId(), tenant.email());

        settingsService.save(tenant.scope(), command("INR", "INV-", 14, 20, AuditHistoryPeriod.THREE_MONTHS));
        assertEquals(2L, productAudits(tenant, settingsService.auditWindowStart(tenant.customerId())),
                "three months reaches back past the aged row");

        settingsService.save(tenant.scope(), command("INR", "INV-", 14, 20, AuditHistoryPeriod.DAYS_15));
        Instant windowStart = settingsService.auditWindowStart(tenant.customerId());
        Duration back = Duration.between(windowStart, Instant.now());
        assertTrue(back.toDays() >= 14 && back.toDays() <= 16,
                "the window is fifteen days wide, measured in days not rows: " + back.toDays());
        assertEquals(1L, productAudits(tenant, windowStart), "the aged entry falls outside the window the account chose");

        // Shortening the window hides history from the account; it never deletes it.
        assertEquals(2L, count("select count(*) from audit_records where customer_id = ? and resource_type = 'PRODUCT'",
                tenant.customerId()), "both rows are still in the database");
        assertTrue(auditService.search(TenantScope.administrator(),
                new AuditFilter(tenant.customerId(), null, null, "PRODUCT", null, null, null), PageRequest.of(0, 50))
                .getTotalElements() >= 2, "the administrative view is not bounded by an account's window");
    }

    @Test
    void pagedQueriesCannotBeWidenedIntoAnotherAccount() {
        Tenant tenant = newTenant("isolation", 5);
        catalogService.save(tenant.scope(), null, new ProductCommand("ISO-1", "Private item", ProductType.SERVICE,
                null, null, new BigDecimal("5.00"), BigDecimal.ZERO, true));
        Invoice own = invoiceService.create(tenant.scope(), new CreateInvoiceCommand(tenant.customerId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "secret note", "INR", tenant.templateId(),
                List.of(line("Work", "1", "100.00", "0.00")), InvoiceStatus.SENT, null, BigDecimal.ZERO));

        Tenant second = newTenant("isolation-other", 5);
        // A filter naming the first account's data, applied in the second account's scope, finds nothing.
        assertEquals(0L, catalogService.page(second.scope(), new ProductFilter("Private item", null, null, null),
                PageRequest.of(0, 20, Sort.by("name"))).getTotalElements());
        assertEquals(0L, invoiceService.page(second.scope(), new InvoiceFilter("secret note", null, null, null, null, null),
                PageRequest.of(0, 20, Sort.by("issueDate"))).getTotalElements());
        assertEquals(0L, catalogService.countAll(second.scope()));
        assertEquals(1L, catalogService.page(tenant.scope(), new ProductFilter(null, null, null, null),
                PageRequest.of(0, 20, Sort.by("name"))).getTotalElements(), "the owner still sees its own row");
        assertThrows(ResourceNotFoundException.class, () -> invoiceService.findById(second.scope(), own.getId()),
                "and a foreign id is reported as missing, not as forbidden");
    }

    @Test
    void aPasswordChangeIsAuditedWithoutTheSecretAndNotifiedInInbox() {
        Tenant tenant = newTenant("security", 3);
        AppUserPrincipal principal = new AppUserPrincipal(
                users.findByEmailIgnoreCase(tenant.email()).orElseThrow(), tenant.customerId());

        customerService.changeOwnPassword(principal, "Integration!2026", "Rotated!2026");

        assertEquals(1L, count("select count(*) from audit_records where customer_id = ? and action = 'PASSWORD_CHANGE'",
                tenant.customerId()), "the change is recorded once");
        String stored = jdbc.queryForObject("select coalesce(before_values, '') || ' ' || coalesce(after_values, '')"
                + " from audit_records where customer_id = ? and action = 'PASSWORD_CHANGE'", String.class,
                tenant.customerId());
        assertTrue(!stored.contains("Rotated!2026") && !stored.contains("Integration!2026"),
                "no password text reaches the audit trail: " + stored);
        assertEquals(1L, count("select count(*) from subscription_notification_logs where customer_id = ?"
                + " and notification_type = 'SECURITY'", tenant.customerId()), "the account is told in its own inbox");

        // The new password works; the old one does not.
        assertThrows(IllegalArgumentException.class,
                () -> customerService.changeOwnPassword(principal, "Integration!2026", "Again!2026"));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    /** Slots consumed this term - the count the quota banner and the guard both use. */
    private long countedSlots(Tenant tenant) {
        return count("select count(*) from invoices where customer_id = ? and usage_counted", tenant.customerId());
    }

    private long productAudits(Tenant tenant, Instant from) {
        return auditService.search(tenant.scope(),
                new AuditFilter(null, null, null, "PRODUCT", null, from, null), PageRequest.of(0, 50)).getTotalElements();
    }

    private long count(String sql, Long customerId) {
        Long value = jdbc.queryForObject(sql, Long.class, customerId);
        return value == null ? 0L : value;
    }

    private static String value(CustomerDashboardService.Dashboard dashboard, String key) {
        return dashboard.cards().stream().filter(card -> card.key().equals(key)).findFirst()
                .map(CustomerDashboardService.Card::value).orElse("<missing card: " + key + ">");
    }

    private static InvoiceLineInput line(String description, String quantity, String price, String tax) {
        return new InvoiceLineInput(description, new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(tax));
    }

    /** The settings command, spelled once so each test only varies what it asserts about. */
    private CustomerSettingsCommand command(String currency, String prefix, int dueDays, int pageSize,
            AuditHistoryPeriod period) {
        return new CustomerSettingsCommand(null, null, currency, prefix, dueDays, null, null, null, null, "hour", null,
                pageSize, period, true, true, true, true, 90);
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

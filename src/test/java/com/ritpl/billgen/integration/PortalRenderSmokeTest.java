package com.ritpl.billgen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.CustomerSettings;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.entity.AuditHistoryPeriod;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import com.ritpl.billgen.service.CustomerAlertService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.CustomerSettingsCommand;
import com.ritpl.billgen.service.CustomerSettingsService;
import com.ritpl.billgen.service.InvoiceLineInput;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.PlanCommand;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.ProductCommand;
import com.ritpl.billgen.service.RegistrationCommand;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.TemplateCommand;
import com.ritpl.billgen.service.TemplateService;

/**
 * The screens themselves. Every other test in this project stops at the service layer, which leaves the
 * widest class of defect unproven: a page that reads a property the model never carried, a template that
 * dereferences a relation the query did not fetch, a form whose field names do not match its object. None
 * of that can be found by reading code, so this test drives the real filter chain - sign-in, CSRF,
 * session, role rules, Spring MVC binding, Thymeleaf rendering - against a real PostgreSQL, and fails with
 * the exception each broken page actually threw.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.demo.seed-enabled=false",
        "app.jobs.enabled=false",
        "spring.cache.type=none",
        // Boot 3 infers the session store from the classpath, and spring-session-data-redis is on it, so
        // a web test has to take the session store out of the picture: without this the sign-in POST
        // tries to write the session into a Redis that no test is running.
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "app.subscription.time-zone=UTC",
        "spring.datasource.hikari.maximum-pool-size=20"
})
@AutoConfigureMockMvc
class PortalRenderSmokeTest {

    private static final String PASSWORD = "Render!2026x";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billgen_render")
            .withUsername("billgen")
            .withPassword("billgen_it_only");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TemplateService templateService;
    @Autowired PlanService planService;
    @Autowired CustomerService customerService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired ProductCategoryService categories;
    @Autowired CatalogService catalog;
    @Autowired InvoiceService invoices;
    @Autowired CustomerSettingsService settings;
    @Autowired CustomerAlertService alerts;

    /**
     * Every customer-facing page, including the paged, filtered and sorted variants, the print and PDF
     * renderers and the export writers. All routes are attempted before any assertion, so one CI run names
     * every broken page instead of the first one only.
     */
    @Test
    void everyCustomerScreenRendersFromTheAccountsOwnRows() throws Exception {
        Fixture fixture = provision("render");
        Session session = signIn(fixture.email());

        List<String> routes = new ArrayList<>(List.of(
                "/portal", "/portal/plan", "/portal/settings", "/portal/account", "/portal/company",
                "/portal/categories", "/portal/categories?q=Ret&page=0&sort=name&dir=asc",
                "/portal/categories?q=zzz-nothing&sort=updated&dir=desc",
                "/portal/products", "/portal/products/new",
                "/portal/products?q=Re&status=active&type=SERVICE&page=0&sort=price&dir=desc",
                "/portal/products?q=zzz-nothing&status=hidden",
                "/portal/invoices", "/portal/invoices/new",
                "/portal/invoices?status=SENT&page=0&sort=price&dir=desc",
                "/portal/invoices?status=overdue",
                "/portal/invoices?from=" + LocalDate.now().minusDays(2) + "&to=" + LocalDate.now(),
                "/portal/templates", "/portal/templates/new", "/portal/notifications",
                "/portal/audit", "/portal/audit?outcome=SUCCESS&resourceType=INVOICE&actor=it-admin",
                                "/portal/exports/products", "/portal/exports/products?q=Re", "/portal/exports/invoices",
                "/portal/exports/invoices/list", "/portal/exports/audit"));
        routes.add("/portal/invoices/" + fixture.draftId());
        routes.add("/portal/invoices/" + fixture.draftId() + "/edit");
        routes.add("/portal/invoices/" + fixture.issuedId());
        routes.add("/portal/invoices/" + fixture.issuedId() + "/print");
        routes.add("/portal/invoices/" + fixture.issuedId() + "/invoice.pdf");
        routes.add("/portal/audit/" + fixture.auditId());
        routes.add("/portal/products/" + fixture.productId() + "/edit");

        List<String> broken = new ArrayList<>();
        for (String route : routes) {
            try {
                checkRoute(route, session, broken);
            } catch (Throwable failure) {
                // MockMvc rethrows whatever the page threw, and an unrenderable page is exactly the thing
                // this test exists to enumerate - so it is recorded, not propagated onto the other routes.
                broken.add(route + " -> threw " + describe(failure));
            }
        }
        // A page must absorb the customer's own bad input: the list redraws itself with the reason,
        // and the export refuses with a readable 400 instead of a stack-trace page.
        MvcResult inverted = mockMvc.perform(get("/portal/invoices?from=2026-01-10&to=2026-01-01")
                .session(session.httpSession)).andReturn();
        assertEquals(200, inverted.getResponse().getStatus(), "an inverted date range redraws the list" + explain(inverted));
        assertTrue(inverted.getResponse().getContentAsString().contains("must not be before"),
                "and says which half was wrong");
        MvcResult invertedAudit = mockMvc.perform(get("/portal/audit?from=2026-01-10&until=2026-01-01")
                .session(session.httpSession)).andReturn();
        assertEquals(200, invertedAudit.getResponse().getStatus(), "the audit page does the same" + explain(invertedAudit));
        MvcResult refusedExport = mockMvc.perform(get("/portal/exports/invoices/list?from=2026-01-10&until=2026-01-01")
                .session(session.httpSession)).andReturn();
        assertEquals(400, refusedExport.getResponse().getStatus(), "an export with a bad range is refused as a"
                + " bad request, not as a server fault" + explain(refusedExport));
        assertTrue(refusedExport.getResponse().getContentAsString().contains("End date must not be before start date"),
                "and the refusal repeats the rule the customer broke");

        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }

    /**
     * One route, one verdict. A redirect counts as an answer the page chose to give; anything other than
     * a rendered page or a redirect is recorded with the reason, so the run reports every broken screen.
     */
    private void checkRoute(String route, Session session, List<String> broken) throws Exception {
        MvcResult result = mockMvc.perform(get(route).session(session.httpSession)).andReturn();
        int status = result.getResponse().getStatus();
        boolean redirected = status >= 300 && status < 400;
        if (status != 200 && !redirected) broken.add(route + " -> HTTP " + status + explain(result));
        if (status == 200 && route.endsWith(".pdf") && result.getResponse().getContentAsByteArray().length == 0) {
            broken.add(route + " -> a PDF that contains nothing");
        }
        if (status == 200 && route.contains("/exports/")) {
            byte[] body = result.getResponse().getContentAsByteArray();
            if (body.length < 1024) broken.add(route + " -> an export of " + body.length + " bytes");
        }
    }

    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = String.valueOf(root.getMessage());
        return root.getClass().getSimpleName() + ": "
                + (message.length() > 400 ? message.substring(0, 400) + "..." : message);
    }


    /**
     * The pages a company reaches before it has an account. They sit outside the panel and outside this
     * test's session, and they are the ones a customer judges the product by, so each is asked for here:
     * the sign-up form must render, and a refused application must come back with its own complaints
     * beside the fields they belong to rather than as a fresh empty page.
     */
    @Test
    void thePublicPagesRenderAndASignUpRefusalComesBackWithItsFields() throws Exception {
        for (String route : List.of("/login", "/register", "/plans")) {
            MvcResult page = mockMvc.perform(get(route)).andReturn();
            assertEquals(200, page.getResponse().getStatus(), route + " is open to anyone" + explain(page));
        }
        MvcResult form = mockMvc.perform(get("/register")).andReturn();
        String html = form.getResponse().getContentAsString();
        assertTrue(html.contains("id=\"companyName\"") && html.contains("for=\"password\""),
                "the sign-up form has to carry the fields it is bound to" + explain(form));
        // The styling these pages share lives in classes both sheets define; a page that quietly loses
        // its shell renders as bare HTML, which no test but this one would notice.
        assertTrue(html.contains("auth-shell") && html.contains("auth-card"),
                "sign-up has to be dressed like sign-in, or the two halves look like different products");

        // The token and the session it was issued to travel together, so both are taken from one page
        // load; a token scraped from a second request belongs to a session this POST will not present.
        String token = csrfTokenFrom(html);
        MockHttpSession applicant = (MockHttpSession) form.getRequest().getSession(false);
        MvcResult refused = mockMvc.perform(post("/register").session(applicant).param("_csrf", token)
                .param("companyName", "Ravi Textiles").param("contactName", "")
                .param("email", "not-an-email").param("password", "short")).andReturn();
        assertEquals(200, refused.getResponse().getStatus(),
                "a refused application redraws the form instead of dropping it" + explain(refused));
        String redrawn = refused.getResponse().getContentAsString();
        assertTrue(redrawn.contains("Ravi Textiles"), "and hands back what the applicant already typed");
        assertTrue(redrawn.contains("field-error"), "and marks the fields that were refused");

        MvcResult signedUp = mockMvc.perform(post("/register").session(applicant).param("_csrf", token)
                .param("companyName", "Ravi Textiles").param("contactName", "Ravi Kumar")
                .param("email", "accounts@ravitextiles.example").param("country", "India")
                .param("password", "Render!2026x")).andReturn();
        assertEquals(302, signedUp.getResponse().getStatus(), "a valid application is accepted" + explain(signedUp));
        assertEquals("/login", signedUp.getResponse().getRedirectedUrl(), "and the customer is sent to sign in");
        assertTrue(jdbc.queryForObject("select count(*) from customers where email = ?", Long.class,
                "accounts@ravitextiles.example") == 1L, "and the account exists before the customer signs in");
    }

    /** The form pages have to bind what they render, or the customer's own edits never reach the database. */
    @Test
    void aCustomerCanSaveSettingsAddACatalogueItemAndIssueAnInvoiceThroughTheForms() throws Exception {
        Fixture fixture = provision("forms");
        Session session = signIn(fixture.email());
        String csrf = csrf(session);

        MvcResult saved = mockMvc.perform(post("/portal/settings").session(session.httpSession)
                .param("_csrf", csrf)
                .param("defaultCurrency", "usd")
                .param("invoiceNumberPrefix", "form-")
                .param("defaultDueDays", "21")
                .param("defaultTaxRate", "12.50")
                .param("defaultPaymentTerms", "Half now, half on delivery.")
                .param("defaultNotes", "Rendered from the settings form.")
                .param("defaultFooter", "Thank you for your business.")
                .param("defaultUnit", "day")
                .param("listPageSize", "50")
                .param("auditHistoryPeriod", "THREE_MONTHS")
                .param("exportRangeDays", "180")
                .param("notifyExpiryReminders", "true")
                .param("notifyQuotaAlerts", "true")
                .param("notifyPaymentUpdates", "true")
                .param("notifySecurityAlerts", "true")
                .param("defaultTemplateId", String.valueOf(fixture.templateId()))).andReturn();
        assertEquals(302, saved.getResponse().getStatus(),
                "the settings form redirects on success: " + explain(saved));

        CustomerSettings stored = settings.get(TenantScope.ofCustomer(fixture.customerId()), fixture.customerId());
        assertEquals("USD", stored.getDefaultCurrency(), "the form's lower-case code is normalised on the way in");
        assertEquals("FORM-", stored.getInvoiceNumberPrefix());
        assertEquals(21, stored.getDefaultDueDays());
        assertEquals(50, stored.getListPageSize());
        assertEquals(AuditHistoryPeriod.THREE_MONTHS, stored.getAuditHistoryPeriod());

        MvcResult product = mockMvc.perform(post("/portal/products").session(session.httpSession)
                .param("_csrf", csrf)
                .param("sku", "FORM-1")
                .param("name", "Rendered retainer")
                .param("productType", "SERVICE")
                .param("unitPrice", "750.00")
                .param("taxRate", "18.00")
                .param("unit", "day")
                .param("description", "Created through the web form.")
                .param("active", "true")).andReturn();
        assertEquals(302, product.getResponse().getStatus(),
                "the product form redirects on success: " + explain(product));
        assertEquals(3L, catalog.countAll(TenantScope.ofCustomer(fixture.customerId())),
                "the two seeded items plus this one - the same figure the page shows");

        // Issue an invoice from the form, with the token the rendered page carried.
        String form = mockMvc.perform(get("/portal/invoices/new").session(session.httpSession)).andReturn()
                .getResponse().getContentAsString();
        String token = firstMatch(form, "name=\"submitToken\"[^>]*value=\"([^\"]+)\"");
        assertNotNull(token, "the issue form carries a one-time token");
        MvcResult issued = mockMvc.perform(post("/portal/invoices").session(session.httpSession)
                .param("_csrf", csrf)
                .param("submitToken", token)
                .param("templateId", String.valueOf(fixture.templateId()))
                .param("issueDate", LocalDate.now().toString())
                .param("dueDate", LocalDate.now().plusDays(21).toString())
                .param("currency", "USD")
                .param("initialStatus", "SENT")
                .param("discountAmount", "10.00")
                .param("paymentTerms", "Net 21 from the form.")
                .param("notes", "Issued through the web form")
                .param("lines[0].description", "Design day")
                .param("lines[0].quantity", "2")
                .param("lines[0].unitPrice", "500.00")
                .param("lines[0].taxRate", "20.00")).andReturn();
        assertEquals(302, issued.getResponse().getStatus(), "the POST should redirect to the invoice: "
                + issued.getResponse().getErrorMessage() + explain(issued));

        List<Long> created = jdbc.query("select id from invoices where customer_id = ? and status = 'SENT' order by id",
                (rs, i) -> rs.getLong(1), fixture.customerId());
        assertEquals(2L, created.size(), "one seeded invoice plus the one this form issued");
        Invoice fromForm = invoices.findById(TenantScope.ofCustomer(fixture.customerId()), created.get(1));
        assertTrue(fromForm.getInvoiceNumber().startsWith("FORM-"),
                "the prefix the account saved is the prefix its next invoice carries: " + fromForm.getInvoiceNumber());
        assertEquals(new BigDecimal("1190.00"), fromForm.getTotalAmount(),
                "2 x 500.00 plus 20% tax (1,000.00 + 200.00), less the 10.00 discount, all on the server");
        assertEquals("Net 21 from the form.", fromForm.getPaymentTerms(), "the terms typed on the invoice win");

        // The same token replayed - a double-click, or a browser resending a POST on back-navigation -
        // must not quietly create a second document.
        MvcResult replay = mockMvc.perform(post("/portal/invoices").session(session.httpSession)
                .param("_csrf", csrf)
                .param("submitToken", token)
                .param("templateId", String.valueOf(fixture.templateId()))
                .param("issueDate", LocalDate.now().toString())
                .param("dueDate", LocalDate.now().plusDays(21).toString())
                .param("currency", "USD")
                .param("initialStatus", "SENT")
                .param("lines[0].description", "Design day")
                .param("lines[0].quantity", "1")
                .param("lines[0].unitPrice", "1.00")
                .param("lines[0].taxRate", "0.00")).andReturn();
        assertEquals(200, replay.getResponse().getStatus(), "a spent token redraws the form instead of issuing");
        assertTrue(replay.getResponse().getContentAsString().contains("already been submitted"),
                "and says why, in words the customer can act on");
        assertEquals(3L, count("select count(*) from invoices where customer_id = ?", fixture.customerId()),
                "two seeded invoices plus the one this form issued - and no fourth");

        // Marking a notice read is a state change the inbox has to show afterwards.
        Long noticeId = jdbc.queryForObject(
                "select id from subscription_notification_logs where customer_id = ? order by id desc limit 1",
                Long.class, fixture.customerId());
        mockMvc.perform(post("/portal/notifications/" + noticeId + "/read").session(session.httpSession)
                .param("_csrf", csrf)).andExpect(result -> assertEquals(302, result.getResponse().getStatus()));
        assertEquals("READ", jdbc.queryForObject("select status from subscription_notification_logs where id = ?",
                String.class, noticeId), "the inbox row moved");
    }

    /** Role rules are asserted on the way out, because hiding a link is not a security boundary. */
    @Test
    void anAnonymousVisitorIsRedirectedAndACustomerIsNotServedAdminScreens() throws Exception {
        Fixture fixture = provision("roles");
        Session session = signIn(fixture.email());

        MvcResult anonymous = mockMvc.perform(get("/portal")).andReturn();
        assertTrue(anonymous.getResponse().getStatus() == 302 || anonymous.getResponse().getStatus() == 401,
                "an unauthenticated customer page is refused, not rendered: " + anonymous.getResponse().getStatus());

        for (String route : List.of("/admin/audit", "/admin/exports/audit", "/admin/exports/products", "/dashboard")) {
            MvcResult result = mockMvc.perform(get(route).session(session.httpSession)).andReturn();
            String forward = result.getResponse().getForwardedUrl();
            boolean refused = result.getResponse().getStatus() == 403 || result.getResponse().getStatus() == 302
                    || (forward != null && forward.contains("403"));
            assertTrue(refused, route + " answered HTTP " + result.getResponse().getStatus()
                    + " (forward " + forward + ") to a customer principal: " + result.getResponse().getContentAsString());
        }
    }

    // ------------------------------------------------------------------ fixtures and helpers

    /** A customer with data in every module the panel reads, so no page renders against an empty tenant. */
    private Fixture provision(String label) {
        String suffix = label + "-" + System.nanoTime();
        String email = suffix + "@example.test";
        BillTemplate template = templateService.save(null, new TemplateCommand("RS-" + suffix, "RS layout " + suffix,
                null, BillTemplateStyle.CLASSIC, "#2563EB", true, 1));
        com.ritpl.billgen.entity.SubscriptionPlan plan = planService.save(null,
                new PlanCommand("RS-" + suffix, "RS plan " + suffix, "render", "smoke", new BigDecimal("499.00"),
                        new BigDecimal("4990.00"), "INR", 1, 8, 50, 2, "rendering", false, true, 1,
                        Set.of(template.getId())));
        Customer customer = customerService.register(new RegistrationCommand("Render Traders " + suffix, "Owner",
                email, "9888800000", "India", PASSWORD));
        TenantScope scope = TenantScope.ofCustomer(customer.getId());
        subscriptionService.activate(TenantScope.administrator(), customer.getId(), plan.getId(), "it-admin");
        settings.save(scope, new CustomerSettingsCommand(null, null, "INR", "RS-", 14, null, null, null, null, "hour",
                null, 20, AuditHistoryPeriod.ONE_YEAR, true, true, true, true, 90));
        categories.save(scope, null, "Render category", "Seeded for the category pages.");
        Long productId = catalog.save(scope, null, new ProductCommand("RS-SKU-1", "Rendered retainer",
                ProductType.SERVICE, null, null, new BigDecimal("1200.00"), new BigDecimal("18.00"), true, "month")).getId();
        catalog.save(scope, null, new ProductCommand("RS-SKU-2", "Hidden widget", ProductType.PRODUCT, null, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, false));
        Invoice draft = invoices.create(scope, command(customer.getId(), template.getId(), "Draft for editing",
                InvoiceStatus.DRAFT));
        Invoice issued = invoices.create(scope, command(customer.getId(), template.getId(), "Issued for rendering",
                InvoiceStatus.SENT));
        alerts.securityAlert(customer.getId(), "A password change was requested for this account.");

        Long auditId = jdbc.queryForObject("select max(id) from audit_records where customer_id = ?", Long.class,
                customer.getId());
        return new Fixture(customer.getId(), template.getId(), draft.getId(), issued.getId(), productId, auditId, email);
    }

    private static CreateInvoiceCommand command(long customerId, long templateId, String note, InvoiceStatus status) {
        return new CreateInvoiceCommand(customerId, LocalDate.now(), LocalDate.now().plusDays(7), note, "INR",
                templateId, List.of(new InvoiceLineInput("Advisory", new BigDecimal("1"), new BigDecimal("100.00"),
                        new BigDecimal("18.00"))), status, null, BigDecimal.ZERO);
    }

    private Session signIn(String email) throws Exception {
        MvcResult page = mockMvc.perform(get("/login")).andReturn();
        String token = csrfTokenFrom(page.getResponse().getContentAsString());
        assertNotNull(token, "the sign-in page carries a CSRF token");
        MvcResult login = mockMvc.perform(post("/login").session((MockHttpSession) page.getRequest().getSession(false))
                .param("username", email).param("password", PASSWORD).param("_csrf", token)).andReturn();
        assertEquals(302, login.getResponse().getStatus(),
                "the seeded customer must be able to sign in: " + login.getResponse().getRedirectedUrl()
                        + " " + login.getResponse().getErrorMessage());
        return new Session((MockHttpSession) login.getRequest().getSession(false));
    }

    private String csrf(Session session) throws Exception {
        return csrfTokenFrom(mockMvc.perform(get("/portal/settings").session(session.httpSession)).andReturn()
                .getResponse().getContentAsString());
    }

    private static String firstMatch(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static String csrfTokenFrom(String html) {
        Matcher m = CSRF.matcher(html);
        if (m.find()) return m.group(1) != null ? m.group(1) : m.group(2);
        return null;
    }

    /** The status alone hides the reason; the resolved exception and its first application frame do not. */
    private static String explain(MvcResult result) {
        Exception thrown = result.getResolvedException();
        if (thrown == null) return "";
        StringBuilder text = new StringBuilder(" :: ").append(thrown.getClass().getSimpleName());
        if (thrown.getMessage() != null) text.append(": ").append(firstLine(thrown.getMessage()));
        Throwable root = thrown;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        if (root != thrown) {
            text.append(" <- ").append(root.getClass().getSimpleName()).append(": ")
                    .append(firstLine(String.valueOf(root.getMessage())));
        }
        for (StackTraceElement frame : root.getStackTrace()) {
            if (frame.getClassName().startsWith("com.ritpl.billgen")) {
                text.append(" @ ").append(frame.getClassName().replaceAll(".*\\.", ""))
                        .append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
                break;
            }
        }
        return text.toString();
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        String line = newline < 0 ? value : value.substring(0, newline);
        return line.length() > 300 ? line.substring(0, 300) + "..." : line;
    }

    private long count(String sql, long customerId) {
        Long value = jdbc.queryForObject(sql, Long.class, customerId);
        return value == null ? 0L : value;
    }

    private record Session(MockHttpSession httpSession) { }

    private record Fixture(long customerId, long templateId, long draftId, long issuedId, long productId,
            Long auditId, String email) { }
}

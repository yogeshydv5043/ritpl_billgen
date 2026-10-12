package com.ritpl.billgen.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductCategory;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.entity.UserRole;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.BillTemplateRepository;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import com.ritpl.billgen.service.InvoiceLineInput;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.PlanCommand;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.ProductCommand;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.SubscriptionService;
import com.ritpl.billgen.service.TemplateCommand;
import com.ritpl.billgen.service.TemplateService;

/**
 * Seeds the local development estate: the base bill templates, the plans that bundle them, and a
 * couple of customer accounts with their own products, categories, plan and invoices. Every step
 * is idempotent, and the seed lock keeps two application instances from seeding at the same time.
 */
@Component
public class DemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final boolean demoEnabled;
    private final String demoPassword;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final BillTemplateRepository templateRepo;
    private final CatalogProductRepository products;
    private final ProductCategoryRepository categories;
    private final SubscriptionPlanRepository plans;
    private final InvoiceRepository invoices;
    private final PasswordEncoder passwordEncoder;
    private final InvoiceService invoiceService;
    private final TemplateService templateService;
    private final PlanService planService;
    private final CatalogService catalogService;
    private final ProductCategoryService categoryService;
    private final SubscriptionService subscriptionService;
    private final JdbcTemplate jdbc;

    public DemoDataInitializer(@Value("${app.demo.seed-enabled:true}") boolean demoEnabled,
            @Value("${app.demo.password:ChangeMe123!}") String demoPassword,
            @Value("${app.bootstrap.admin-email:}") String bootstrapEmail,
            @Value("${app.bootstrap.admin-password:}") String bootstrapPassword,
            AppUserRepository users, CustomerRepository customers, BillTemplateRepository templateRepo,
            CatalogProductRepository products, ProductCategoryRepository categories, SubscriptionPlanRepository plans,
            InvoiceRepository invoices, PasswordEncoder passwordEncoder, InvoiceService invoiceService,
            TemplateService templateService, PlanService planService, CatalogService catalogService,
            ProductCategoryService categoryService, SubscriptionService subscriptionService, JdbcTemplate jdbc) {
        this.demoEnabled = demoEnabled; this.demoPassword = demoPassword; this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword; this.users = users; this.customers = customers;
        this.templateRepo = templateRepo; this.products = products; this.categories = categories;
        this.plans = plans; this.invoices = invoices; this.passwordEncoder = passwordEncoder;
        this.invoiceService = invoiceService; this.templateService = templateService; this.planService = planService;
        this.catalogService = catalogService; this.categoryService = categoryService;
        this.subscriptionService = subscriptionService; this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        acquireSeedLock();
        seedTemplates();
        if (demoEnabled) {
            seedPlans();
            Customer northstar = ensureCustomer("hello@northstar.example", "Northstar Studio", "Maya Kapoor",
                    "Gurugram", "Haryana", "122002", "06AABCN1234A1Z5");
            Customer maple = ensureCustomer("accounts@mapleworks.example", "Maple & Co.", "Arjun Mehta",
                    "New Delhi", "Delhi", "110001", "07AAFCM4821E1Z2");
            ensureUser("admin@billgen.local", demoPassword, UserRole.ADMIN, null);
            ensureUser("customer@demo.local", demoPassword, UserRole.CUSTOMER, northstar);
            ensureUser("customer2@demo.local", demoPassword, UserRole.CUSTOMER, maple);
            assignPlan(northstar, "STARTER");
            assignPlan(maple, "GROWTH");
            seedAccountData(northstar);
            seedAccountData(maple);
            log.info("BillGen demo data is enabled. Disable APP_DEMO_SEED_ENABLED outside local development.");
        }
        if (bootstrapEmail != null && !bootstrapEmail.isBlank() && bootstrapPassword != null && !bootstrapPassword.isBlank()) {
            ensureUser(bootstrapEmail.trim().toLowerCase(java.util.Locale.ROOT), bootstrapPassword, UserRole.ADMIN, null);
        } else if (!demoEnabled) {
            log.warn("No login accounts were seeded. Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD to create the first administrator.");
        }
    }

    private void acquireSeedLock() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, 0x42494c4c); // BILL
                statement.setInt(2, 0x47454e);   // GEN
                statement.execute();
            }
            return null;
        });
    }

    /** The three base layouts. Plans and invoices are built on top of these. */
    private void seedTemplates() {
        seedTemplate("CLASSIC", "Classic", "Balanced and professional.", BillTemplateStyle.CLASSIC, 10);
        seedTemplate("MODERN", "Modern", "A bold contemporary accent.", BillTemplateStyle.MODERN, 20);
        seedTemplate("MINIMAL", "Minimal", "A clean, compact statement.", BillTemplateStyle.MINIMAL, 30);
    }

    private void seedTemplate(String code, String name, String description, BillTemplateStyle style, int sortOrder) {
        if (templateRepo.findByCodeIgnoreCase(code).isPresent()) return;
        templateService.save(null, new TemplateCommand(code, name, description, style, null, true, sortOrder));
    }

    private void seedPlans() {
        List<PlanCommand> examples = List.of(
                new PlanCommand("STARTER", "Starter", "For your first 50 invoices", "Simple invoicing for solo businesses.",
                        new BigDecimal("0.00"), new BigDecimal("0.00"), "INR", 25, 50, 25, 1,
                        "Unlimited drafts\nClassic and minimal templates\nYour own product catalogue", false, true, 10,
                        templateIds("CLASSIC", "MINIMAL")),
                new PlanCommand("GROWTH", "Growth", "Room to build momentum", "More customers and smarter workflows for growing teams.",
                        new BigDecimal("799.00"), new BigDecimal("7990.00"), "INR", 500, 1000, 500, 5,
                        "Everything in Starter\nEvery bill template\nProduct and category management\nPriority support\nExcel exports", true, true, 20,
                        templateIds("CLASSIC", "MODERN", "MINIMAL")),
                new PlanCommand("SCALE", "Scale", "Billing at full speed", "A richer control plane for finance teams and established businesses.",
                        new BigDecimal("2499.00"), new BigDecimal("24990.00"), "INR", 5000, 10000, 5000, 20,
                        "Everything in Growth\nCustom invoice templates\nAdvanced reporting\nDedicated onboarding", false, true, 30,
                        templateIds("CLASSIC", "MODERN", "MINIMAL"))
        );
        for (PlanCommand plan : examples) {
            SubscriptionPlan existing = planService.findAll().stream()
                    .filter(candidate -> candidate.getCode().equalsIgnoreCase(plan.code()))
                    .findFirst().orElse(null);
            if (existing == null) {
                planService.save(null, plan);
            } else if (existing.getTemplates().isEmpty()) {
                // Upgrade old seeded plans after V2 so existing demo accounts receive their
                // expected template entitlements without overwriting administrator choices.
                planService.setTemplates(existing.getId(), plan.templateIds());
            }
        }
    }

    private Set<Long> templateIds(String... codes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String code : codes) {
            templateRepo.findByCodeIgnoreCase(code).map(BillTemplate::getId).ifPresent(ids::add);
        }
        return ids;
    }

    private void assignPlan(Customer customer, String planCode) {
        SubscriptionPlan plan = plans.findAll().stream()
                .filter(candidate -> candidate.getCode().equalsIgnoreCase(planCode)).findFirst().orElse(null);
        if (plan == null) return;
        if (subscriptionService.findActiveSubscription(TenantScope.ofCustomer(customer.getId()), customer.getId()).isPresent()) return;
        subscriptionService.activate(TenantScope.administrator(), customer.getId(), plan.getId(), "demo-seed");
    }

    /** Gives an account its own categories, products and a couple of invoices. */
    private void seedAccountData(Customer customer) {
        TenantScope scope = TenantScope.ofCustomer(customer.getId());
        ProductCategory software = ensureCategory(scope, "Software");
        ProductCategory services = ensureCategory(scope, "Professional services");
        ensureProduct(scope, "PRD-LIC-01", "Annual software licence", ProductType.PRODUCT, software,
                "Annual named-user software licence.", new BigDecimal("12000.00"), new BigDecimal("18.00"));
        ensureProduct(scope, "SVC-CONSULT", "Consulting services", ProductType.SERVICE, services,
                "Strategy and consulting, billed by the hour.", new BigDecimal("3500.00"), new BigDecimal("18.00"));
        if (invoices.countByCustomer_Id(customer.getId()) > 0) return;
        String templateCode = subscriptionService.accessibleTemplates(scope).stream()
                .findFirst().map(BillTemplate::getCode).orElse("CLASSIC");
        invoiceService.create(scope, new CreateInvoiceCommand(customer.getId(),
                LocalDate.now().minusDays(21), LocalDate.now().minusDays(7),
                "Thank you for your business. Payment is due within 14 days.", "INR", templateCodeOf(templateCode),
                List.of(new InvoiceLineInput("Product design workshop", BigDecimal.ONE, new BigDecimal("48000.00"), new BigDecimal("18.00")),
                        new InvoiceLineInput("Research and discovery", new BigDecimal("8"), new BigDecimal("3500.00"), new BigDecimal("18.00"))),
                InvoiceStatus.SENT));
        var paidInvoice = invoiceService.create(scope, new CreateInvoiceCommand(customer.getId(),
                LocalDate.now().minusDays(3), LocalDate.now().plusDays(11),
                "Payment received. We appreciate your business.", "INR", templateCodeOf(templateCode),
                List.of(new InvoiceLineInput("Brand identity package", BigDecimal.ONE, new BigDecimal("32000.00"), new BigDecimal("18.00"))),
                InvoiceStatus.SENT));
        invoiceService.markPaid(scope, paidInvoice.getId());
    }

    private Long templateCodeOf(String code) { return templateRepo.findByCodeIgnoreCase(code).map(BillTemplate::getId).orElse(null); }

    private ProductCategory ensureCategory(TenantScope scope, String name) {
        return categories.findByCustomer_IdOrderByNameAsc(scope.requireCustomerId()).stream()
                .filter(category -> category.getName().equalsIgnoreCase(name)).findFirst()
                .orElseGet(() -> categoryService.save(scope, null, name, null));
    }

    private void ensureProduct(TenantScope scope, String sku, String name, ProductType type, ProductCategory category,
            String description, BigDecimal price, BigDecimal tax) {
        if (products.existsBySkuIgnoreCaseAndCustomer_Id(sku, scope.requireCustomerId())) return;
        catalogService.save(scope, null, new ProductCommand(sku, name, type, category.getId(), description, price, tax, true));
    }

    private Customer ensureCustomer(String email, String company, String contact, String city, String state, String postal, String gstin) {
        return customers.findByEmailIgnoreCase(email).orElseGet(() -> {
            Customer c = new Customer(company, contact, email);
            c.setPhone("+91 98765 43210"); c.setBillingAddress("42 Horizon Plaza");
            c.setCity(city); c.setState(state); c.setPostalCode(postal); c.setCountry("India"); c.setGstin(gstin);
            return customers.save(c);
        });
    }

    private void ensureUser(String email, String password, UserRole role, Customer customer) {
        if (users.findByEmailIgnoreCase(email).isPresent()) return;
        AppUser user = new AppUser(email, passwordEncoder.encode(password), role);
        user.setCustomer(customer);
        users.save(user);
    }
}
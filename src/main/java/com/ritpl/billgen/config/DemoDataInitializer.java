package com.ritpl.billgen.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.entity.UserRole;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.CatalogProductRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import com.ritpl.billgen.service.InvoiceLineInput;
import com.ritpl.billgen.service.InvoiceService;

@Component
public class DemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private final boolean demoEnabled;
    private final String demoPassword;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final CatalogProductRepository products;
    private final SubscriptionPlanRepository plans;
    private final InvoiceRepository invoices;
    private final PasswordEncoder passwordEncoder;
    private final InvoiceService invoiceService;
    private final JdbcTemplate jdbc;

    public DemoDataInitializer(@Value("${app.demo.seed-enabled:true}") boolean demoEnabled,
            @Value("${app.demo.password:ChangeMe123!}") String demoPassword,
            @Value("${app.bootstrap.admin-email:}") String bootstrapEmail,
            @Value("${app.bootstrap.admin-password:}") String bootstrapPassword,
            AppUserRepository users, CustomerRepository customers, CatalogProductRepository products,
            SubscriptionPlanRepository plans, InvoiceRepository invoices, PasswordEncoder passwordEncoder,
            InvoiceService invoiceService, JdbcTemplate jdbc) {
        this.demoEnabled = demoEnabled; this.demoPassword = demoPassword; this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword; this.users = users; this.customers = customers;
        this.products = products; this.plans = plans; this.invoices = invoices;
        this.passwordEncoder = passwordEncoder; this.invoiceService = invoiceService; this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        acquireSeedLock();
        Customer demoCustomer = null;
        if (demoEnabled) {
            demoCustomer = ensureCustomer("hello@northstar.example", "Northstar Studio", "Maya Kapoor", "Gurugram", "Haryana", "122002", "06AABCN1234A1Z5");
            ensureCustomer("accounts@mapleworks.example", "Maple & Co.", "Arjun Mehta", "New Delhi", "Delhi", "110001", "07AAFCM4821E1Z2");
            seedPlans();
            seedProducts();
            ensureUser("admin@billgen.local", demoPassword, UserRole.ADMIN, null);
            ensureUser("finance@billgen.local", demoPassword, UserRole.FINANCE, null);
            ensureUser("customer@demo.local", demoPassword, UserRole.CUSTOMER, demoCustomer);
            if (invoices.count() == 0 && demoCustomer != null) seedInvoices(demoCustomer);
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

    private Customer ensureCustomer(String email, String company, String contact, String city, String state, String postal, String gstin) {
        return customers.findByEmailIgnoreCase(email).orElseGet(() -> {
            Customer c = new Customer(company, contact, email);
            c.setPhone("+91 98765 43210"); c.setBillingAddress("42 Horizon Plaza");
            c.setCity(city); c.setState(state); c.setPostalCode(postal); c.setCountry("India"); c.setGstin(gstin);
            return customers.save(c);
        });
    }

    private void ensureUser(String email, String password, UserRole role, Customer customer) {
        if (users.findByEmailIgnoreCase(email).isEmpty()) {
            AppUser user = new AppUser(email, passwordEncoder.encode(password), role);
            user.setCustomer(customer);
            users.save(user);
        }
    }

    private void seedPlans() {
        List<SubscriptionPlan> examples = List.of(
            new SubscriptionPlan("STARTER", "Starter", "For your first 50 invoices", "Simple invoicing for solo businesses.", new BigDecimal("0.00"), new BigDecimal("0.00"), "INR", 25, 50, "Unlimited drafts\n3 invoice templates\nCustomer directory\nEmail support", false, 10),
            new SubscriptionPlan("GROWTH", "Growth", "Room to build momentum", "More customers and smarter workflows for growing teams.", new BigDecimal("799.00"), new BigDecimal("7990.00"), "INR", 500, 1000, "Everything in Starter\nUnlimited customers\nProduct and service catalogue\nPriority support\nCSV exports", true, 20),
            new SubscriptionPlan("SCALE", "Scale", "Billing at full speed", "A richer control plane for finance teams and established businesses.", new BigDecimal("2499.00"), new BigDecimal("24990.00"), "INR", 5000, 10000, "Everything in Growth\nTeam roles and permissions\nAdvanced reporting\nDedicated onboarding\nCustom invoice branding", false, 30)
        );
        for (SubscriptionPlan plan : examples) {
            if (!plans.existsByCodeIgnoreCase(plan.getCode())) plans.save(plan);
        }
    }

    private void seedProducts() {
        List<CatalogProduct> examples = List.of(
            new CatalogProduct("SVC-CONSULT", "Consulting services", ProductType.SERVICE, "Professional services", "Strategy and consulting, billed by the hour.", new BigDecimal("3500.00"), new BigDecimal("18.00")),
            new CatalogProduct("SVC-DESIGN", "Design sprint", ProductType.SERVICE, "Creative", "A focused five-day product design sprint.", new BigDecimal("42000.00"), new BigDecimal("18.00")),
            new CatalogProduct("PRD-LIC-01", "Annual software licence", ProductType.PRODUCT, "Software", "Annual named-user software licence.", new BigDecimal("12000.00"), new BigDecimal("18.00")),
            new CatalogProduct("SVC-SUPPORT", "Support retainer", ProductType.SERVICE, "Support", "Monthly technical support retainer.", new BigDecimal("18000.00"), new BigDecimal("18.00"))
        );
        for (CatalogProduct product : examples) {
            if (!products.existsBySkuIgnoreCase(product.getSku())) products.save(product);
        }
    }

    private void seedInvoices(Customer customer) {
        invoiceService.create(new CreateInvoiceCommand(customer.getId(), LocalDate.now().minusDays(21), LocalDate.now().minusDays(7),
                "Thank you for choosing Northstar Studio. Payment is due within 14 days.", "INR", "CLASSIC",
                List.of(new InvoiceLineInput("Product design workshop", new BigDecimal("1"), new BigDecimal("48000.00"), new BigDecimal("18.00")),
                        new InvoiceLineInput("Research and discovery", new BigDecimal("8"), new BigDecimal("3500.00"), new BigDecimal("18.00"))), InvoiceStatus.SENT));
        invoiceService.create(new CreateInvoiceCommand(customer.getId(), LocalDate.now().minusDays(3), LocalDate.now().plusDays(11),
                "Payment received. We appreciate your business.", "INR", "MODERN",
                List.of(new InvoiceLineInput("Brand identity package", new BigDecimal("1"), new BigDecimal("32000.00"), new BigDecimal("18.00"))), InvoiceStatus.PAID));
    }
}

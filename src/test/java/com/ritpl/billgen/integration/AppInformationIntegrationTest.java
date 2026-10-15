package com.ritpl.billgen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ritpl.billgen.entity.AppInfo;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.EmailSecurityProtocol;
import com.ritpl.billgen.entity.EmailSetting;
import com.ritpl.billgen.entity.Faq;
import com.ritpl.billgen.entity.FaqType;
import com.ritpl.billgen.entity.NotificationSetting;
import com.ritpl.billgen.entity.PolicyPage;
import com.ritpl.billgen.entity.PolicyType;
import com.ritpl.billgen.entity.Review;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AppInfoCommand;
import com.ritpl.billgen.service.AppInfoService;
import com.ritpl.billgen.service.EmailSettingsCommand;
import com.ritpl.billgen.service.EmailSettingsService;
import com.ritpl.billgen.service.FaqCommand;
import com.ritpl.billgen.service.FaqService;
import com.ritpl.billgen.service.FaqTypeCommand;
import com.ritpl.billgen.service.FaqTypeService;
import com.ritpl.billgen.service.NotificationSettingCommand;
import com.ritpl.billgen.service.NotificationSettingService;
import com.ritpl.billgen.service.PolicyPageCommand;
import com.ritpl.billgen.service.PolicyPageService;
import com.ritpl.billgen.service.ReviewCommand;
import com.ritpl.billgen.service.ReviewService;

/**
 * The App Information module against a real PostgreSQL: the migration, the singleton and uniqueness
 * constraints, the sanitizer on the way in, the sealed credential in the column, and the audit rows that
 * describe all of it. These are the guarantees a unit test cannot make, because they live in the database
 * rather than in a service - a second {@code app_info} row, a published FAQ whose type is hidden, or a
 * password that outlives a blank submission are all caught here and nowhere else.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.demo.seed-enabled=false",
        "app.jobs.enabled=false",
        "spring.session.store-type=none",
        "spring.cache.type=none",
        "app.subscription.time-zone=UTC",
        "app.secrets.encryption-key=integration-test-only-key"
})
class AppInformationIntegrationTest {

    private static final String PASSWORD = "smtp-s3cret-p4ss";
    private static final TenantScope ADMIN = TenantScope.administrator();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billgen_appinfo")
            .withUsername("billgen")
            .withPassword("billgen_it_only");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AppInfoService appInfo;
    @Autowired PolicyPageService policies;
    @Autowired ReviewService reviews;
    @Autowired FaqTypeService faqTypes;
    @Autowired FaqService faqs;
    @Autowired EmailSettingsService emailSettings;
    @Autowired NotificationSettingService notifications;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theSevenTablesExistWithTheirSeededRows() {
        for (String table : List.of("app_info", "policy_pages", "reviews", "faq_types", "faqs",
                "email_settings", "notification_settings")) {
            assertEquals(1, count("select count(*) from information_schema.tables where table_schema = 'public' "
                    + "and table_name = ?", table), table + " was created by the migration");
        }
        assertEquals(1, count("select count(*) from app_info"), "exactly one branding row is seeded");
        assertEquals("BillGen", jdbc.queryForObject("select site_name from app_info where id = 1", String.class),
                "the seeded row is the branding the application already ships with");
        assertEquals(11, count("select count(*) from notification_settings"),
                "one row per event the application can actually produce");
        for (String table : List.of("policy_pages", "reviews", "faq_types", "faqs")) {
            assertEquals(0, count("select count(*) from " + table + " where created_by = 'migration:v12'"),
                    "no " + table + " content is invented by a migration");
        }
        assertEquals(1, count("select count(*) from notification_settings where notification_code = "
                + "'PAYMENT_FAILED' and status = 'INACTIVE'"),
                "a notice nothing raises exists but is switched off, and says why");
    }

    @Test
    void aSecondSingletonRowIsRefusedByTheDatabaseRatherThanByConvention() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("insert into app_info (id, site_name) values (2, 'Implied second brand')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("insert into app_info (id, site_name, primary_color) values (1, 'Repeat', '#fff')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into app_info (id, site_name, primary_color) values (1, 'Bad', 'blue')"));
    }

    @Test
    void brandingIsStoredAuditedAndReflectedInThePublicView() {
        AppInfo saved = appInfo.save(ADMIN, new AppInfoCommand("RIT Payments", "Billing for studios",
                "https://cdn.example.test/logo.png", "/images/favicon.png", "help@ritpayments.test",
                "+91 98765 43210", null, "#1d4ed8", null, "https://instagram.com/ritpayments", null, null,
                null, "Built for small studios.", "© 2026 RIT Payments", "Prices exclude taxes."));
        assertEquals("RIT Payments", saved.getSiteName());
        assertEquals("#19182f", saved.getSecondaryColor(), "an omitted colour keeps the default, not an empty string");
        AppInfo reread = appInfo.current();
        assertEquals("help@ritpayments.test", reread.getSupportEmail());
        assertEquals("https://cdn.example.test/logo.png", reread.getSiteLogoUrl());

        assertEquals(1, count("select count(*) from audit_records where resource_type = 'APP_INFO' "
                + "and action = 'UPDATE'"), "changing the product's own identity is an audited event");
        String audited = jdbc.queryForObject("select coalesce(summary,'') || ' ' || coalesce(before_values,'')"
                + " || ' ' || coalesce(after_values,'') from audit_records where resource_type = 'APP_INFO' "
                + "order by created_at desc limit 1", String.class);
        assertFalse(audited.contains("cdn.example.test/logo.png"),
                "the audit row records that a logo was set, not the address it points at");
        assertFalse(audited.contains("+91 98765 43210"), "nor does it dump the whole form");
    }

    @Test
    void invalidBrandingIsRefusedWithASentenceTheAdministratorCanActOn() {
        assertThrows(IllegalArgumentException.class,
                () -> appInfo.save(ADMIN, new AppInfoCommand("  ", null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null)), "a blank name is not a name");
        IllegalArgumentException colour = assertThrows(IllegalArgumentException.class,
                () -> appInfo.save(ADMIN, new AppInfoCommand("RIT Payments", null, null, null, null, null, null,
                        "blue", null, null, null, null, null, null, null, null)));
        assertTrue(colour.getMessage().contains("hex colour"), colour.getMessage());
        IllegalArgumentException link = assertThrows(IllegalArgumentException.class,
                () -> appInfo.save(ADMIN, new AppInfoCommand("RIT Payments", null, null, null, null, null, null,
                        null, null, "ftp://files.example.test", null, null, null, null, null, null)));
        assertTrue(link.getMessage().contains("http://"), link.getMessage());
    }

    @Test
    void aPolicyIsOneRowPerTypeSanitizedOnTheWayInAndOnlyVisibleWhenPublished() {
        PolicyPage first = policies.save(ADMIN, new PolicyPageCommand(PolicyType.REFUND, "Refund policy",
                "Refunds", "What we refund and when.",
                "<h2>Window</h2><p>Within seven days.</p><script>steal()</script>"
                        + "<a href=\"javascript:steal()\">click</a>", ContentStatus.INACTIVE));
        assertNotNull(first.getId());
        assertFalse(first.getPageContent().contains("script"), "the tag does not reach the column");
        assertFalse(first.getPageContent().contains("javascript:"), "nor does the link target");
        assertTrue(first.getPageContent().contains("<p>Within seven days.</p>"), "the policy itself survives");
        assertFalse(policies.published().stream().anyMatch(row -> row.getId().equals(first.getId())),
                "an unpublished policy is not offered to the public site");

        PolicyPage again = policies.save(ADMIN, new PolicyPageCommand(PolicyType.REFUND, "Refund policy v2",
                "Refunds", "Seven days, no questions.", "<p>Revised.</p>", ContentStatus.INACTIVE));
        assertEquals(first.getId(), again.getId(), "a policy is revised in place rather than versioned behind a list");
        assertEquals(1, count("select count(*) from policy_pages where policy_type = 'REFUND'"));
        assertEquals("Refund policy v2", again.getHeading());

        PolicyPage published = policies.setStatus(ADMIN, PolicyType.REFUND, ContentStatus.ACTIVE);
        assertEquals(ContentStatus.ACTIVE, published.getStatus());
        assertTrue(policies.published().stream().anyMatch(row -> row.getId().equals(published.getId())));
        policies.setStatus(ADMIN, PolicyType.REFUND, ContentStatus.INACTIVE);
        assertFalse(policies.published().stream().anyMatch(row -> row.getId().equals(published.getId())),
                "withdrawing takes it off the public page again");
        assertThrows(IllegalArgumentException.class,
                () -> policies.save(ADMIN, new PolicyPageCommand(PolicyType.PRIVACY, "Privacy", null, null,
                        "<p></p>", ContentStatus.INACTIVE)), "markup with no text in it is not a policy");
    }

    @Test
    void reviewsAreRatedOrderedAndRemovedWithoutLosingTheRecord() {
        assertThrows(IllegalArgumentException.class, () -> reviews.save(ADMIN, null,
                new ReviewCommand("Too Kind", "Loved it", 6, null, null, null, 0, ContentStatus.INACTIVE)));
        Review second = reviews.save(ADMIN, null, new ReviewCommand("Zeta Studio", "Second shown.", 4,
                "Ludhiana", "Owner", null, 2, ContentStatus.ACTIVE));
        Review first = reviews.save(ADMIN, null, new ReviewCommand("Alpha Films", "First shown.", 5,
                "Delhi", "Producer", null, 1, ContentStatus.ACTIVE));
        List<Review> publicList = reviews.published(50);
        assertTrue(positionOf(publicList, first.getId()) >= 0
                        && positionOf(publicList, second.getId()) > positionOf(publicList, first.getId()),
                "display order decides, not the order rows were inserted");

        reviews.delete(ADMIN, second.getId());
        assertTrue(reviews.published(50).stream().noneMatch(row -> row.getId().equals(second.getId())));
        assertEquals(1, count("select count(*) from reviews where id = ? and deleted_at is not null "
                + "and deleted_by = 'administrator'", second.getId()),
                "a removed review keeps its row so the page's history is explainable");
        assertThrows(IllegalArgumentException.class, () -> reviews.save(ADMIN, second.getId(),
                new ReviewCommand("Edited after deletion", "No.", 5, null, null, null, 0, ContentStatus.ACTIVE)),
                "a deleted review is not an editable one");
        Review restored = reviews.restore(ADMIN, second.getId());
        assertNull(restored.getDeletedAt());
        assertEquals(ContentStatus.INACTIVE, restored.getStatus(), "restoring returns it to the list, not to the site");
    }

    @Test
    void faqTypesAndQuestionsAnswerToBothTheirOwnStatusAndTheirTypes() {
        FaqType billing = faqTypes.save(ADMIN, new FaqTypeCommand("Billing & Plans", null, "Invoices and plans.",
                1, ContentStatus.ACTIVE));
        assertEquals("billing-plans", billing.getTypeCode(), "the code is derived, lower-cased and link-shaped");
        // A second type may not claim the same code in another case: the code is what a link points at.
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class, () -> faqTypes.save(ADMIN,
                new FaqTypeCommand("Other billing", "BILLING-PLANS", null, 2, ContentStatus.ACTIVE)));
        assertTrue(duplicate.getMessage().contains("already exists"), duplicate.getMessage());

        Faq question = faqs.save(ADMIN, null, new FaqCommand(billing.getId(), "How do I get a refund?",
                "<p>Write to <strong>support</strong>.</p>", 1, ContentStatus.INACTIVE));
        assertTrue(faqs.published(billing.getTypeCode(), 10).stream()
                        .noneMatch(row -> row.id().equals(question.getId())),
                "a hidden question is not on the public page");
        faqs.setStatus(ADMIN, question.getId(), ContentStatus.ACTIVE);
        List<FaqService.PublicFaq> publicList = faqs.published(billing.getTypeCode(), 10);
        assertEquals(1, publicList.size());
        assertEquals("How do I get a refund?", publicList.get(0).question());
        assertEquals("Billing & Plans", publicList.get(0).typeName(), "the page can name the group it is listing");
        assertEquals(0, faqs.published("no-such-type", 10).size(), "an unknown type is an empty page, not an error");

        IllegalArgumentException held = assertThrows(IllegalArgumentException.class,
                () -> faqTypes.delete(ADMIN, billing.getId()));
        assertTrue(held.getMessage().contains("question"), held.getMessage());
        faqs.delete(ADMIN, question.getId());
        faqTypes.delete(ADMIN, billing.getId());
        assertTrue(faqTypes.list().stream().noneMatch(row -> row.getId().equals(billing.getId())),
                "an empty type can be removed once the questions are gone");
    }

    @Test
    void theMailPasswordIsSealedAndABlankSubmissionLeavesItAlone() {
        EmailSetting saved = emailSettings.save(ADMIN, new EmailSettingsCommand("smtp.example.test", 587,
                "billing@ritpayments.test", PASSWORD, true, EmailSecurityProtocol.START_TLS, 8000,
                "RIT Payments", "billing@ritpayments.test", "help@ritpayments.test", ContentStatus.ACTIVE));
        assertTrue(saved.isUsable());
        String sealed = jdbc.queryForObject("select smtp_secret from email_settings where id = 1", String.class);
        assertNotNull(sealed);
        assertTrue(sealed.startsWith("v1:"), "the column holds an envelope, not a password");
        assertFalse(sealed.contains(PASSWORD));

        EmailSettingsService.View view = emailSettings.view();
        assertTrue(view.passwordSet());
        assertTrue(view.transportLive(), "an active, complete transport is what the sink will use");
        assertFalse(view.toString().contains(PASSWORD), "no read path carries it, not even a debug print");

        EmailSetting again = emailSettings.save(ADMIN, new EmailSettingsCommand("smtp.example.test", 465,
                "billing@ritpayments.test", "   ", true, EmailSecurityProtocol.SSL_TLS, 8000,
                "RIT Payments", "billing@ritpayments.test", null, ContentStatus.ACTIVE));
        assertEquals(465, again.getSmtpPort().intValue(), "the rest of the form did change");
        assertEquals(sealed, jdbc.queryForObject("select smtp_secret from email_settings where id = 1", String.class),
                "and the password was not overwritten by an empty field");
        assertFalse(jdbc.queryForObject(
                "select coalesce(before_values,'') || coalesce(after_values,'') || coalesce(summary,'') "
                        + "from audit_records where resource_type = 'EMAIL_SETTINGS' order by id desc limit 1",
                String.class).contains(PASSWORD), "the audit trail describes the change without repeating the credential");
    }

    @Test
    void anInactiveTransportSaysSoInsteadOfPretendingToSend() {
        emailSettings.save(ADMIN, new EmailSettingsCommand("smtp.example.test", 587, "relay@ritpayments.test",
                PASSWORD, true, EmailSecurityProtocol.START_TLS, 8000, "RIT Payments", "billing@ritpayments.test",
                null, ContentStatus.INACTIVE));
        EmailSettingsService.TestResult refused = emailSettings.test(ADMIN, "someone@example.test");
        assertFalse(refused.sent(), "a saved-but-inactive transport is not asked to send");
        assertTrue(refused.message().contains("not active and complete"), refused.message());
        assertFalse(emailSettings.view().transportLive(), "and the application says it is not live");

        assertThrows(IllegalArgumentException.class, () -> emailSettings.save(ADMIN,
                new EmailSettingsCommand("smtp://smtp.example.test", 587, "u", "p", true,
                        EmailSecurityProtocol.NONE, 8000, "n", "a@b.test", null, ContentStatus.ACTIVE)),
                "a URL where a server name belongs is refused rather than tried");
        assertThrows(IllegalArgumentException.class, () -> emailSettings.save(ADMIN,
                new EmailSettingsCommand("smtp.example.test", 587, null, "p", true,
                        EmailSecurityProtocol.NONE, 8000, "n", "a@b.test", null, ContentStatus.ACTIVE)),
                "authentication without a username cannot be activated");
        assertThrows(IllegalArgumentException.class, () -> emailSettings.save(ADMIN,
                new EmailSettingsCommand("smtp.example.test", 587, "u", "p", true, EmailSecurityProtocol.NONE,
                        50, "n", "a@b.test", null, ContentStatus.ACTIVE)),
                "a timeout the transport could not honour is refused before it is stored");
    }

    private static int positionOf(List<Review> rows, Long id) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).getId().equals(id)) return i;
        return -1;
    }

    @Test
    void notificationSettingsAreEditablePerCodeAndCanBeMuted() {
        NotificationSetting warning = notifications.find("SUBSCRIPTION_EXPIRY_WARNING").orElseThrow();
        assertEquals(7, warning.getTriggerDaysBefore(), "the seeded row matches what the job already did");
        NotificationSetting updated = notifications.update(ADMIN, "SUBSCRIPTION_EXPIRY_WARNING",
                new NotificationSettingCommand("Subscription expiry warning", "Three weeks of notice.", 21, 7,
                        true, false, ContentStatus.ACTIVE));
        assertEquals(21, updated.getTriggerDaysBefore());
        assertFalse(updated.isInAppEnabled());
        assertTrue(notifications.active("SUBSCRIPTION_EXPIRY_WARNING").isPresent());

        NotificationSetting muted = notifications.setStatus(ADMIN, "SUBSCRIPTION_EXPIRY_WARNING",
                ContentStatus.INACTIVE);
        assertFalse(notifications.active("SUBSCRIPTION_EXPIRY_WARNING").isPresent(),
                "a muted notice is invisible to the jobs, which is the point of muting it");
        assertEquals(ContentStatus.INACTIVE, muted.getStatus());

        assertThrows(IllegalArgumentException.class, () -> notifications.update(ADMIN, "SUBSCRIPTION_EXPIRED",
                new NotificationSettingCommand("Subscription expired", null, 0, 0, false, false,
                        ContentStatus.ACTIVE)), "a notice with no channel would announce nothing");
        assertThrows(IllegalArgumentException.class,
                () -> notifications.update(ADMIN, "NOT_A_REAL_CODE", new NotificationSettingCommand("Made up",
                        null, 0, 0, true, true, ContentStatus.ACTIVE)),
                "an administrator configures notices the application can send, not ones it cannot");
        notifications.setStatus(ADMIN, "SUBSCRIPTION_EXPIRY_WARNING", ContentStatus.ACTIVE);
        notifications.update(ADMIN, "SUBSCRIPTION_EXPIRY_WARNING", new NotificationSettingCommand(
                "Subscription expiry warning", "Sent before a term ends.", 7, 4, true, true, ContentStatus.ACTIVE));
    }

    private int count(String sql, Object... args) {
        Integer found = jdbc.queryForObject(sql, Integer.class, args);
        return found == null ? 0 : found;
    }
}

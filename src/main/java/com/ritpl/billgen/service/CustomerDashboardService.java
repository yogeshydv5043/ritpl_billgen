package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AuditRecord;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductCategory;
import com.ritpl.billgen.repository.CustomInvoiceTemplateRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.ProductCategoryRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The customer dashboard. Every figure and every row is an aggregate or a paged query over this
 * account's own data - nothing here is sampled, cached from a fixture or written anywhere - and the
 * lists are bounded so the page costs the same whether the account has twelve invoices or twelve
 * thousand.
 */
@Service
public class CustomerDashboardService {

    /** One summary card. {@code href} is the list that produced the number, so a figure is explainable. */
    public record Card(String key, String label, String value, String note, String href, String icon, String tone) { }

    public record InvoiceRow(long id, String invoiceNumber, LocalDate issueDate, LocalDate dueDate, String statusLabel,
            String status, BigDecimal total, String currency, boolean overdue) { }

    /** A catalogue item or category with the activity that last touched it. */
    public record CatalogueRow(long id, String name, String detail, String activity, LocalDateTime changedOn,
            boolean active) { }

    public record AuditRow(long id, String action, String module, String record, String actor, Instant at,
            boolean failure) { }

    public record Dashboard(List<Card> cards, List<InvoiceRow> recentInvoices, List<CatalogueRow> recentProducts,
            List<CatalogueRow> recentCategories, List<AuditRow> recentActivity, String planName, String planCode,
            String subscriptionStatus, LocalDate startedOn, LocalDate expiresOn, Long daysToExpiry,
            long invoiceLimit, long invoicesUsed, long remainingInvoices, boolean quotaExhausted,
            boolean expiringSoon, boolean hasPlan) { }

    private final InvoiceService invoices;
    private final InvoiceRepository invoiceRepository;
    private final CatalogService catalog;
    private final ProductCategoryService categories;
    private final ProductCategoryRepository categoryRepository;
    private final CustomInvoiceTemplateRepository customTemplates;
    private final SubscriptionService subscriptions;
    private final AuditService audit;
    private final CustomerSettingsService settings;

    public CustomerDashboardService(InvoiceService invoices, InvoiceRepository invoiceRepository, CatalogService catalog,
            ProductCategoryService categories, ProductCategoryRepository categoryRepository,
            CustomInvoiceTemplateRepository customTemplates, SubscriptionService subscriptions, AuditService audit,
            CustomerSettingsService settings) {
        this.invoices = invoices; this.invoiceRepository = invoiceRepository; this.catalog = catalog;
        this.categories = categories; this.categoryRepository = categoryRepository; this.customTemplates = customTemplates;
        this.subscriptions = subscriptions; this.audit = audit; this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Dashboard load(TenantScope scope, long customerId) {
        scope.requireCustomerAccess(customerId);
        UsageQuota quota = subscriptions.usage(scope, customerId);
        CustomerSubscription term = subscriptions.findActiveSubscription(scope, customerId).orElse(null);
        LocalDate today = subscriptions.today();

        long templateCount = subscriptions.accessibleTemplates(scope).size();
        long customizedCount = customTemplates.countByCustomer_IdAndActiveTrue(customerId);
        long productTotal = catalog.countAll(scope);
        long productActive = catalog.countActive(scope);
        long categoryTotal = categories.countAll(scope);
        long categoryActive = categories.countActive(scope);
        long invoiceTotal = invoiceRepository.countByCustomer_Id(customerId);
        long invoiceMonth = invoiceRepository.countByCustomer_IdAndIssueDateGreaterThanEqualAndIssueDateLessThan(
                customerId, today.withDayOfMonth(1), today.plusMonths(1).withDayOfMonth(1));
        BigDecimal billedMonth = invoiceRepository.sumTotalByCustomerBetween(customerId,
                today.withDayOfMonth(1), today.plusMonths(1).withDayOfMonth(1));

        String currency = settings.get(scope, customerId).getDefaultCurrency();
        Long daysToExpiry = term == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(today, term.getEndsOn());
        boolean expiringSoon = daysToExpiry != null && daysToExpiry >= 0 && daysToExpiry <= 7;

        List<Card> cards = new ArrayList<>();
        cards.add(new Card("templates", "Invoice templates", String.valueOf(templateCount),
                "Included in " + (term == null ? "your plan" : term.getPlan().getName()), "/portal/templates", "▤", "neutral"));
        cards.add(new Card("custom-templates", "Customised templates", String.valueOf(customizedCount),
                templateCount == 0 && customizedCount == 0 ? "No template is available until a plan is active"
                        : quota.customTemplateLimit() <= 0 ? "Not included in your plan"
                        : quota.customTemplateLimit() - quota.customTemplatesUsed() + " of " + quota.customTemplateLimit() + " free",
                "/portal/templates", "✎", "neutral"));
        cards.add(new Card("remaining-quota", "Remaining invoice quota", String.valueOf(quota.remainingInvoices()),
                quota.invoiceLimit() <= 0 ? "No allowance on the current term"
                        : quota.invoicesUsed() + " of " + quota.invoiceLimit() + " used this period",
                "/portal/invoices", "◑", quota.remainingInvoices() == 0 ? "warning" : "positive"));
        cards.add(new Card("invoices", "Invoices generated", String.valueOf(invoiceTotal),
                "All time, this account only", "/portal/invoices", "▥", "neutral"));
        cards.add(new Card("invoices-month", "Invoices this month", String.valueOf(invoiceMonth),
                billedMonth == null || billedMonth.signum() == 0 ? "No billed value yet"
                        : money(billedMonth) + " " + currency + " issued", "/portal/invoices", "▦", "neutral"));
        cards.add(new Card("products", "Total products", String.valueOf(productTotal),
                productActive + " active · " + (productTotal - productActive) + " hidden", "/portal/products", "◇", "neutral"));
        cards.add(new Card("products-active", "Active products", String.valueOf(productActive),
                "Available on new invoices", "/portal/products?status=active", "✓", "positive"));
        cards.add(new Card("products-inactive", "Inactive products", String.valueOf(productTotal - productActive),
                "Kept on invoices already issued", "/portal/products?status=hidden", "⊘", "muted"));
        cards.add(new Card("categories", "Total categories", String.valueOf(categoryTotal),
                categoryActive + " available for new items", "/portal/categories", "▤", "neutral"));
        cards.add(new Card("subscription", term == null ? "No active subscription" : "Active subscription",
                term == null ? "—" : (daysToExpiry != null && daysToExpiry < 0 ? "Expired" : term.getPlan().getName()),
                term == null ? "Choose a plan to unlock invoicing"
                        : (daysToExpiry != null && daysToExpiry < 0 ? "Term ended " + term.getEndsOn()
                                : "Renews or ends " + term.getEndsOn() + " · in " + daysToExpiry + " day(s)"),
                "/portal/plan", "◆", term == null ? "warning" : expiringSoon ? "warning" : "positive"));

        List<InvoiceRow> recentInvoices = new ArrayList<>();
        for (Invoice invoice : invoices.recent(scope, 10)) {
            recentInvoices.add(new InvoiceRow(invoice.getId(), invoice.getInvoiceNumber(), invoice.getIssueDate(),
                    invoice.getDueDate(), invoice.getStatus().getLabel(), invoice.getStatus().name(),
                    invoice.getTotalAmount(), invoice.getCurrency(),
                    invoice.getStatus() == InvoiceStatus.SENT && invoice.getDueDate() != null
                            && invoice.getDueDate().isBefore(today)));
        }

        List<CatalogueRow> recentProducts = new ArrayList<>();
        for (CatalogProduct product : catalog.recent(scope, 10)) {
            boolean updated = product.getUpdatedAt() != null && product.getCreatedAt() != null
                    && product.getUpdatedAt().isAfter(product.getCreatedAt());
            recentProducts.add(new CatalogueRow(product.getId(), product.getName(),
                    product.getSku() + (product.getCategoryName() == null ? "" : " · " + product.getCategoryName()),
                    updated ? "Updated" : "Created", product.getUpdatedAt(), product.isActive()));
        }

        List<CatalogueRow> recentCategories = new ArrayList<>();
        for (ProductCategory category : categories.recent(scope, 5)) {
            boolean updated = category.getUpdatedAt() != null && category.getCreatedAt() != null
                    && category.getUpdatedAt().isAfter(category.getCreatedAt());
            recentCategories.add(new CatalogueRow(category.getId(), category.getName(),
                    category.getDescription() == null ? "No description" : category.getDescription(),
                    updated ? "Updated" : "Created", category.getUpdatedAt(), category.isActive()));
        }

        List<AuditRow> recentActivity = new ArrayList<>();
        // The account's own viewing window applies here too, so the dashboard cannot show history that
        // the same account has chosen to keep out of its listings.
        AuditFilter filter = new AuditFilter(null, null, null, null, null, settings.auditWindowStart(customerId), null);
        for (AuditRecord record : audit.search(scope, filter,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent()) {
            recentActivity.add(new AuditRow(record.getId(), record.getAction(), record.getResourceType(),
                    record.getSummary(), record.getActor(), record.getCreatedAt(),
                    "FAILURE".equals(record.getOutcome())));
        }

        return new Dashboard(cards, recentInvoices, recentProducts, recentCategories, recentActivity,
                term == null ? null : term.getPlan().getName(), term == null ? null : term.getPlan().getCode(),
                term == null ? "No plan" : term.getStatus().getDisplayName(),
                term == null ? null : term.getStartedOn(), term == null ? null : term.getEndsOn(),
                daysToExpiry, quota.invoiceLimit(), quota.invoicesUsed(), quota.remainingInvoices(),
                quota.remainingInvoices() == 0, expiringSoon, term != null);
    }

    /** Grouped digits; the caller appends the account's currency code rather than an assumed symbol. */
    private static String money(BigDecimal value) {
        return new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT))
                .format(value);  // DecimalFormat.format(Object) keeps BigDecimal precision
    }
}

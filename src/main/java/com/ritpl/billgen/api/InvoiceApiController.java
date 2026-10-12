package com.ritpl.billgen.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AccountStatsService;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.PlanService;
import com.ritpl.billgen.service.ProductCategoryService;
import com.ritpl.billgen.service.SubscriptionService;
import jakarta.validation.Valid;

/**
 * The management API.
 *
 * <p>Every endpoint resolves a {@link TenantScope} from the authenticated credential first: an
 * administrator reads across accounts, a customer reads and writes only its own rows, and records
 * of another account are reported as {@code 404}. Writes to customer-owned data are refused for
 * administrator credentials.</p>
 */
@RestController
@Validated
@RequestMapping("/api/v1")
public class InvoiceApiController {
    private final InvoiceService invoices;
    private final CustomerService customers;
    private final CatalogService catalog;
    private final ProductCategoryService categories;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final AccountStatsService accountStats;

    public InvoiceApiController(InvoiceService invoices, CustomerService customers, CatalogService catalog,
            ProductCategoryService categories, PlanService plans,
            SubscriptionService subscriptions, AccountStatsService accountStats) {
        this.invoices = invoices; this.customers = customers; this.catalog = catalog; this.categories = categories;
        this.plans = plans; this.subscriptions = subscriptions; this.accountStats = accountStats;
    }

    // --- invoices -------------------------------------------------------------------------------------------------

    @GetMapping("/invoices")
    public List<InvoiceResponse> listInvoices(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long customerId) {
        TenantScope scope = scope(principal);
        var all = invoices.findAll(scope);
        if (customerId == null) return all.stream().map(InvoiceResponse::from).toList();
        scope.requireCustomerAccess(customerId);
        return all.stream().filter(invoice -> invoice.getCustomer().getId().equals(customerId))
                .map(InvoiceResponse::from).toList();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceResponse invoice(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return InvoiceResponse.from(invoices.findById(scope(principal), id));
    }

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> createInvoice(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateInvoiceRequest request) {
        if (request.initialStatus() == InvoiceStatus.PAID) throw new IllegalArgumentException("An API invoice must start as DRAFT or SENT.");
        var saved = invoices.create(scope(principal), request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(InvoiceResponse.from(invoices.findById(scope(principal), saved.getId())));
    }

    @PatchMapping("/invoices/{id}/status")
    public InvoiceResponse updateStatus(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody InvoiceStatusRequest request) {
        TenantScope scope = scope(principal);
        var invoice = invoices.findById(scope, id);
        if (request.status() == InvoiceStatus.SENT && invoice.getStatus() == InvoiceStatus.DRAFT) {
            invoices.markSent(scope, id);
        } else if (request.status() == InvoiceStatus.PAID && invoice.getStatus() == InvoiceStatus.SENT) {
            invoices.markPaid(scope, id);
        } else if (request.status() != invoice.getStatus()) {
            throw new IllegalArgumentException("Status can only move from DRAFT to SENT and from SENT to PAID.");
        }
        return InvoiceResponse.from(invoices.findById(scope, id));
    }

    // --- products -------------------------------------------------------------------------------------------------

    @GetMapping("/products")
    public List<ProductResponse> listProducts(@AuthenticationPrincipal AppUserPrincipal principal) {
        return catalog.findAll(scope(principal)).stream().map(ProductResponse::from).toList();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ProductRequest request) {
        var saved = catalog.save(scope(principal), null, request.toCommand());
        return ResponseEntity.status(201).body(ProductResponse.from(saved));
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(catalog.save(scope(principal), id, request.toCommand()));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        catalog.delete(scope(principal), id);
        return ResponseEntity.noContent().build();
    }

    // --- product categories ---------------------------------------------------------------------------------------

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories(@AuthenticationPrincipal AppUserPrincipal principal) {
        return categories.findAll(scope(principal)).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        var saved = categories.save(scope(principal), null, request.name(), request.description());
        return ResponseEntity.status(201).body(CategoryResponse.from(saved));
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categories.save(scope(principal), id, request.name(), request.description()));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        categories.delete(scope(principal), id);
        return ResponseEntity.noContent().build();
    }

    // --- templates, plans and subscriptions -----------------------------------------------------------------------

    /** Administrators see the whole catalogue; a customer only sees its plan's templates. */
    @GetMapping("/templates")
    public List<TemplateResponse> listTemplates(@AuthenticationPrincipal AppUserPrincipal principal) {
        return subscriptions.accessibleTemplates(scope(principal)).stream().map(TemplateResponse::from).toList();
    }

    @GetMapping("/plans")
    public List<PlanResponse> listPlans() { return plans.findActive().stream().map(PlanResponse::from).toList(); }

    @GetMapping("/customers/{id}/subscription")
    public SubscriptionRequest.SubscriptionResponse subscription(@PathVariable long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        TenantScope scope = scope(principal);
        return subscriptions.findActiveSubscription(scope, id)
                .map(SubscriptionRequest.SubscriptionResponse::from)
                .orElseThrow(() -> new com.ritpl.billgen.service.ResourceNotFoundException("This account has no active plan."));
    }

    /** A customer may only subscribe its own account; an administrator may manage any account. */
    @PostMapping("/customers/{id}/subscription")
    public SubscriptionRequest.SubscriptionResponse assignPlan(@PathVariable long id,
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody SubscriptionRequest request) {
        var subscription = subscriptions.activate(scope(principal), id, request.planId(), principal.getUsername());
        return SubscriptionRequest.SubscriptionResponse.from(subscription);
    }

    @DeleteMapping("/customers/{id}/subscription")
    public ResponseEntity<Void> cancelPlan(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        subscriptions.cancel(scope(principal), id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    // --- customer accounts ----------------------------------------------------------------------------------------

    @GetMapping("/customers")
    public List<CustomerResponse> listCustomers(@AuthenticationPrincipal AppUserPrincipal principal) {
        TenantScope scope = scope(principal);
        return customers.findAll(scope, null).stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/customers/{id}")
    public CustomerResponse customer(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        TenantScope scope = scope(principal);
        return CustomerResponse.from(customers.findById(scope, id));
    }

    /** Customer-wise statistics. A customer credential only ever receives its own row. */
    @GetMapping("/customers/{id}/stats")
    public AccountStatsResponse customerStats(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return AccountStatsResponse.from(accountStats.findOne(scope(principal), id));
    }

    @GetMapping("/account-stats")
    public List<AccountStatsResponse> accountStats(@AuthenticationPrincipal AppUserPrincipal principal) {
        TenantScope scope = scope(principal);
        if (scope.isAdminScope()) {
            return accountStats.findAll(scope, null).stream().map(AccountStatsResponse::from).toList();
        }
        return List.of(AccountStatsResponse.from(accountStats.findOne(scope, scope.requireCustomerId())));
    }

    private TenantScope scope(AppUserPrincipal principal) {
        if (principal == null) throw new AccessDeniedException("Authentication is required.");
        return TenantScope.from(principal);
    }
}
package com.ritpl.billgen.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.CustomInvoiceTemplate;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.repository.CustomInvoiceTemplateRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.security.TenantScope;

@Service
public class CustomTemplateService {
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private final CustomInvoiceTemplateRepository templates;
    private final CustomerRepository customers;
    private final SubscriptionService subscriptions;
    private final AuditService audit;

    public CustomTemplateService(CustomInvoiceTemplateRepository templates, CustomerRepository customers,
            SubscriptionService subscriptions, AuditService audit) {
        this.templates = templates; this.customers = customers; this.subscriptions = subscriptions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CustomInvoiceTemplate> findAll(TenantScope scope) {
        return scope.isAdminScope() ? templates.findAllByOrderByCustomer_CompanyNameAscNameAsc()
                : templates.findByCustomer_IdOrderByNameAsc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public List<CustomInvoiceTemplate> findActive(TenantScope scope) {
        if (scope.isAdminScope()) return templates.findAllByOrderByCustomer_CompanyNameAscNameAsc().stream().filter(CustomInvoiceTemplate::isActive).toList();
        return templates.findByCustomer_IdAndActiveTrueOrderByNameAsc(scope.requireCustomerId());
    }

    @Transactional(readOnly = true)
    public CustomInvoiceTemplate findById(TenantScope scope, long id) {
        if (scope.isAdminScope()) return templates.findById(id).orElseThrow(() -> new ResourceNotFoundException("Custom template not found."));
        return templates.findByIdAndCustomer_Id(id, scope.requireCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Custom template not found."));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public CustomInvoiceTemplate save(TenantScope scope, Long id, CustomTemplateCommand command) {
        scope.requireWriteAccess();
        long ownerId = scope.requireCustomerId();
        Validated v = validate(command);
        var activeSubscription = subscriptions.requireActiveForUpdate(scope);
        Customer owner = customers.findById(ownerId).filter(c -> !c.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        if (id == null) {
            long used = templates.countByCustomer_IdAndActiveTrue(ownerId);
            if (used >= activeSubscription.getEffectiveCustomTemplateLimit()) {
                throw new IllegalArgumentException("Your plan's custom template limit has been reached.");
            }
        }
        boolean duplicate = id == null
                ? templates.existsByCustomer_IdAndNameIgnoreCase(ownerId, v.name())
                : templates.existsByCustomer_IdAndNameIgnoreCaseAndIdNot(ownerId, v.name(), id);
        if (duplicate) throw new IllegalArgumentException("You already have a custom template with that name.");
        CustomInvoiceTemplate template = id == null
                ? new CustomInvoiceTemplate(owner, v.name(), v.style(), v.color()) : findById(scope, id);
        Map<String, Object> before = id == null ? null : Map.of("name", template.getName(), "style", template.getStyleKey(), "active", template.isActive());
        template.setCustomer(owner); template.setName(v.name()); template.setStyle(v.style());
        template.setAccentColor(v.color()); template.setActive(command.active());
        CustomInvoiceTemplate saved = templates.save(template);
        audit.record(AuditEventCommand.success(ownerId, id == null ? "CREATE" : "UPDATE", "CUSTOM_TEMPLATE", "Invoice template", saved.getId(),
                id == null ? "Customer created a custom invoice template." : "Customer updated a custom invoice template.", before,
                Map.of("name", saved.getName(), "style", saved.getStyleKey(), "active", saved.isActive())));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(TenantScope scope, long id) {
        CustomInvoiceTemplate template = findById(scope, id);
        scope.requireWriteAccess();
        long ownerId = scope.requireCustomerId();
        if (templates.countInvoices(id) > 0) template.setActive(false);
        else templates.delete(template);
        audit.record(AuditEventCommand.success(ownerId, "DELETE", "CUSTOM_TEMPLATE", "Invoice template", id,
                "Customer deleted or archived a custom invoice template.", Map.of("name", template.getName()),
                Map.of("archived", templates.countInvoices(id) > 0)));
    }

    private Validated validate(CustomTemplateCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.name().trim().length() > 100)
            throw new IllegalArgumentException("Enter a custom template name of at most 100 characters.");
        BillTemplateStyle style;
        try { style = BillTemplateStyle.valueOf(command.styleKey() == null ? "CLASSIC" : command.styleKey().trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose a supported invoice template style."); }
        String color = command.accentColor() == null || command.accentColor().isBlank() ? null : command.accentColor().trim();
        if (color != null && !COLOR.matcher(color).matches()) throw new IllegalArgumentException("Accent color must be a six-digit hex color such as #2563EB.");
        return new Validated(command.name().trim(), style, color);
    }
    private record Validated(String name, BillTemplateStyle style, String color) { }
}

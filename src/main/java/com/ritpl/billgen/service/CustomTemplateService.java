package com.ritpl.billgen.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * The invoice templates a customer owns. A custom template is always a copy of one of the layouts the
 * platform offers, decorated with this account's own accent, font and text blocks: the system template
 * a plan bundles is never written here, so customizing one design cannot change what another account
 * - or an already issued invoice - looks like.
 *
 * <p>Availability is a plan entitlement, checked on the write path: creating a template, duplicating
 * one and hiding one all consult the active subscription's custom-template limit, and every query is
 * pinned to the owning account.</p>
 */
@Service
public class CustomTemplateService {
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Set<String> FONTS = Set.of("DEFAULT", "SERIF", "MONO");
    private static final Set<String> DENSITIES = Set.of("COMFORTABLE", "COMPACT");

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
            long used = templates.countByCustomer_IdAndDeletedAtIsNull(ownerId);
            if (used >= activeSubscription.getEffectiveCustomTemplateLimit()) {
                throw new IllegalArgumentException("Your plan's custom template limit has been reached. Hiding a "
                        + "design does not free its slot; deleting one that no invoice has used does.");
            }
        }
        boolean duplicate = id == null
                ? templates.existsByCustomer_IdAndNameIgnoreCaseAndDeletedAtIsNull(ownerId, v.name())
                : templates.existsByCustomer_IdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(ownerId, v.name(), id);
        if (duplicate) throw new IllegalArgumentException("You already have a custom template with that name.");
        CustomInvoiceTemplate template = id == null
                ? new CustomInvoiceTemplate(owner, v.name(), v.style(), v.color()) : findById(scope, id);
        if (template.isDeleted()) {
            throw new IllegalArgumentException("That design is kept only because invoices were issued with it. It "
                    + "cannot be edited or brought back - create a new design instead.");
        }
        Map<String, Object> before = id == null ? null : Map.of("name", template.getName(), "style", template.getStyleKey(), "active", template.isActive());
        template.setCustomer(owner); template.setName(v.name()); template.setStyle(v.style());
        template.setAccentColor(v.color()); template.setActive(command.active());
        template.setFontKey(v.font()); template.setLayoutDensity(v.density());
        template.setShowLogo(command.showLogo()); template.setShowBankDetails(command.showBankDetails());
        template.setBankDetails(command.showBankDetails() ? v.bankDetails() : null);
        template.setPaymentTerms(v.paymentTerms()); template.setFooterText(v.footerText());
        template.setSignatureLabel(v.signatureLabel()); template.setSignatureName(v.signatureName());
        CustomInvoiceTemplate saved = templates.save(template);
        audit.record(AuditEventCommand.success(ownerId, id == null ? "CREATE" : "UPDATE", "CUSTOM_TEMPLATE", "Invoice template", saved.getId(),
                id == null ? "Customer created a custom invoice template." : "Customer updated a custom invoice template.", before,
                Map.of("name", saved.getName(), "style", saved.getStyleKey(), "active", saved.isActive(),
                        "font", saved.getFontKey(), "density", saved.getLayoutDensity(),
                        "customized", saved.getFooterText() != null || saved.getPaymentTerms() != null
                                || saved.getBankDetails() != null || saved.getSignatureName() != null)));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(TenantScope scope, long id) {
        CustomInvoiceTemplate template = findById(scope, id);
        scope.requireWriteAccess();
        long ownerId = scope.requireCustomerId();
        boolean referenced = templates.countInvoices(id) > 0;
        if (referenced) {
            // Retire rather than hide. The row stays because issued documents refer to it, but it is out
            // of the gallery, out of the selectable list, and no longer occupies a slot in the plan - a
            // customer must not be locked out of customising by the size of its history.
            template.setActive(false);
            template.setDeletedAt(java.time.Instant.now());
            templates.save(template);
        } else {
            templates.delete(template);
        }
        audit.record(AuditEventCommand.success(ownerId, "DELETE", "CUSTOM_TEMPLATE", "Invoice template", id,
                referenced ? "Customer retired a custom invoice template that invoices still use."
                        : "Customer deleted a custom invoice template.",
                Map.of("name", template.getName()), Map.of("archived", referenced)));
    }

    /**
     * A copy of one of the account's own templates, so a design can be branched instead of overwritten.
     * The plan's limit applies here too, because a copy is one more row.
     */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public CustomInvoiceTemplate duplicate(TenantScope scope, long id) {
        scope.requireWriteAccess();
        long ownerId = scope.requireCustomerId();
        CustomInvoiceTemplate source = findById(scope, id);
        var activeSubscription = subscriptions.requireActiveForUpdate(scope);
        if (templates.countByCustomer_IdAndDeletedAtIsNull(ownerId) >= activeSubscription.getEffectiveCustomTemplateLimit()) {
            throw new IllegalArgumentException("Your plan's custom template limit has been reached, so this copy cannot be made."
                    + " Hiding a design does not free its slot; deleting one that no invoice has used does.");
        }
        Customer owner = customers.findById(ownerId).filter(c -> !c.isSystemAccount())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found."));
        CustomInvoiceTemplate copy = new CustomInvoiceTemplate(owner, uniqueCopyName(ownerId, source.getName()),
                source.getStyle(), source.getAccentColor());
        copy.setFontKey(source.getFontKey()); copy.setLayoutDensity(source.getLayoutDensity());
        copy.setShowLogo(source.isShowLogo()); copy.setShowBankDetails(source.isShowBankDetails());
        copy.setBankDetails(source.getBankDetails()); copy.setPaymentTerms(source.getPaymentTerms());
        copy.setFooterText(source.getFooterText()); copy.setSignatureLabel(source.getSignatureLabel());
        copy.setSignatureName(source.getSignatureName());
        CustomInvoiceTemplate saved = templates.save(copy);
        audit.record(AuditEventCommand.success(ownerId, "CREATE", "CUSTOM_TEMPLATE", "Invoice template", saved.getId(),
                "Customer duplicated one of its own invoice templates.", Map.of("sourceId", id, "sourceName", source.getName()),
                Map.of("name", saved.getName(), "style", saved.getStyleKey())));
        return saved;
    }

    /**
     * Drops the presentation this account added - accent, font, footer, terms, bank details, signature -
     * and keeps the name and the base layout. The system template was never modified, so there is
     * nothing further to restore.
     */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public CustomInvoiceTemplate resetPresentation(TenantScope scope, long id) {
        scope.requireWriteAccess();
        CustomInvoiceTemplate template = findById(scope, id);
        Map<String, Object> before = Map.of("style", template.getStyleKey(), "accent", String.valueOf(template.getAccentColor()),
                "font", template.getFontKey());
        template.setAccentColor(null);
        template.resetPresentation();
        CustomInvoiceTemplate saved = templates.save(template);
        audit.record(AuditEventCommand.success(scope.requireCustomerId(), "RESET", "CUSTOM_TEMPLATE", "Invoice template", id,
                "Customer reset an invoice template to its plain layout.", before,
                Map.of("style", saved.getStyleKey(), "accent", "none")));
        return saved;
    }

    /** Availability of the account's own template; a hidden one cannot be picked for a new invoice. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public CustomInvoiceTemplate setActive(TenantScope scope, long id, boolean active) {
        scope.requireWriteAccess();
        CustomInvoiceTemplate template = findById(scope, id);
        if (active && template.isDeleted()) {
            throw new IllegalArgumentException("That design is retired: invoices were issued with it, so it is kept "
                    + "for their sake but cannot be used again. Create a new design instead.");
        }
        boolean before = template.isActive();
        if (before == active) return template;
        template.setActive(active);
        CustomInvoiceTemplate saved = templates.save(template);
        audit.record(AuditEventCommand.success(scope.requireCustomerId(), "UPDATE_STATUS", "CUSTOM_TEMPLATE",
                "Invoice template", id, active ? "Customer made an invoice template available again."
                        : "Customer hid an invoice template from new invoices.",
                Map.of("active", before), Map.of("active", active)));
        return saved;
    }

    private String uniqueCopyName(long ownerId, String sourceName) {
        String base = sourceName.length() > 90 ? sourceName.substring(0, 90) : sourceName;
        String candidate = base + " copy";
        int suffix = 2;
        while (templates.existsByCustomer_IdAndNameIgnoreCaseAndDeletedAtIsNull(ownerId, candidate) && suffix < 60) {
            candidate = base + " copy " + suffix++;
        }
        return candidate;
    }

    private Validated validate(CustomTemplateCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.name().trim().length() > 100)
            throw new IllegalArgumentException("Enter a custom template name of at most 100 characters.");
        BillTemplateStyle style;
        try { style = BillTemplateStyle.valueOf(command.styleKey() == null ? "CLASSIC" : command.styleKey().trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose a supported invoice template style."); }
        String color = command.accentColor() == null || command.accentColor().isBlank() ? null : command.accentColor().trim();
        if (color != null && !COLOR.matcher(color).matches()) throw new IllegalArgumentException("Accent color must be a six-digit hex color such as #2563EB.");
        return new Validated(command.name().trim(), style, color,
                choice(command.fontKey(), "DEFAULT", FONTS, "Choose a supported font for the template."),
                choice(command.layoutDensity(), "COMFORTABLE", DENSITIES, "Choose a supported layout density."),
                clip(command.bankDetails(), 600, "bank details"), clip(command.paymentTerms(), 240, "payment terms"),
                clip(command.footerText(), 600, "footer text"), clip(command.signatureLabel(), 120, "signature label"),
                clip(command.signatureName(), 120, "signature name"));
    }

    /** An unset choice falls back to the platform default; anything unknown is refused rather than guessed. */
    private static String choice(String value, String fallback, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) return fallback;
        String upper = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!allowed.contains(upper)) throw new IllegalArgumentException(message);
        return upper;
    }

    private static String clip(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Keep the " + label + " to " + max + " characters or fewer.");
        }
        return trimmed;
    }

    /** The accepted form of one submission: identity, base style, then the presentation choices. */
    private record Validated(String name, BillTemplateStyle style, String color, String font, String density,
            String bankDetails, String paymentTerms, String footerText, String signatureLabel, String signatureName) { }
}

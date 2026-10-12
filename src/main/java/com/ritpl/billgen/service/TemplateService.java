package com.ritpl.billgen.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.repository.BillTemplateRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.repository.SubscriptionPlanRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Bill/invoice templates are administered data: an administrator creates them and bundles them
 * into plans. Customer access is never granted here directly - it is derived from the purchased
 * plan in {@link SubscriptionService#accessibleTemplates}.
 */
@Service
public class TemplateService {
    private static final String NOT_FOUND = "Bill template not found.";

    private final BillTemplateRepository templates;
    private final SubscriptionPlanRepository plans;
    private final InvoiceRepository invoices;
    private final AuditService audit;

    public TemplateService(BillTemplateRepository templates, SubscriptionPlanRepository plans,
            InvoiceRepository invoices, AuditService audit) {
        this.templates = templates; this.plans = plans; this.invoices = invoices; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BillTemplate> findAll() { return templates.findAllByOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    public List<BillTemplate> findActive() { return templates.findByActiveTrueOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    public BillTemplate findById(long id) {
        return templates.findById(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public BillTemplate findByCode(String code) {
        if (code == null || code.isBlank()) throw new ResourceNotFoundException(NOT_FOUND);
        return templates.findByCodeIgnoreCase(code.trim()).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<BillTemplate> findByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return templates.findByIdInOrderBySortOrderAscNameAsc(List.copyOf(ids));
    }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public BillTemplate save(Long id, TemplateCommand command) {
        validate(command);
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = id == null ? templates.existsByCodeIgnoreCase(code) : templates.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicate) throw new IllegalArgumentException("That template code is already in use.");
        BillTemplate template = id == null ? new BillTemplate(code, command.name().trim(), command.style()) : findById(id);
        Map<String, Object> before = id == null ? null : Map.of("name", template.getName(), "active", template.isActive(), "style", template.getStyle().name());
        template.setCode(code);
        template.setName(command.name().trim());
        template.setDescription(blankToNull(command.description()));
        template.setStyle(command.style());
        template.setAccentColor(blankToNull(command.accentColor()));
        template.setActive(command.active());
        template.setSortOrder(command.sortOrder());
        BillTemplate saved = templates.save(template);
        audit.record(AuditEventCommand.success(null, id == null ? "CREATE" : "UPDATE", "TEMPLATE", "Bill template", saved.getId(),
                id == null ? "Admin created a bill template." : "Admin updated a bill template.", before,
                Map.of("name", saved.getName(), "active", saved.isActive(), "style", saved.getStyle().name())));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public void setActive(long id, boolean active) {
        BillTemplate template = findById(id);
        boolean before = template.isActive(); template.setActive(active);
        audit.record(AuditEventCommand.success(null, "UPDATE_STATUS", "TEMPLATE", "Bill template", id,
                "Admin changed bill template availability.", Map.of("active", before), Map.of("active", active)));
    }

    /**
     * Removes a template that is not in use. A template that is bundled with a plan or already
     * used by an invoice is hidden instead of deleted so issued invoices keep rendering.
     */
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public void delete(long id) {
        BillTemplate template = findById(id);
        if (invoices.countByTemplate_Id(id) > 0) {
            throw new IllegalArgumentException("This template has been used on invoices. Hide it instead of deleting it.");
        }
        if (plans.countByTemplate(id) > 0) {
            throw new IllegalArgumentException("Remove this template from its plans before deleting it.");
        }
        String name = template.getName(); templates.delete(template);
        audit.record(AuditEventCommand.success(null, "DELETE", "TEMPLATE", "Bill template", id,
                "Admin deleted an unused bill template.", Map.of("name", name), null));
    }

    private void validate(TemplateCommand c) {
        if (c == null || c.code() == null || c.code().isBlank() || c.code().trim().length() > 40
                || !c.code().trim().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("A template code may use letters, digits, dashes and underscores.");
        }
        if (c.name() == null || c.name().isBlank() || c.name().trim().length() > 100) {
            throw new IllegalArgumentException("Enter a template name of at most 100 characters.");
        }
        if (c.style() == null) throw new IllegalArgumentException("Choose a layout for the template.");
        String accent = blankToNull(c.accentColor());
        if (accent != null && !accent.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Accent colour must be a hex value such as #21826A.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    /** Accent colours offered by the template form. */
    public static Set<String> presetAccentColors() {
        return new LinkedHashSet<>(List.of("#17172f", "#21826a", "#3f51b5", "#c2410c"));
    }

    public static BillTemplateStyle defaultStyle() { return BillTemplateStyle.CLASSIC; }
}
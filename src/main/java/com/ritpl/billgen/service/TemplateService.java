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
    /** A double quote, written once so the HTML builder above stays readable. */
    private static final String QUOTE = "\"";
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

    /** Live templates for the Super Admin listing; retired rows are excluded. */
    @Transactional(readOnly = true)
    public List<BillTemplate> findLive() { return templates.findByDeletedAtIsNullOrderBySortOrderAscNameAsc(); }

    /** Selectable templates: active and not retired, because a retired row is hidden everywhere. */
    @Transactional(readOnly = true)
    public List<BillTemplate> findActive() { return templates.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscNameAsc(); }

    @Transactional(readOnly = true)
    public BillTemplate findById(long id) {
        return templates.findById(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public long activeCount() { return templates.countByActiveTrueAndDeletedAtIsNull(); }

    /** How many plans currently grant this template, shown next to every row of the listing. */
    @Transactional(readOnly = true)
    public long assignedPlanCount(long templateId) { return plans.countByTemplate(templateId); }

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
    public BillTemplate save(Long id, TemplateCommand command) { return save(id, command, "admin"); }

    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public BillTemplate save(Long id, TemplateCommand command, String actor) {
        validate(command);
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = id == null ? templates.existsByCodeIgnoreCase(code) : templates.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicate) throw new IllegalArgumentException("That template code is already in use.");
        if (command.name() != null && !command.name().isBlank()) {
            boolean duplicateName = id == null ? templates.existsByNameIgnoreCase(command.name().trim())
                    : templates.existsByNameIgnoreCaseAndIdNot(command.name().trim(), id);
            if (duplicateName) throw new IllegalArgumentException("A template with this name already exists.");
        }
        if (blankToNull(command.termsAndConditions()) != null && command.termsAndConditions().trim().length() > 2000) {
            throw new IllegalArgumentException("Terms and conditions may be at most 2000 characters.");
        }
        BillTemplate template = id == null ? new BillTemplate(code, command.name().trim(), command.style()) : findById(id);
        Map<String, Object> before = id == null ? null : Map.of("name", template.getName(), "active", template.isActive(), "style", template.getStyle().name());
        template.setCode(code);
        template.setName(command.name().trim());
        template.setDescription(blankToNull(command.description()));
        template.setStyle(command.style());
        template.setAccentColor(blankToNull(command.accentColor()));
        template.setActive(command.active());
        template.setSortOrder(command.sortOrder());
        template.setHeaderText(blankToNull(command.headerText()));
        template.setFooterText(blankToNull(command.footerText()));
        template.setPaymentTerms(blankToNull(command.paymentTerms()));
        template.setTermsAndConditions(blankToNull(command.termsAndConditions()));
        if (id != null) template.bumpVersion();
        template.setUpdatedBy(actor == null || actor.isBlank() ? "admin" : actor);
        BillTemplate saved = templates.save(template);
        audit.record(AuditEventCommand.success(null, id == null ? "CREATE" : "UPDATE", "TEMPLATE", "Bill template", saved.getId(),
                id == null ? "Admin created a bill template." : "Admin updated a bill template.", before,
                Map.of("name", saved.getName(), "active", saved.isActive(), "style", saved.getStyle().name(),
                        "version", saved.getVersion())));
        return saved;
    }

    /**
     * Deactivating a template only removes it from the picker for new invoices. Already issued
     * invoices keep rendering because their company and template details were snapshotted, so
     * deactivation can never break an existing invoice record.
     */
    @Transactional
    @CacheEvict(cacheNames = {"active-plans", "customer-templates", "dashboard"}, allEntries = true)
    public void setActive(long id, boolean active) {
        BillTemplate template = findById(id);
        if (active && template.isDeleted()) {
            throw new IllegalArgumentException("This template was retired. Its id stays reserved, so create a new "
                    + "template instead of reviving it.");
        }
        boolean before = template.isActive(); template.setActive(active);
        audit.record(AuditEventCommand.success(null, "UPDATE_STATUS", "TEMPLATE", "Bill template", id,
                active ? "Admin activated a bill template for new invoices."
                        : "Admin deactivated a bill template. Existing invoices keep their snapshot and still render.",
                Map.of("active", before), Map.of("active", active)));
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
        String name = template.getName();
        // Deletion is always soft, even for a template no invoice has used: the row is hidden and
        // deactivated so its id stays reserved, any historical reference still resolves, and
        // re-creating a template with the same code cannot silently inherit old invoices.
        template.setDeletedAt(java.time.Instant.now());
        template.setDeletedBy("admin");
        template.setActive(false);
        templates.save(template);
        audit.record(AuditEventCommand.success(null, "DELETE", "TEMPLATE", "Bill template", id,
                "Admin retired a bill template. Invoices already issued keep their template snapshot and still render.",
                Map.of("name", name), Map.of("retired", true)));
    }

    /**
     * A static preview of the layout with sample data. It is deliberately plain HTML so the same
     * markup can be reused by the print stylesheet and by the PDF renderer. Attributes are quoted
     * with single quotes because the fragment is assembled inside Java string literals.
     */
    public String previewHtml(TemplateCommand command, String sampleCompanyName) {
        String accent = blankToNull(command == null ? null : command.accentColor());
        String safeAccent = accent != null && accent.matches("#[0-9a-fA-F]{6}") ? accent : "#17172f";
        String style = command == null || command.style() == null ? "CLASSIC" : command.style().name();
        StringBuilder html = new StringBuilder();
        html.append("<div class='bill-preview bill-preview-").append(style.toLowerCase(Locale.ROOT))
                .append("' style='--preview-accent:").append(safeAccent).append("'>");
        html.append("<header class='bill-preview-head'><div><strong>")
                .append(escape(sampleCompanyName == null ? "Northstar Studio" : sampleCompanyName))
                .append("</strong><small>Studio Lane 14 · Bengaluru 560001 · GSTIN 29ABCDE1234F1Z5</small></div>")
                .append("<div class='bill-preview-mark'>TAX INVOICE</div></header>");
        if (blankToNull(command == null ? null : command.headerText()) != null) {
            html.append("<p class='bill-preview-header'>").append(escape(command.headerText())).append("</p>");
        }
        html.append("<div class='bill-preview-meta'><span><small>Invoice</small>INV-001042</span>")
                .append("<span><small>Issued</small>").append(java.time.LocalDate.now()).append("</span>")
                .append("<span><small>Due</small>").append(java.time.LocalDate.now().plusDays(14)).append("</span></div>");
        html.append("<table class='bill-preview-lines'><thead><tr><th>Description</th><th>Qty</th><th>Unit</th>")
                .append("<th>Tax</th><th>Amount</th></tr></thead><tbody>")
                .append("<tr><td>Design sprint</td><td>4</td><td>12,500.00</td><td>18%</td><td>59,000.00</td></tr>")
                .append("<tr><td>Managed hosting</td><td>1</td><td>8,000.00</td><td>18%</td><td>9,440.00</td></tr>")
                .append("</tbody></table>");
        html.append("<div class='bill-preview-totals'><span>Subtotal <b>₹ 56,500.00</b></span>")
                .append("<span>Tax <b>₹ 10,170.00</b></span><span>Discount <b>−₹ 1,500.00</b></span>")
                .append("<span class='bill-preview-total'>Total <b>₹ 65,170.00</b></span></div>");
        if (blankToNull(command == null ? null : command.paymentTerms()) != null) {
            html.append("<p class='bill-preview-terms'><strong>Payment terms</strong>")
                    .append(escape(command.paymentTerms())).append("</p>");
        }
        if (blankToNull(command == null ? null : command.termsAndConditions()) != null) {
            html.append("<p class='bill-preview-terms'><strong>Terms and conditions</strong>")
                    .append(escape(command.termsAndConditions())).append("</p>");
        }
        if (blankToNull(command == null ? null : command.footerText()) != null) {
            html.append("<footer class='bill-preview-foot'>").append(escape(command.footerText())).append("</footer>");
        }
        html.append("</div>");
        return html.toString();
    }

    /** Escapes the five characters that could otherwise break out of text or an attribute. */
    private String escape(String value) {
        if (value == null) return "";
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        escaped = escaped.replace(QUOTE, "&quot;").replace("'", "&#39;");
        return escaped.replace("\n", "<br>");
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
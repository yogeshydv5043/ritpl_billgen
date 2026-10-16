package com.ritpl.billgen.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.FaqType;
import com.ritpl.billgen.repository.FaqRepository;
import com.ritpl.billgen.repository.FaqTypeRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The FAQ groupings, which are what makes the FAQ page a menu instead of a flat list. The code is the
 * part a link is built from, so it is derived from the name when the form leaves it blank, kept unique
 * without regard to case, and never edited in place once a question points at it.
 *
 * <p>A type with questions in it cannot be deleted. That is enforced twice on purpose: the foreign key
 * refuses it at the database, and the service refuses it with a sentence that tells the administrator
 * what to do instead.</p>
 */
@Service
public class FaqTypeService {

    private final FaqTypeRepository types;
    private final FaqRepository faqs;
    private final AuditService audit;

    public FaqTypeService(FaqTypeRepository types, FaqRepository faqs, AuditService audit) {
        this.types = types; this.faqs = faqs; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FaqType> list() { return types.findAllByOrderByDisplayOrderAscTypeNameAsc(); }

    @Transactional(readOnly = true)
    public List<FaqType> published() { return types.findByStatusOrderByDisplayOrderAscTypeNameAsc(ContentStatus.ACTIVE); }

    /** How many questions each type holds, for the admin table and for the delete guard. */
    @Transactional(readOnly = true)
    public Map<Long, Long> questionCounts() {
        Map<Long, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : faqs.countsByType()) counts.put((Long) row[0], (Long) row[1]);
        return counts;
    }

    @Transactional(readOnly = true)
    public long questionCount(Long typeId) { return faqs.countByFaqType_Id(typeId); }

    /** Only creation lives here: the code cannot be re-derived on an edit, so updates use {@link #update}. */
    @Transactional
    public FaqType save(TenantScope scope, FaqTypeCommand command) {
        ConfigurationRules.requireAdmin(scope);
        String name = ConfigurationRules.required(command.typeName(), 150, "Type name");
        String code = deriveCode(command.typeCode(), name);
        if (types.existsByTypeCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A FAQ type with the code " + code + " already exists.");
        }
        FaqType row = new FaqType();
        row.setTypeName(name);
        row.setTypeCode(code);
        row.setDescription(ConfigurationRules.optional(command.description(), 500, "Description"));
        row.setDisplayOrder(ConfigurationRules.bounded(command.displayOrder(), 0, 0, 9999, "Display order"));
        row.setStatus(command.status() == null ? ContentStatus.INACTIVE : command.status());
        row.recordAuthor("administrator");
        FaqType saved = types.save(row);
        audit.record(AuditEventCommand.success(null, "CREATE", "FAQ_TYPE", saved.getTypeName(), saved.getId(),
                "FAQ type " + saved.getTypeCode() + " added.", null,
                Map.of("typeName", saved.getTypeName(), "typeCode", saved.getTypeCode())));
        return saved;
    }

    /**
     * The name and code can be renamed; the ordering, status and description can be changed. What is not
     * offered is a code change once questions exist, because the public page links by code and a renamed
     * code would break links the administrator did not intend to break.
     */
    @Transactional
    public FaqType update(TenantScope scope, Long id, String typeName, String description,
            Integer displayOrder, ContentStatus status) {
        ConfigurationRules.requireAdmin(scope);
        FaqType row = types.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ type not found."));
        Map<String, Object> before = Map.of("typeName", row.getTypeName(), "status", row.getStatus().name());
        row.setTypeName(ConfigurationRules.required(typeName, 150, "Type name"));
        row.setDescription(ConfigurationRules.optional(description, 500, "Description"));
        row.setDisplayOrder(ConfigurationRules.bounded(displayOrder, row.getDisplayOrder(), 0, 9999, "Display order"));
        if (status != null) row.setStatus(status);
        row.recordEditor("administrator");
        FaqType saved = types.save(row);
        audit.record(AuditEventCommand.success(null, "UPDATE", "FAQ_TYPE", saved.getTypeName(), saved.getId(),
                "FAQ type updated.", before,
                Map.of("typeName", saved.getTypeName(), "status", saved.getStatus().name())));
        return saved;
    }

    @Transactional
    public void delete(TenantScope scope, Long id) {
        ConfigurationRules.requireAdmin(scope);
        FaqType row = types.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ type not found."));
        long held = faqs.countByFaqType_Id(id);
        if (held > 0) {
            throw new IllegalArgumentException("This type has " + held
                    + " question(s). Move them to another type or delete them first.");
        }
        types.delete(row);
        audit.record(AuditEventCommand.success(null, "DELETE", "FAQ_TYPE", row.getTypeName(), row.getId(),
                "FAQ type " + row.getTypeCode() + " deleted.", Map.of("typeName", row.getTypeName()), null));
    }

    private static String deriveCode(String requested, String name) {
        String base = (requested == null || requested.isBlank()) ? name : requested.trim();
        String code = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (code.length() > 48) code = code.substring(0, 48);
        if (code.isBlank()) throw new IllegalArgumentException("A usable FAQ type code could not be derived from that name.");
        return code;
    }
}

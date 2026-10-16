package com.ritpl.billgen.service;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.Faq;
import com.ritpl.billgen.entity.FaqType;
import com.ritpl.billgen.repository.FaqRepository;
import com.ritpl.billgen.repository.FaqTypeRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Questions and answers. An answer is sanitized HTML for the same reason a policy is: it is written by an
 * administrator in a browser, stored, and rendered to strangers.
 *
 * <p>A question belongs to a type or it does not exist - there is no "uncategorized" row to accumulate
 * orphans in, which is what the {@code NOT NULL} and the {@code ON DELETE RESTRICT} in the schema say as
 * well.</p>
 */
@Service
public class FaqService {

    private static final int MAX_ANSWER = 4000;

    private final FaqRepository faqs;
    private final FaqTypeRepository types;
    private final AuditService audit;

    public FaqService(FaqRepository faqs, FaqTypeRepository types, AuditService audit) {
        this.faqs = faqs; this.types = types; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Faq> search(String term, Long typeId, ContentStatus status, Pageable pageable) {
        return faqs.search(term, typeId, status, pageable);
    }

    /**
     * The public list for a type code, or for every published type when no code is given. The rows come
     * back as records rather than entities because the page needs the type's name and code, and an
     * entity handed to the view would resolve that through a lazy proxy after the transaction has closed.
     */
    @Transactional(readOnly = true)
    public List<PublicFaq> published(String typeCode, int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        List<FaqType> wanted;
        if (typeCode == null || typeCode.isBlank()) {
            wanted = types.findByStatusOrderByDisplayOrderAscTypeNameAsc(ContentStatus.ACTIVE);
        } else {
            wanted = types.findByTypeCodeIgnoreCase(typeCode.trim())
                    .filter(FaqType::isActive).map(List::of).orElse(List.of());
        }
        List<PublicFaq> found = new java.util.ArrayList<>();
        for (FaqType type : wanted) {
            for (Faq faq : faqs.findPublishedForType(type, ContentStatus.ACTIVE, Pageable.ofSize(size))) {
                found.add(new PublicFaq(faq.getId(), type.getTypeCode(), type.getTypeName(), faq.getQuestion(),
                        faq.getAnswer()));
            }
        }
        return found;
    }

    /** One question as the public page needs it: no status, no timestamps, no audit columns. */
    public record PublicFaq(Long id, String typeCode, String typeName, String question, String answer) { }

    @Transactional
    public Faq save(TenantScope scope, Long id, FaqCommand command) {
        ConfigurationRules.requireAdmin(scope);
        if (command.faqTypeId() == null) throw new IllegalArgumentException("Choose the FAQ type this question belongs to.");
        FaqType type = types.findById(command.faqTypeId())
                .orElseThrow(() -> new IllegalArgumentException("FAQ type not found."));
        Faq row = id == null ? new Faq() : faqs.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ not found."));
        boolean created = row.getId() == null;
        String answer = RichTextSanitizer.sanitize(command.answer());
        if (answer.replaceAll("<[^>]*>", "").trim().isEmpty()) {
            throw new IllegalArgumentException("An answer cannot be saved with no text in it.");
        }
        row.setQuestion(ConfigurationRules.required(command.question(), 500, "Question"));
        row.setAnswer(answer);
        row.setFaqType(type);
        row.setDisplayOrder(ConfigurationRules.bounded(command.displayOrder(), 0, 0, 9999, "Display order"));
        row.setStatus(command.status() == null ? ContentStatus.INACTIVE : command.status());
        if (created) row.recordAuthor("administrator"); else row.recordEditor("administrator");
        Faq saved = faqs.save(row);
        audit.record(AuditEventCommand.success(null, created ? "CREATE" : "UPDATE", "FAQ", saved.getQuestion(),
                saved.getId(), (created ? "Added" : "Updated") + " a FAQ in " + type.getTypeName() + " ("
                        + (saved.getStatus() == ContentStatus.ACTIVE ? "published" : "not published") + ").",
                null, Map.of("question", saved.getQuestion(), "faqType", type.getTypeCode(),
                        "status", saved.getStatus().name())));
        return saved;
    }

    @Transactional
    public Faq setStatus(TenantScope scope, Long id, ContentStatus status) {
        ConfigurationRules.requireAdmin(scope);
        Faq row = faqs.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ not found."));
        ContentStatus before = row.getStatus();
        row.recordEditor("administrator");
        row.setStatus(status);
        Faq saved = faqs.save(row);
        audit.record(AuditEventCommand.success(null, status == ContentStatus.ACTIVE ? "PUBLISH" : "WITHDRAW",
                "FAQ", saved.getQuestion(), saved.getId(),
                "FAQ " + (status == ContentStatus.ACTIVE ? "published." : "withdrawn from the public site."),
                Map.of("status", before.name()), Map.of("status", status.name())));
        return saved;
    }

    /** Content rows are not tenant data, so a FAQ is removed outright and the audit record is the history. */
    @Transactional
    public void delete(TenantScope scope, Long id) {
        ConfigurationRules.requireAdmin(scope);
        Faq row = faqs.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ not found."));
        Map<String, Object> before = Map.of("question", row.getQuestion(), "faqType", row.getFaqType().getTypeCode(),
                "status", row.getStatus().name());
        faqs.delete(row);
        audit.record(AuditEventCommand.success(null, "DELETE", "FAQ", row.getQuestion(), row.getId(),
                "FAQ deleted.", before, null));
    }
}

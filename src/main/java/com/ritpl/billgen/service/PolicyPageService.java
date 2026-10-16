package com.ritpl.billgen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.PolicyPage;
import com.ritpl.billgen.entity.PolicyType;
import com.ritpl.billgen.repository.PolicyPageRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The three legal documents. Each type holds one row that is overwritten in place, because "the refund
 * policy" is a thing the company has one of, and the alternative - a list of versions an administrator
 * has to keep straight - produces a published draft and an archived final.
 *
 * <p>Content goes through {@link RichTextSanitizer} on the way in, so what is stored is already what is
 * safe to render; the read path then has no escaping decisions left to make.</p>
 */
@Service
public class PolicyPageService {

    private final PolicyPageRepository pages;
    private final AuditService audit;

    public PolicyPageService(PolicyPageRepository pages, AuditService audit) { this.pages = pages; this.audit = audit; }

    /** Every type in order, with any saved content merged in, so the admin list has one row per policy. */
    @Transactional(readOnly = true)
    public List<PolicyRow> overview() {
        Map<PolicyType, PolicyPage> saved = new LinkedHashMap<>();
        for (PolicyPage page : pages.findAll()) saved.put(page.getPolicyType(), page);
        List<PolicyRow> rows = new ArrayList<>();
        for (PolicyType type : PolicyType.values()) {
            PolicyPage page = saved.get(type);
            rows.add(new PolicyRow(type, page == null ? null : page.getId(),
                    page == null ? null : page.getHeading(),
                    page == null ? null : page.getMetaTitle(),
                    page == null ? null : page.getMetaDescription(),
                    page == null ? null : page.getPageContent(),
                    page == null ? ContentStatus.INACTIVE : page.getStatus(),
                    page == null ? null : page.getUpdatedAt()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Optional<PolicyPage> find(PolicyType type) { return pages.findByPolicyType(type); }

    /** What the public site can serve: published rows only, with the content already sanitized. */
    @Transactional(readOnly = true)
    public List<PolicyPage> published() { return pages.findByStatusOrderByPolicyTypeAsc(ContentStatus.ACTIVE); }

    @Transactional
    public PolicyPage save(TenantScope scope, PolicyPageCommand command) {
        ConfigurationRules.requireAdmin(scope);
        if (command.policyType() == null) throw new IllegalArgumentException("Choose which policy this is.");
        PolicyPage row = pages.findByPolicyType(command.policyType()).orElseGet(() -> {
            PolicyPage fresh = new PolicyPage();
            fresh.setPolicyType(command.policyType());
            return fresh;
        });
        boolean created = row.getCreatedAt() == null;

        String content = RichTextSanitizer.sanitize(command.pageContent());
        if (content.replaceAll("<[^>]*>", "").trim().isEmpty()) {
            throw new IllegalArgumentException("A policy cannot be saved with no text in it.");
        }
        if (created) row.recordAuthor("administrator"); else row.recordEditor("administrator");
        row.setHeading(ConfigurationRules.required(command.heading(), 200, "Heading"));
        row.setMetaTitle(ConfigurationRules.optional(command.metaTitle(), 200, "Meta title"));
        row.setMetaDescription(ConfigurationRules.optional(command.metaDescription(), 500, "Meta description"));
        row.setPageContent(content);
        ContentStatus status = command.status() == null ? row.getStatus() : command.status();
        row.setStatus(status == null ? ContentStatus.INACTIVE : status);
        PolicyPage saved = pages.save(row);
        audit.record(AuditEventCommand.success(null, created ? "CREATE" : "UPDATE", "POLICY_PAGE",
                saved.getPolicyType().getDisplayName(), saved.getId(),
                (created ? "Administrator created the " : "Administrator updated the ") + saved.getPolicyType().getDisplayName()
                        + " (" + plainTextLength(content) + " characters, " + statusLabel(saved) + ").",
                null, Map.of("policyType", saved.getPolicyType().name(),
                        "heading", saved.getHeading(), "characters", plainTextLength(content),
                        "status", saved.getStatus().name())));
        return saved;
    }

    /**
     * Publishing is the one decision that changes what the public sees, so it is its own operation with
     * its own audit action rather than a checkbox on the save.
     */
    @Transactional
    public PolicyPage setStatus(TenantScope scope, PolicyType type, ContentStatus status) {
        ConfigurationRules.requireAdmin(scope);
        PolicyPage row = pages.findByPolicyType(type)
                .orElseThrow(() -> new IllegalArgumentException("Write the " + type.getDisplayName()
                        + " before publishing it."));
        String previous = row.getStatus().name();
        row.recordEditor("administrator");
        row.setStatus(status);
        PolicyPage saved = pages.save(row);
        audit.record(AuditEventCommand.success(null, status == ContentStatus.ACTIVE ? "PUBLISH" : "WITHDRAW",
                "POLICY_PAGE", saved.getPolicyType().getDisplayName(), saved.getId(),
                saved.getPolicyType().getDisplayName() + " was "
                        + (status == ContentStatus.ACTIVE ? "published." : "withdrawn from the public site."),
                Map.of("status", previous), Map.of("status", status.name())));
        return saved;
    }

    private static String statusLabel(PolicyPage page) {
        return page.getStatus() == ContentStatus.ACTIVE ? "published" : "not published";
    }

    private static int plainTextLength(String html) {
        return html.replaceAll("<[^>]*>", " ").replaceAll("\s+", " ").trim().length();
    }

    /** One row of the administrator's policy list: the type plus whatever has been saved for it. */
    public record PolicyRow(PolicyType policyType, Long id, String heading, String metaTitle,
            String metaDescription, String content, ContentStatus status, java.time.Instant updatedAt) {

        public boolean exists() { return id != null; }
        public int contentLength() { return content == null ? 0 : plainTextLength(content); }
        /** Whether a public reader can see this document right now. */
        public boolean publiclyVisible() { return status == ContentStatus.ACTIVE; }
    }
}

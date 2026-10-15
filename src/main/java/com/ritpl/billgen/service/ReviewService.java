package com.ritpl.billgen.service;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.Review;
import com.ritpl.billgen.repository.ReviewRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Customer testimonials. There are two fates worse than no reviews, and both are prevented here: an
 * unpublished one shown to visitors, and a deleted one silently vanished from a page that said
 * "what our customers say" yesterday. So a review is soft-deleted - the row keeps a {@code deletedAt}
 * and the audit trail says who removed it and when.
 *
 * <p>The rating is not trusted, only stored: one to five, enforced here and by a database check,
 * because a page that displays a 41-star review is a page somebody edited by hand.</p>
 */
@Service
public class ReviewService {

    private static final int MAX_CONTENT = 2000;

    private final ReviewRepository reviews;
    private final AuditService audit;

    public ReviewService(ReviewRepository reviews, AuditService audit) { this.reviews = reviews; this.audit = audit; }

    @Transactional(readOnly = true)
    public Page<Review> search(String term, ContentStatus status, Integer minRating, Pageable pageable) {
        return reviews.search(term, status, minRating == null ? null : minRating.shortValue(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Review> published(int limit) {
        return reviews.findPublished(ContentStatus.ACTIVE, Pageable.ofSize(Math.max(1, Math.min(limit, 100))));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.of("total", reviews.countByDeletedAtIsNull(),
                "published", reviews.countByStatusAndDeletedAtIsNull(ContentStatus.ACTIVE));
    }

    @Transactional
    public Review save(TenantScope scope, Long id, ReviewCommand command) {
        ConfigurationRules.requireAdmin(scope);
        Review row = id == null ? new Review() : reviews.findById(id).orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (row.getDeletedAt() != null) throw new IllegalArgumentException("Restore the review before editing it.");
        boolean created = row.getId() == null;

        row.setReviewerName(ConfigurationRules.required(command.reviewerName(), 150, "Reviewer name"));
        row.setReviewContent(ConfigurationRules.required(command.reviewContent(), MAX_CONTENT, "Review text"));
        int rating = ConfigurationRules.bounded(command.rating(), 5, 1, 5, "Rating");
        row.setRating(rating);
        row.setLocation(ConfigurationRules.optional(command.location(), 120, "Location"));
        row.setDesignation(ConfigurationRules.optional(command.designation(), 120, "Designation"));
        row.setProfileImageUrl(ConfigurationRules.image(command.profileImageUrl(), 500, "Profile image URL"));
        row.setDisplayOrder(ConfigurationRules.bounded(command.displayOrder(), 0, 0, 9999, "Display order"));
        row.setStatus(command.status() == null ? ContentStatus.INACTIVE : command.status());
        if (created) row.recordAuthor("administrator"); else row.recordEditor("administrator");
        Review saved = reviews.save(row);
        audit.record(AuditEventCommand.success(null, created ? "CREATE" : "UPDATE", "REVIEW",
                saved.getReviewerName(), saved.getId(),
                (created ? "Added" : "Updated") + " a review rated " + saved.getRating() + "/5 ("
                        + (saved.getStatus() == ContentStatus.ACTIVE ? "published" : "not published") + ").",
                null, Map.of("reviewerName", saved.getReviewerName(), "rating", (int) saved.getRating(),
                        "status", saved.getStatus().name())));
        return saved;
    }

    @Transactional
    public Review setStatus(TenantScope scope, Long id, ContentStatus status) {
        ConfigurationRules.requireAdmin(scope);
        Review row = reviews.findById(id).orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (row.getDeletedAt() != null) throw new IllegalArgumentException("Restore the review before changing its status.");
        ContentStatus before = row.getStatus();
        row.recordEditor("administrator");
        row.setStatus(status);
        Review saved = reviews.save(row);
        audit.record(AuditEventCommand.success(null, status == ContentStatus.ACTIVE ? "PUBLISH" : "WITHDRAW",
                "REVIEW", saved.getReviewerName(), saved.getId(),
                "Review by " + saved.getReviewerName() + " "
                        + (status == ContentStatus.ACTIVE ? "published." : "withdrawn from the public site."),
                Map.of("status", before.name()), Map.of("status", status.name())));
        return saved;
    }

    /** Removes it from the public page and from the administrator's list, without losing the row. */
    @Transactional
    public void delete(TenantScope scope, Long id) {
        ConfigurationRules.requireAdmin(scope);
        Review row = reviews.findById(id).orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (row.getDeletedAt() != null) return;
        row.recordEditor("administrator");
        row.setStatus(ContentStatus.INACTIVE);
        row.markDeleted("administrator");
        reviews.save(row);
        audit.record(AuditEventCommand.success(null, "DELETE", "REVIEW", row.getReviewerName(), row.getId(),
                "Review removed from the public page; the record is kept.",
                Map.of("reviewerName", row.getReviewerName(), "status", "visible"),
                Map.of("reviewerName", row.getReviewerName(), "status", "deleted")));
    }

    @Transactional
    public Review restore(TenantScope scope, Long id) {
        ConfigurationRules.requireAdmin(scope);
        Review row = reviews.findById(id).orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (row.getDeletedAt() == null) return row;
        row.recordEditor("administrator");
        row.restore();
        Review saved = reviews.save(row);
        audit.record(AuditEventCommand.success(null, "RESTORE", "REVIEW", saved.getReviewerName(), saved.getId(),
                "Review restored to the administrator's list. It stays unpublished until published.",
                Map.of("status", "deleted"), Map.of("status", saved.getStatus().name())));
        return saved;
    }
}

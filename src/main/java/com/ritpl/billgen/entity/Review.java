package com.ritpl.billgen.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A testimonial an administrator has chosen to show. Deletion is soft: a review that appeared on the
 * site stays in the table so the record of what was published cannot be quietly rewritten.
 */
@Entity
@Table(name = "reviews")
public class Review extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "reviewer_name", nullable = false, length = 150) private String reviewerName;
    @Column(name = "review_content", nullable = false, length = 2000) private String reviewContent;
    @Column(nullable = false) private short rating = 5;
    @Column(length = 120) private String location;
    @Column(length = 120) private String designation;
    @Column(name = "profile_image_url", length = 500) private String profileImageUrl;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ContentStatus status = ContentStatus.INACTIVE;

    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "deleted_by", length = 190) private String deletedBy;

    public Long getId() { return id; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String v) { this.reviewerName = v; }
    public String getReviewContent() { return reviewContent; }
    public void setReviewContent(String v) { this.reviewContent = v; }
    public int getRating() { return rating; }
    public void setRating(int v) { this.rating = (short) v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { this.designation = v; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String v) { this.profileImageUrl = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus v) { this.status = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public boolean isDeleted() { return deletedAt != null; }
    public void markDeleted(String actor) { this.deletedAt = Instant.now(); this.deletedBy = actor; }
    public void restore() { this.deletedAt = null; this.deletedBy = null; }
}

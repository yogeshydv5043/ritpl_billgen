package com.ritpl.billgen.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * The four audit columns every configuration row carries: who made it, when, and the last hand and
 * hour to change it. Timestamps are stamped by the persistence layer rather than by each service, so a
 * row cannot be written without them, and they are instants because the database is UTC by policy -
 * the application converts for display, never for storage.
 *
 * <p>The actor is set by the service that owns the write, since only it knows whether an administrator
 * or a scheduled job moved the row.</p>
 */
@MappedSuperclass
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", length = 190) private String createdBy;
    @Column(name = "updated_by", length = 190) private String updatedBy;

    @PrePersist void stampCreation() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void stampUpdate() { updatedAt = Instant.now(); }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }

    /** Records the actor for a row that has not been written yet; an insert then has both halves. */
    public void recordAuthor(String actor) { createdBy = actor; updatedBy = actor; }

    /** Records the actor for a row that already exists, leaving its original author alone. */
    public void recordEditor(String actor) { updatedBy = actor; }
}

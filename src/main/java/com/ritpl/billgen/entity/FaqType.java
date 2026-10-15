package com.ritpl.billgen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A grouping for FAQs - "Billing", "Templates", and so on. The code is the handle used in links. */
@Entity
@Table(name = "faq_types")
public class FaqType extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "type_name", nullable = false, length = 150) private String typeName;
    @Column(name = "type_code", nullable = false, length = 48) private String typeCode;
    @Column(length = 500) private String description;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ContentStatus status = ContentStatus.ACTIVE;

    public Long getId() { return id; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String v) { this.typeName = v; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String v) { this.typeCode = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus v) { this.status = v; }
    public boolean isActive() { return status.isActive(); }
}

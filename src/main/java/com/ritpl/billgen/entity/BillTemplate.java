package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A bill/invoice template. Templates are created and maintained by an administrator, bundled
 * into subscription plans and unlocked for a customer account through its purchased plan.
 */
@Entity
@Table(name = "bill_templates")
public class BillTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "style_key", nullable = false, length = 20)
    private BillTemplateStyle style = BillTemplateStyle.CLASSIC;
    @Column(name = "accent_color", length = 7)
    private String accentColor;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BillTemplate() { }

    public BillTemplate(String code, String name, BillTemplateStyle style) {
        this.code = code;
        this.name = name;
        this.style = style == null ? BillTemplateStyle.CLASSIC : style;
    }

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public BillTemplateStyle getStyle() { return style; }
    public void setStyle(BillTemplateStyle value) { style = value == null ? BillTemplateStyle.CLASSIC : value; }
    public String getStyleCode() { return style == null ? BillTemplateStyle.CLASSIC.name() : style.name(); }
    public String getStyleCssClass() { return style == null ? BillTemplateStyle.CLASSIC.getCssClass() : style.getCssClass(); }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String value) { accentColor = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { sortOrder = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return name; }
}
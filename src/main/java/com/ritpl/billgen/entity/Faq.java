package com.ritpl.billgen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A question and its answer, filed under a {@link FaqType}. The answer is stored as sanitized HTML,
 * which is what lets an administrator format a list inside an answer without the page trusting it.
 */
@Entity
@Table(name = "faqs")
public class Faq extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faq_type_id", nullable = false) private FaqType faqType;

    @Column(nullable = false, length = 500) private String question;
    @Column(nullable = false, length = 4000) private String answer;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ContentStatus status = ContentStatus.ACTIVE;

    public Long getId() { return id; }
    public FaqType getFaqType() { return faqType; }
    public void setFaqType(FaqType v) { this.faqType = v; }
    public Long getFaqTypeId() { return faqType == null ? null : faqType.getId(); }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
    public String getAnswer() { return answer; }
    public void setAnswer(String v) { this.answer = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus v) { this.status = v; }
    public boolean isActive() { return status.isActive(); }
}

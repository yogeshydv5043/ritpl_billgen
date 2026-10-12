package com.ritpl.billgen.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 160) private String tagline;
    @Column(length = 500) private String description;
    @Column(name = "monthly_price", nullable = false, precision = 14, scale = 2) private BigDecimal monthlyPrice;
    @Column(name = "annual_price", nullable = false, precision = 14, scale = 2) private BigDecimal annualPrice;
    @Column(nullable = false, length = 3) private String currency="INR";
    @Column(name = "max_customers", nullable = false) private int maxCustomers;
    @Column(name = "max_monthly_invoices", nullable = false) private int maxMonthlyInvoices;
    @Column(name = "max_products", nullable = false) private int maxProducts = 100;
    @Column(name = "max_custom_templates", nullable = false) private int maxCustomTemplates = 3;
    @Column(nullable = false, columnDefinition = "text") private String features;
    @Column(nullable = false) private boolean featured;
    @Column(nullable = false) private boolean active=true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    /**
     * The bill templates included with this plan. A customer can only issue invoices with the
     * templates of the plan it purchased.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "plan_templates",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "template_id"))
    private Set<BillTemplate> templates = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected SubscriptionPlan() { }
    public SubscriptionPlan(String code,String name,String tagline,String description,BigDecimal monthlyPrice,BigDecimal annualPrice,String currency,int maxCustomers,int maxMonthlyInvoices,String features,boolean featured,int sortOrder){
        this.code=code;this.name=name;this.tagline=tagline;this.description=description;this.monthlyPrice=monthlyPrice;this.annualPrice=annualPrice;this.currency=currency;this.maxCustomers=maxCustomers;this.maxMonthlyInvoices=maxMonthlyInvoices;this.features=features;this.featured=featured;this.sortOrder=sortOrder;
    }
    @PrePersist void onCreate(){LocalDateTime n=LocalDateTime.now();createdAt=n;updatedAt=n;} @PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getTagline(){return tagline;} public void setTagline(String v){tagline=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getMonthlyPrice(){return monthlyPrice;} public void setMonthlyPrice(BigDecimal v){monthlyPrice=v;} public BigDecimal getAnnualPrice(){return annualPrice;} public void setAnnualPrice(BigDecimal v){annualPrice=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public int getMaxCustomers(){return maxCustomers;} public void setMaxCustomers(int v){maxCustomers=v;}
    public int getMaxMonthlyInvoices(){return maxMonthlyInvoices;} public void setMaxMonthlyInvoices(int v){maxMonthlyInvoices=v;}
    public int getMaxProducts(){return maxProducts;} public void setMaxProducts(int v){maxProducts=v;}
    public int getMaxCustomTemplates(){return maxCustomTemplates;} public void setMaxCustomTemplates(int v){maxCustomTemplates=v;}
    public String getFeatures(){return features;} public void setFeatures(String v){features=v;}
    public boolean isFeatured(){return featured;} public void setFeatured(boolean v){featured=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
    public Set<BillTemplate> getTemplates(){return templates;} public void setTemplates(Set<BillTemplate> v){templates=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);}
    /** The ids of every template bundled with this plan. */
    public Set<Long> getTemplateIds(){java.util.Set<Long> ids=new java.util.LinkedHashSet<>();for(BillTemplate t:templates){ids.add(t.getId());}return ids;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}

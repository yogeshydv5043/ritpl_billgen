package com.ritpl.billgen.web.form;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import com.ritpl.billgen.entity.SubscriptionPlan;
import com.ritpl.billgen.service.PlanCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PlanForm {
    @NotBlank @Size(max=40) @Pattern(regexp="[A-Za-z0-9_-]+") private String code;
    @NotBlank @Size(max=100) private String name;
    @Size(max=160) private String tagline;
    @Size(max=500) private String description;
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal monthlyPrice = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal annualPrice = BigDecimal.ZERO;
    @NotBlank @Pattern(regexp="[A-Za-z]{3}") private String currency = "INR";
    @Min(1) private int maxCustomers = 25;
    @Min(1) private int maxMonthlyInvoices = 50;
    @NotBlank @Size(max=5000) private String features;
    private boolean featured;
    private boolean active = true;
    private int sortOrder = 10;
    /** Bill templates included with this plan; a customer may use these and nothing else. */
    private Set<Long> templateIds = new LinkedHashSet<>();

    public static PlanForm from(SubscriptionPlan p) {
        PlanForm f = new PlanForm();
        f.code = p.getCode(); f.name = p.getName(); f.tagline = p.getTagline(); f.description = p.getDescription();
        f.monthlyPrice = p.getMonthlyPrice(); f.annualPrice = p.getAnnualPrice(); f.currency = p.getCurrency();
        f.maxCustomers = p.getMaxCustomers(); f.maxMonthlyInvoices = p.getMaxMonthlyInvoices();
        f.features = p.getFeatures(); f.featured = p.isFeatured(); f.active = p.isActive(); f.sortOrder = p.getSortOrder();
        f.templateIds = new LinkedHashSet<>(p.getTemplateIds());
        return f;
    }

    public PlanCommand toCommand() {
        return new PlanCommand(code, name, tagline, description, monthlyPrice, annualPrice, currency,
                maxCustomers, maxMonthlyInvoices, features, featured, active, sortOrder, templateIds);
    }

    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getTagline(){return tagline;} public void setTagline(String v){tagline=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getMonthlyPrice(){return monthlyPrice;} public void setMonthlyPrice(BigDecimal v){monthlyPrice=v;}
    public BigDecimal getAnnualPrice(){return annualPrice;} public void setAnnualPrice(BigDecimal v){annualPrice=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public int getMaxCustomers(){return maxCustomers;} public void setMaxCustomers(int v){maxCustomers=v;}
    public int getMaxMonthlyInvoices(){return maxMonthlyInvoices;} public void setMaxMonthlyInvoices(int v){maxMonthlyInvoices=v;}
    public String getFeatures(){return features;} public void setFeatures(String v){features=v;}
    public boolean isFeatured(){return featured;} public void setFeatured(boolean v){featured=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
    public Set<Long> getTemplateIds(){return templateIds;} public void setTemplateIds(Set<Long> v){templateIds=v;}
}
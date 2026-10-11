package com.ritpl.billgen.api;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.SubscriptionPlan;

public record PlanResponse(Long id,String code,String name,String tagline,String description,
        BigDecimal monthlyPrice,BigDecimal annualPrice,String currency,int maxCustomers,
        int maxMonthlyInvoices,String features,boolean featured) {
    public static PlanResponse from(SubscriptionPlan p){return new PlanResponse(p.getId(),p.getCode(),p.getName(),p.getTagline(),p.getDescription(),p.getMonthlyPrice(),p.getAnnualPrice(),p.getCurrency(),p.getMaxCustomers(),p.getMaxMonthlyInvoices(),p.getFeatures(),p.isFeatured());}
}

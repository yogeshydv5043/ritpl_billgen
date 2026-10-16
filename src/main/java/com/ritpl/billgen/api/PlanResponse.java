package com.ritpl.billgen.api;

import java.math.BigDecimal;
import java.util.List;
import com.ritpl.billgen.entity.SubscriptionPlan;

public record PlanResponse(Long id, String code, String name, String tagline, String description,
        BigDecimal monthlyPrice, BigDecimal annualPrice, String currency, int maxCustomers,
        int maxMonthlyInvoices, int maxProducts, int maxCustomTemplates, String features,
        boolean featured, List<TemplateResponse> templates) {
    public static PlanResponse from(SubscriptionPlan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getTagline(), p.getDescription(),
                p.getMonthlyPrice(), p.getAnnualPrice(), p.getCurrency(), p.getMaxCustomers(),
                p.getMaxMonthlyInvoices(), p.getMaxProducts(), p.getMaxCustomTemplates(), p.getFeatures(), p.isFeatured(),
                p.getTemplates().stream().filter(com.ritpl.billgen.entity.BillTemplate::isActive).map(TemplateResponse::from).toList());
    }
}

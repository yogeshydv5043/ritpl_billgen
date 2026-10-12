package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.util.Set;

public record PlanCommand(String code, String name, String tagline, String description,
                          BigDecimal monthlyPrice, BigDecimal annualPrice, String currency,
                          int maxCustomers, int maxMonthlyInvoices, String features,
                          boolean featured, boolean active, int sortOrder,
                          Set<Long> templateIds) {

    public PlanCommand {
        templateIds = templateIds == null ? Set.of() : Set.copyOf(templateIds);
    }
}
package com.ritpl.billgen.service;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.ProductType;

public record ProductCommand(String sku, String name, ProductType productType, Long categoryId,
                             String description, BigDecimal unitPrice, BigDecimal taxRate, boolean active,
                             String unit) {

    /** Callers that have no opinion about the unit still compile; the field stays empty rather than guessed. */
    public ProductCommand(String sku, String name, ProductType productType, Long categoryId, String description,
            BigDecimal unitPrice, BigDecimal taxRate, boolean active) {
        this(sku, name, productType, categoryId, description, unitPrice, taxRate, active, null);
    }
}
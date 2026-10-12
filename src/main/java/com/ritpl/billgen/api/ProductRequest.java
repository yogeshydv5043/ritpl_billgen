package com.ritpl.billgen.api;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.service.ProductCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(@NotBlank @Size(max=50) String sku,
        @NotBlank @Size(max=160) String name,
        @NotNull ProductType productType,
        /** Must be a category of the calling account. */
        Long categoryId,
        @Size(max=500) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        Boolean active) {
    public ProductCommand toCommand(){
        return new ProductCommand(sku, name, productType, categoryId, description, unitPrice, taxRate,
                active == null || active);
    }
}